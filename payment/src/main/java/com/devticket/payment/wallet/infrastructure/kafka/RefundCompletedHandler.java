package com.devticket.payment.wallet.infrastructure.kafka;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devticket.payment.common.messaging.MessageDeduplicationService;

@Service
@RequiredArgsConstructor
public class RefundCompletedHandler {

    private final MessageDeduplicationService deduplicationService;

    @Transactional
    public void markProcessedOnly(UUID messageId, String topic) {
        deduplicationService.markProcessed(messageId, topic);
    }
}
