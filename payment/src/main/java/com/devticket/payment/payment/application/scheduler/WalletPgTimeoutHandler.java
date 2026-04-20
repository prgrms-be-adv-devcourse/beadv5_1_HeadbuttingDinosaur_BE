package com.devticket.payment.payment.application.scheduler;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import com.devticket.payment.wallet.application.event.PaymentFailedEvent;
import com.devticket.payment.wallet.application.service.WalletService;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletPgTimeoutHandler {

    private static final int TIMEOUT_MINUTES = 30;

    private final PaymentRepository paymentRepository;
    private final WalletService walletService;
    private final OutboxService outboxService;
    private final CommerceInternalClient commerceInternalClient;

    @Transactional
    public void processTimeout(Payment payment) {
        payment.fail("WALLET_PG 결제 타임아웃 (" + TIMEOUT_MINUTES + "분 초과)");
        paymentRepository.save(payment);

        walletService.restoreForWalletPgFail(
            payment.getUserId(), payment.getWalletAmount(), payment.getOrderId()
        );

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
    }
}
