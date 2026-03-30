package com.devticket.payment.wallet.application.service;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmCommand;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmResult;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // =====================================================================
    // 충전 시작
    // =====================================================================

    @Override
    @Transactional
    public WalletChargeResponse charge(UUID userId, WalletChargeRequest request,
        String idempotencyKey) {
        Optional<WalletCharge> existing =
            walletChargeRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return WalletChargeResponse.from(existing.get());
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
            .orElseGet(() -> walletRepository.save(Wallet.create(userId)));

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        int todayTotal = walletChargeRepository.sumTodayChargeAmount(userId, startOfDay);
        if (todayTotal + request.amount() > WalletPolicyConstants.DAILY_CHARGE_LIMIT) {
            throw new WalletException(WalletErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED);
        }

        try {
            WalletCharge walletCharge = WalletCharge.create(
                wallet.getId(), userId, request.amount(), idempotencyKey);
            walletChargeRepository.save(walletCharge);
            return WalletChargeResponse.from(walletCharge);
        } catch (DataIntegrityViolationException e) {
            return walletChargeRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(WalletChargeResponse::from)
                .orElseThrow(() -> e);
        }
    }

    // =====================================================================
    // 충전 승인
    // =====================================================================

    @Override
    @Transactional
    public WalletChargeConfirmResponse confirmCharge(UUID userId,
        WalletChargeConfirmRequest request) {
        UUID chargeId = parseUUID(request.chargeId());
        WalletCharge walletCharge = walletChargeRepository.findByChargeId(chargeId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.CHARGE_NOT_FOUND));

        if (!walletCharge.getUserId().equals(userId)) {
            throw new WalletException(WalletErrorCode.CHARGE_NOT_FOUND);
        }
        if (!walletCharge.isPending()) {
            throw new WalletException(WalletErrorCode.CHARGE_NOT_PENDING);
        }
        if (!walletCharge.getAmount().equals(request.amount())) {
            throw new WalletException(WalletErrorCode.CHARGE_AMOUNT_MISMATCH);
        }

        PgPaymentConfirmResult pgResult;
        try {
            pgResult = pgPaymentClient.confirm(new PgPaymentConfirmCommand(
                request.paymentKey(), walletCharge.getChargeId().toString(), request.amount()
            ));
        } catch (Exception e) {
            log.error("[WalletCharge] PG 승인 실패 — chargeId={}, error={}", chargeId, e.getMessage());
            walletCharge.fail();
            return WalletChargeConfirmResponse.from(
                walletCharge.getChargeId().toString(), walletCharge.getAmount(),
                null, "FAILED", null
            );
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        wallet.charge(walletCharge.getAmount());
        walletCharge.complete(pgResult.paymentKey());

        String transactionKey = "CHARGE:" + pgResult.paymentKey();
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
    // 출금
    // =====================================================================

    @Override
    @Transactional
    public WalletWithdrawResponse withdraw(UUID userId, WalletWithdrawRequest request) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        // 잔액 부족 체크는 Wallet.use() 내부에서도 하지만 WalletException으로 던지기 위해 선행 체크
        if (wallet.getBalance() < request.amount()) {
            throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        wallet.use(request.amount()); // Wallet에 withdraw() 없음 — use()로 처리

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
    public WalletBalanceResponse getBalance(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));
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
    public void processWalletPayment(UUID userId, Long orderId, int amount) {
        String transactionKey = "USE_" + orderId;

        if (walletTransactionRepository.existsByTransactionKey(transactionKey)) {
            log.info("[WalletPayment] 이미 처리된 주문 — orderId={}", orderId);
            return;
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        if (wallet.getBalance() < amount) {
            throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        wallet.use(amount);

        WalletTransaction tx = WalletTransaction.createUse(
            wallet.getId(), userId, transactionKey, amount, wallet.getBalance(), orderId
        );
        walletTransactionRepository.save(tx);

        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));
        payment.approve(null);

        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
            .orderId(orderId)
            .userId(userId.toString())
            .paymentId(payment.getPaymentId().toString())
            .paymentMethod("WALLET")
            .totalAmount(amount)
            .timestamp(LocalDateTime.now())
            .build();
        outboxService.save("PAYMENT", payment.getId(), KafkaTopics.PAYMENT_COMPLETED, event);

        log.info("[WalletPayment] 예치금 결제 완료 — orderId={}, amount={}, balanceAfter={}",
            orderId, amount, wallet.getBalance());
    }

    // =====================================================================
    // refund.completed Consumer — 예치금 복구
    // =====================================================================

    @Override
    @Transactional
    public void restoreBalance(UUID userId, int amount, UUID refundId, Long orderId) {
        String transactionKey = "REFUND_" + refundId;

        if (walletTransactionRepository.existsByTransactionKey(transactionKey)) {
            log.info("[WalletRefund] 이미 처리된 환불 — refundId={}", refundId);
            return;
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
            .orElseGet(() -> walletRepository.save(Wallet.create(userId)));

        wallet.refund(amount); // Wallet.refund() = balance += amount

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
    public void processBatchRefund(Long eventId) {
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
            Long orderId = orderInfo.getOrderId();
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
            // .refundId(refund.getRefundId().toString())
            // .orderId(orderId)
            // .userId(userId.toString())
            // .paymentId(payment.getPaymentId().toString())
            // .paymentMethod(orderInfo.getPaymentMethod())
            // .refundAmount(refundAmount)
            // .refundRate(100)
            // .timestamp(LocalDateTime.now())
            // .build();
            // outboxService.save("REFUND", refund.getId(), KafkaTopics.REFUND_COMPLETED, event);

            // log.info("[BatchRefund] 환불 완료 — orderId={}, refundId={}",
            // orderId, refund.getRefundId());
            log.info("[BatchRefund] Refund 모듈 미완성 — 스킵 orderId={}", orderId);
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