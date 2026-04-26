package com.devticket.commerce.common.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * action.log 토픽 Kafka 발행 DTO.
 * BE_log(fastify-log/kafkajs)가 소비 — Java↔Node 이종 스택 계약.
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} 필수 (kafka-design.md §3).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionLogEvent(
        UUID userId,
        UUID eventId,
        ActionType actionType,
        String searchKeyword,
        String stackFilter,
        Integer dwellTimeSeconds,
        Integer quantity,
        Long totalAmount,
        Instant timestamp
) {
    public static ActionLogEvent from(ActionLogDomainEvent domain) {
        return new ActionLogEvent(
                domain.userId(),
                domain.eventId(),
                domain.actionType(),
                domain.searchKeyword(),
                domain.stackFilter(),
                domain.dwellTimeSeconds(),
                domain.quantity(),
                domain.totalAmount(),
                domain.timestamp()
        );
    }
}
