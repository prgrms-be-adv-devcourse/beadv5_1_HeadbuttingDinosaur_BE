package com.devticket.commerce.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring 내부 도메인 이벤트 — Kafka 직렬화 대상 아님 (레이어 경계 보존).
 * ApplicationEventPublisher.publishEvent() → @TransactionalEventListener(AFTER_COMMIT)로 소비.
 * Kafka 발행 payload는 {@link ActionLogEvent}로 변환.
 */
public record ActionLogDomainEvent(
        UUID userId,
        UUID eventId,
        ActionType actionType,
        String searchKeyword,
        String stackFilter,
        Integer dwellTimeSeconds,
        Integer quantity,
        Long totalAmount,
        Instant timestamp
) {}
