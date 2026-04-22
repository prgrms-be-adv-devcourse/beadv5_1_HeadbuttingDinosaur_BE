package com.devticket.payment.wallet.infrastructure.kafka;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.wallet.application.service.WalletService;

@Service
@RequiredArgsConstructor
public class RefundCompletedHandler {

    private final WalletService walletService;
    private final MessageDeduplicationService deduplicationService;

    @Transactional
    public void restoreBalanceAndMarkProcessed(
        UUID userId, int amount, UUID refundId, UUID orderId,
        UUID messageId, String topic
    ) {
        walletService.restoreBalance(userId, amount, refundId, orderId);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void markProcessedOnly(UUID messageId, String topic) {
        deduplicationService.markProcessed(messageId, topic);
    }
}
