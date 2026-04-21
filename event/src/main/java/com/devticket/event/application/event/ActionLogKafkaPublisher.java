package com.devticket.event.application.event;

import com.devticket.event.common.messaging.event.ActionLogEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionLogKafkaPublisher {

    @Qualifier("actionLogKafkaTemplate")
    private final KafkaTemplate<String, String> actionLogKafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(ActionLogDomainEvent domain) {
        // 본체 구현은 커밋 3 (Listener + @EnableAsync) 에서 완성
    }

    // Kafka DTO 매핑은 application → infrastructure 방향 준수 위해 Publisher 내부에 배치
    // (ActionLogEvent 는 순수 record, 도메인 타입 참조 금지 — AGENTS.md §2.2)
    private static ActionLogEvent toKafkaEvent(ActionLogDomainEvent domain) {
        return new ActionLogEvent(
                domain.userId().toString(),
                domain.eventId() == null ? null : domain.eventId().toString(),
                domain.actionType().name(),
                domain.searchKeyword(),
                domain.stackFilter(),
                domain.dwellTimeSeconds(),
                domain.quantity(),
                domain.totalAmount(),
                domain.timestamp()
        );
    }
}
