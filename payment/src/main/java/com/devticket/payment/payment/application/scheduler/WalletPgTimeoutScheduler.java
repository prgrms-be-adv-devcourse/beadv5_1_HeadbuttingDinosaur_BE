package com.devticket.payment.payment.application.scheduler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import com.devticket.payment.wallet.application.event.PaymentFailedEvent;
import com.devticket.payment.wallet.application.service.WalletService;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletPgTimeoutScheduler {

    private static final int TIMEOUT_MINUTES = 30;

    private final PaymentRepository paymentRepository;
    private final WalletService walletService;
    private final OutboxService outboxService;
    private final CommerceInternalClient commerceInternalClient;

    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name = "wallet-pg-timeout", lockAtMostFor = "50s", lockAtLeastFor = "10s")
    public void processExpiredWalletPgPayments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);

        List<Payment> expiredPayments = paymentRepository.findExpiredReadyPayments(
            PaymentStatus.READY, PaymentMethod.WALLET_PG, cutoff
        );

        if (expiredPayments.isEmpty()) {
            return;
        }

        log.info("[WalletPgTimeout] 만료 WALLET_PG 결제 {}건 처리 시작", expiredPayments.size());

        for (Payment payment : expiredPayments) {
            processTimeout(payment);
        }

        log.info("[WalletPgTimeout] 만료 WALLET_PG 결제 처리 완료");
    }

    @Transactional
    protected void processTimeout(Payment payment) {
        try {
            // 상태 전이 (canTransitionTo 가드가 경쟁 조건 방어)
            payment.fail("WALLET_PG 결제 타임아웃 (" + TIMEOUT_MINUTES + "분 초과)");
            paymentRepository.save(payment);

            // 예치금 복구 (transactionKey 멱등성으로 중복 복구 방지)
            walletService.restoreForWalletPgFail(
                payment.getUserId(), payment.getWalletAmount(), payment.getOrderId()
            );

            // Commerce에서 orderItems 조회 후 payment.failed Outbox 발행
            InternalOrderInfoResponse order = commerceInternalClient.getOrderInfo(payment.getOrderId());

            List<PaymentFailedEvent.OrderItem> orderItems = order.orderItems() == null
                ? List.of()
                : order.orderItems().stream()
                    .map(item -> new PaymentFailedEvent.OrderItem(item.eventId(), item.quantity()))
                    .toList();

            PaymentFailedEvent event = PaymentFailedEvent.builder()
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .orderItems(orderItems)
                .reason(payment.getFailureReason())
                .timestamp(Instant.now())
                .build();

            outboxService.save(
                payment.getPaymentId().toString(),
                KafkaTopics.PAYMENT_FAILED,
                KafkaTopics.PAYMENT_FAILED,
                payment.getOrderId().toString(),
                event
            );

            log.info("[WalletPgTimeout] 타임아웃 처리 완료 — orderId={}, walletAmount={}",
                payment.getOrderId(), payment.getWalletAmount());

        } catch (Exception e) {
            log.error("[WalletPgTimeout] 타임아웃 처리 실패 — orderId={}, error={}",
                payment.getOrderId(), e.getMessage(), e);
            // 다음 스케줄러 주기에 재시도
        }
    }
}
