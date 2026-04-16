package com.devticket.payment.wallet.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmCommand;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmResult;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentStatusResponse;
import com.devticket.payment.wallet.application.event.PaymentCompletedEvent;
import com.devticket.payment.wallet.domain.WalletPolicyConstants;
import com.devticket.payment.wallet.domain.exception.WalletErrorCode;
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletCharge;
import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import com.devticket.payment.wallet.infrastructure.client.dto.InternalEventOrdersResponse;
import com.devticket.payment.wallet.presentation.dto.WalletBalanceResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeResponse;
import com.devticket.payment.wallet.presentation.dto.WalletTransactionListResponse;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawRequest;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawResponse;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletChargeRepository walletChargeRepository;
    private final PaymentRepository paymentRepository;
    // private final RefundRepository refundRepository; // TODO: Refund 모듈 완성 후 활성화
    private final PgPaymentClient pgPaymentClient;
    private final OutboxService outboxService;
    private final CommerceInternalClient commerceInternalClient;
    private final PlatformTransactionManager txManager;

    //this.claimChargeForProcessing() 같은 Self-call이 프록시를 우회하는 문제를 해결하려면 자기 참조 주입(self-injection) 이 필요
    @Lazy
    @Autowired
    private WalletServiceImpl self;

    // =====================================================================
    // 충전 시작(결제인증에 필요한 WalletCharge생성-chargeId)
    // =====================================================================

    //예치금 충전시 PG사 결제창을 띄우기위한 chargeId와 결제정보 생성.
    @Override
    @Transactional
    public WalletChargeResponse charge(UUID userId, WalletChargeRequest request,
        String idempotencyKey) {

        //1차 멱등성 체크 : 이미 Wallet이 존재하는 경우(기존 사용자)
        Optional<WalletCharge> existing =
            walletChargeRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return WalletChargeResponse.from(existing.get());
        }

        //예치금 지갑 조회 — 비관적 락으로 한도 체크 구간 직렬화
        Optional<Wallet> existingWallet = walletRepository.findByUserIdForUpdate(userId);
        Wallet wallet;
        //Wallet이 존재
        if (existingWallet.isPresent()) {
            wallet = existingWallet.get();
        } else {
            //wallet이 존재하지 않는 경우 내부에서 새로운 세션으로 wallet생성 진행
            //지갑중복생성 예외가 발행해도 메인 트랜잭션은 롤백되지 않고 로직을 이어갈수 있음.
            TransactionTemplate requiresNew = new TransactionTemplate(txManager);
            requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            try {
                requiresNew.executeWithoutResult(s -> walletRepository.save(Wallet.create(userId)));
            } catch (DataIntegrityViolationException ignored) {
            }
            //wallet생성시 중복오류가 발생한경우_외부 세션은 살아있으므로 wallet 조회 진행
            wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));
        }

        //2차 멱등성 체크 : 기존에 Wallet이 존재하지 않았고 이번에 새롭게 생성한 경우(신규사용자)
        Optional<WalletCharge> reCheckExisting =
            walletChargeRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (reCheckExisting.isPresent()) {
            return WalletChargeResponse.from(reCheckExisting.get());
        }

        //일일 충전 한도 체크
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        int todayTotal = walletChargeRepository.sumTodayChargeAmount(userId, startOfDay);
        if (todayTotal + request.amount() > WalletPolicyConstants.DAILY_CHARGE_LIMIT) {
            throw new WalletException(WalletErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED);
        }

        // WalletCharge 생성
        WalletCharge walletCharge = WalletCharge.create(
            wallet.getId(), userId, request.amount(), idempotencyKey);
        walletChargeRepository.save(walletCharge);
        return WalletChargeResponse.from(walletCharge);
    }


    // =====================================================================
    // 충전 승인
    // =====================================================================

    //PG사 결제창에서 결제인증 완료 후 인증완료된 건에 대한 최종 결제승인 처리진행.
    // 비관적 락 구간을 최소화하기 위해 3단계로 트랜잭션 분리:
    // 1) 락 + 선점(PENDING→PROCESSING)  2) PG 호출(락 없음)  3) 결과 반영
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)  //클래스 레벨에 @Transactional(readOnly = true)가 붙어 있는 상황에서, confirmCharge 메서드만 트랜잭션 없이 실행하도록 설정.
    public WalletChargeConfirmResponse confirmCharge(UUID userId,
        WalletChargeConfirmRequest request) {

        // ── 1단계: 비관적 락으로 상태 선점 (PENDING → PROCESSING) ──
        // self를 통해 호출 → 프록시 경유 → @Transactional 실제 적용
        UUID chargeId = parseUUID(request.chargeId());
        self.claimChargeForProcessing(userId, chargeId, request.amount());

        // ── 2단계: 락 해제 후 PG 호출 (DB 커넥션 점유 없음) ──
        PgPaymentConfirmResult pgResult;
        try {
            pgResult = pgPaymentClient.confirm(new PgPaymentConfirmCommand(
                request.paymentKey(), chargeId.toString(), request.amount()
            ));
        } catch (Exception e) {
            log.error("[WalletCharge] PG 승인 실패 — chargeId={}, error={}", chargeId, e.getMessage());
            self.failProcessingCharge(chargeId);
            return WalletChargeConfirmResponse.from(
                chargeId.toString(), request.amount(),
                null, "FAILED", null
            );
        }

        // ── 3단계: 새 트랜잭션에서 결과 반영 ──
        return self.completeChargeAfterPg(userId, chargeId, pgResult);
    }

    /**
     * 비관적 락으로 PENDING → PROCESSING 선점. 락은 트랜잭션 종료 시 즉시 해제.
     */
    @Transactional
    public void claimChargeForProcessing(UUID userId, UUID chargeId, Integer expectedAmount) {
        WalletCharge walletCharge = walletChargeRepository.findByChargeIdForUpdate(chargeId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.CHARGE_NOT_FOUND));

        if (!walletCharge.getUserId().equals(userId)) {
            throw new WalletException(WalletErrorCode.CHARGE_NOT_FOUND);
        }
        if (!walletCharge.isPending()) {
            throw new WalletException(WalletErrorCode.CHARGE_NOT_PENDING);
        }
        if (!walletCharge.getAmount().equals(expectedAmount)) {
            throw new WalletException(WalletErrorCode.CHARGE_AMOUNT_MISMATCH);
        }

        walletCharge.markProcessing();
    }

    /**
     * PG 실패 시 PROCESSING → FAILED 원복.
     */
    @Transactional
    public void failProcessingCharge(UUID chargeId) {
        WalletCharge walletCharge = walletChargeRepository.findByChargeId(chargeId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.CHARGE_NOT_FOUND));
        walletCharge.fail();
    }

    /**
     * PG 승인 성공 후 잔액 반영 + WalletTransaction 생성.
     */
    @Transactional
    public WalletChargeConfirmResponse completeChargeAfterPg(UUID userId, UUID chargeId,
        PgPaymentConfirmResult pgResult) {
        WalletCharge walletCharge = walletChargeRepository.findByChargeId(chargeId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.CHARGE_NOT_FOUND));

        walletCharge.complete(pgResult.paymentKey());
        walletRepository.chargeBalanceAtomic(userId, walletCharge.getAmount());

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        String transactionKey = "CHARGE:" + pgResult.paymentKey();
        if (walletTransactionRepository.existsByTransactionKey(transactionKey)) {
            log.warn("[WalletCharge] WalletTransaction 이미 존재 — transactionKey={}", transactionKey);
            return WalletChargeConfirmResponse.from(
                walletCharge.getChargeId().toString(), walletCharge.getAmount(),
                wallet.getBalance(), walletCharge.getStatus().name(), null
            );
        }
        WalletTransaction walletTransaction = WalletTransaction.createCharge(
            wallet.getId(), userId, transactionKey, walletCharge.getAmount(), wallet.getBalance()
        );
        walletTransactionRepository.save(walletTransaction);

        log.info("[WalletCharge] 충전 승인 완료 — chargeId={}, amount={}, balance={}",
            chargeId, walletCharge.getAmount(), wallet.getBalance());

        return WalletChargeConfirmResponse.from(
            walletCharge.getChargeId().toString(), walletCharge.getAmount(),
            wallet.getBalance(), walletCharge.getStatus().name(), walletTransaction.getCreatedAt()
        );
    }

    // =====================================================================
    // 충전 실패 처리
    // =====================================================================

    @Override
    @Transactional
    public void failCharge(UUID userId, String chargeId) {
        // 비관적 락: confirmCharge()와 동시 실행 시 상태 역전 방지
        WalletCharge walletCharge = walletChargeRepository.findByChargeIdForUpdate(parseUUID(chargeId))
            .orElseThrow(() -> new WalletException(WalletErrorCode.CHARGE_NOT_FOUND));

        if (!walletCharge.getUserId().equals(userId)) {
            throw new WalletException(WalletErrorCode.CHARGE_NOT_FOUND);
        }
        if (!walletCharge.isPending()) {
            throw new WalletException(WalletErrorCode.CHARGE_NOT_PENDING);
        }

        walletCharge.fail();
        log.info("[WalletCharge] 충전 실패 처리 완료 — chargeId={}", chargeId);
    }

    // =====================================================================
    // 출금
    // =====================================================================

    @Override
    @Transactional
    public WalletWithdrawResponse withdraw(UUID userId, WalletWithdrawRequest request) {
        //atomic update방식으로 balance값 업데이트진행
        int updated = walletRepository.useBalanceAtomic(userId, request.amount());
        if (updated == 0) {
            // 0 rows = 지갑 없음의 경우와  잔액 부족의 경우가 동일한 응답이 반환됨
            // 지갑 존재 여부로 재구분
            walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));
            throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        // clearAutomatically = true 로 캐시 초기화됐으므로 최신 잔액을 재조회
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        //WalletTransaction 생성
        String transactionKey = "WITHDRAW:" + userId + ":" + UUID.randomUUID();
        WalletTransaction tx = WalletTransaction.createWithdraw(
            wallet.getId(), userId, transactionKey, request.amount(), wallet.getBalance()
        );
        walletTransactionRepository.save(tx);

        log.info("[Wallet] 출금 완료 — userId={}, amount={}, balanceAfter={}",
            userId, request.amount(), wallet.getBalance());

        return WalletWithdrawResponse.of(
            wallet.getWalletId().toString(),
            tx.getWalletTransactionId().toString(),
            tx.getAmount(),
            wallet.getBalance(),
            "COMPLETED",
            tx.getCreatedAt()
        );
    }

    // =====================================================================
    // 잔액 조회 / 내역 조회
    // =====================================================================

    @Override
    @Transactional
    public WalletBalanceResponse getBalance(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseGet(() -> walletRepository.save(Wallet.create(userId)));
        return WalletBalanceResponse.of(wallet);
    }

    @Override
    public WalletTransactionListResponse getTransactions(UUID userId, int page, int size) {
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WalletTransaction> txPage =
            walletTransactionRepository.findAllByWalletId(wallet.getId(), pageable);

        return WalletTransactionListResponse.of(txPage, page);
    }

    // =====================================================================
    // 예치금 결제 (USE) — Commerce Internal 동기 호출로 진입
    // =====================================================================

    @Override
    @Transactional
    public void processWalletPayment(UUID userId, UUID orderId, int amount) {
        String transactionKey = "USE_" + orderId;

        //토스 paymentKey = WalletTransaction의 transactionKey
        //해당 transactionKey의 유무로 멱등성 체크
        if (walletTransactionRepository.existsByTransactionKey(transactionKey)) {
            log.info("[WalletPayment] 이미 처리된 주문 — orderId={}", orderId);
            return;
        }

        //원자적 업데이트 방식으로 Wallet의 잔액 업데이트
        int updated = walletRepository.useBalanceAtomic(userId, amount);
        if (updated == 0) {
            walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));
            throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        // clearAutomatically = true 로 캐시 초기화됐으므로 최신 잔액을 재조회
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        //WalletTransaction 생성
        WalletTransaction tx = WalletTransaction.createUse(
            wallet.getId(), userId, transactionKey, amount, wallet.getBalance(), orderId
        );
        walletTransactionRepository.save(tx);

        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));
        payment.approve(null);

        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
            .orderId(orderId)
            .userId(userId)
            .paymentId(payment.getPaymentId())
            .paymentMethod(PaymentMethod.WALLET)
            .totalAmount(amount)
            .timestamp(Instant.now())
            .build();
        outboxService.save(
            payment.getPaymentId().toString(),
            KafkaTopics.PAYMENT_COMPLETED,
            KafkaTopics.PAYMENT_COMPLETED,
            orderId.toString(),
            event
        );

        log.info("[WalletPayment] 예치금 결제 완료 — orderId={}, amount={}, balanceAfter={}",
            orderId, amount, wallet.getBalance());
    }

    // =====================================================================
    // refund.completed Consumer — 예치금 복구
    // =====================================================================

    @Override
    @Transactional
    public void restoreBalance(UUID userId, int amount, UUID refundId, UUID orderId) {
        String transactionKey = "REFUND_" + refundId;

        if (walletTransactionRepository.existsByTransactionKey(transactionKey)) {
            log.info("[WalletRefund] 이미 처리된 환불 — refundId={}", refundId);
            return;
        }

        // 지갑이 없으면 생성 후 환불 진행
        walletRepository.findByUserId(userId)
            .orElseGet(() -> walletRepository.save(Wallet.create(userId)));

        walletRepository.refundBalanceAtomic(userId, amount);

        // clearAutomatically = true 로 캐시 초기화됐으므로 최신 잔액을 재조회
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        WalletTransaction tx = WalletTransaction.createRefund(
            wallet.getId(), userId, transactionKey, amount, wallet.getBalance(),
            orderId, null // relatedRefundId는 Long — UUID를 직접 넣을 수 없음 (아래 주석 참고)
        );
        walletTransactionRepository.save(tx);

        log.info("[WalletRefund] 예치금 복구 완료 — refundId={}, amount={}, balanceAfter={}",
            refundId, amount, wallet.getBalance());
    }

    // =====================================================================
    // event.force-cancelled / event.sale-stopped — 일괄 환불
    // =====================================================================

    @Override
    @Transactional
    public void processBatchRefund(UUID eventId) {
        InternalEventOrdersResponse response = commerceInternalClient.getOrdersByEvent(eventId);

        if (response == null || response.getOrders() == null || response.getOrders().isEmpty()) {
            log.info("[BatchRefund] 환불 대상 주문 없음 — eventId={}", eventId);
            return;
        }

        List<InternalEventOrdersResponse.OrderInfo> orders = response.getOrders().stream()
            .filter(o -> "PAID".equals(o.getStatus()))
            .toList();

        log.info("[BatchRefund] 일괄 환불 시작 — eventId={}, 대상 건수={}", eventId, orders.size());

        for (InternalEventOrdersResponse.OrderInfo orderInfo : orders) {
            UUID orderId = orderInfo.getOrderId();
            UUID userId = UUID.fromString(orderInfo.getUserId());
            int refundAmount = orderInfo.getTotalAmount();

            Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
            if (payment == null) {
                log.warn("[BatchRefund] Payment 없음 — orderId={}", orderId);
                continue;
            }
            if (payment.getStatus() == PaymentStatus.REFUNDED) {
                log.info("[BatchRefund] 이미 환불됨 — orderId={}", orderId);
                continue;
            }

            // TODO: Refund 모듈 완성 후 주석 해제
            // Refund refund = Refund.createForBatch(payment, refundAmount, 100);
            // refundRepository.save(refund);
            // payment.refund();

            // if ("WALLET".equals(orderInfo.getPaymentMethod())) {
            // restoreBalance(userId, refundAmount, refund.getRefundId(), orderId);
            // }

            // RefundCompletedEvent event = RefundCompletedEvent.builder()
            // .refundId(refund.getRefundId())
            // .orderId(orderId)
            // .userId(userId)
            // .paymentId(payment.getPaymentId())
            // .paymentMethod(payment.getPaymentMethod())
            // .refundAmount(refundAmount)
            // .refundRate(100)
            // .timestamp(Instant.now())
            // .build();
            // outboxService.save("REFUND", refund.getId(), KafkaTopics.REFUND_COMPLETED, event);

            // log.info("[BatchRefund] 환불 완료 — orderId={}, refundId={}",
            // orderId, refund.getRefundId());
            log.info("[BatchRefund] Refund 모듈 미완성 — 스킵 orderId={}", orderId);
        }
    }

    // =====================================================================
    // 사후 보정 (Self-healing) — 스케줄러에서 개별 건 단위로 호출
    // =====================================================================

    /**
     * 스케줄러가 넘겨준 chargeId 한 건을 복구한다.
     * 비관적 락 구간을 최소화하기 위해 선점 → PG 조회 → 결과 반영으로 분리.
     */
    @Override
    public void recoverStalePendingCharge(UUID chargeId) {
        // ── 1단계: 비관적 락으로 선점 (PENDING → PROCESSING) ──
        if (!claimChargeForRecovery(chargeId)) {
            return; // 이미 처리됐거나 찾을 수 없음
        }

        // ── 2단계: 락 해제 후 Toss에서 실제 결제 상태 조회 ──
        Optional<TossPaymentStatusResponse> pgStatusOpt;
        try {
            pgStatusOpt = pgPaymentClient.findPaymentByOrderId(chargeId.toString());
        } catch (Exception e) {
            log.warn("[Recovery] PG 상태 조회 실패 — chargeId={}, PROCESSING 상태 유지 후 다음 주기 재시도. error={}",
                chargeId, e.getMessage());
            revertTopending(chargeId);
            return;
        }

        // ── 3단계: 새 트랜잭션에서 결과 반영 ──
        applyRecoveryResult(chargeId, pgStatusOpt);
    }

    /**
     * 복구용 선점: PENDING → PROCESSING. 이미 처리됐으면 false 반환.
     */
    @Transactional
    public boolean claimChargeForRecovery(UUID chargeId) {
        WalletCharge walletCharge = walletChargeRepository.findByChargeIdForUpdate(chargeId)
            .orElse(null);

        if (walletCharge == null || !walletCharge.isPending()) {
            return false;
        }

        walletCharge.markProcessing();
        return true;
    }

    /**
     * PG 조회 실패 시 PROCESSING → PENDING 원복 (다음 스케줄에서 재시도 가능하도록).
     */
    @Transactional
    public void revertTopending(UUID chargeId) {
        WalletCharge walletCharge = walletChargeRepository.findByChargeId(chargeId).orElse(null);
        if (walletCharge != null && walletCharge.isProcessing()) {
            walletCharge.revertToPending();
        }
    }

    /**
     * PG 조회 결과에 따라 COMPLETED 또는 FAILED 처리.
     */
    @Transactional
    public void applyRecoveryResult(UUID chargeId, Optional<TossPaymentStatusResponse> pgStatusOpt) {
        WalletCharge walletCharge = walletChargeRepository.findByChargeId(chargeId)
            .orElse(null);
        if (walletCharge == null) {
            return;
        }

        if (pgStatusOpt.isEmpty()) {
            walletCharge.fail();
            log.info("[Recovery] Toss 미도달(404) — chargeId={} → FAILED", chargeId);
            return;
        }

        TossPaymentStatusResponse pgStatus = pgStatusOpt.get();

        if ("DONE".equals(pgStatus.status())) {
            String transactionKey = "CHARGE:" + pgStatus.paymentKey();

            if (walletTransactionRepository.existsByTransactionKey(transactionKey)) {
                walletCharge.complete(pgStatus.paymentKey());
                log.info("[Recovery] 거래 기록 중복 확인 — chargeId={} → COMPLETED (잔액 반영 생략)", chargeId);
                return;
            }

            walletCharge.complete(pgStatus.paymentKey());
            walletRepository.chargeBalanceAtomic(walletCharge.getUserId(), walletCharge.getAmount());

            Wallet wallet = walletRepository.findByUserId(walletCharge.getUserId())
                .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

            WalletTransaction tx = WalletTransaction.createCharge(
                wallet.getId(), walletCharge.getUserId(),
                transactionKey, walletCharge.getAmount(), wallet.getBalance()
            );
            walletTransactionRepository.save(tx);

            log.info("[Recovery] PG DONE 감지 — chargeId={}, amount={}, balance={} → COMPLETED",
                chargeId, walletCharge.getAmount(), wallet.getBalance());

        } else {
            walletCharge.fail();
            log.info("[Recovery] PG 상태 '{}' — chargeId={} → FAILED", pgStatus.status(), chargeId);
        }
    }

    // =====================================================================
    // 공통 유틸
    // =====================================================================

    private UUID parseUUID(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new WalletException(WalletErrorCode.INVALID_CHARGE_REQUEST);
        }
    }
}