package com.devticket.event.application.event;

import static com.devticket.event.common.messaging.KafkaTopics.ACTION_LOG;

import com.devticket.event.common.messaging.event.ActionLogEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionLogKafkaPublisher {

    @Qualifier("actionLogKafkaTemplate")
    private final KafkaTemplate<String, String> actionLogKafkaTemplate;
    private final ObjectMapper objectMapper;

    // DB 미접근 리스너 — @Transactional 불요 (at-most-once 외부 발행 전용).
    // 기존 도메인 리스너(StockStatusChangedListener 의 @Transactional(REQUIRES_NEW, readOnly))
    // 패턴과 의도적 차이: 이 리스너의 주 책임은 내부 상태 동기화가 아닌 외부 Kafka 발행.
    @Async("actionLogTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ActionLogDomainEvent domain) {
        try {
            String payload = objectMapper.writeValueAsString(toKafkaEvent(domain));
            actionLogKafkaTemplate.send(ACTION_LOG, domain.userId().toString(), payload);
        } catch (JsonProcessingException e) {
            log.warn("action.log 직렬화 실패 — skip (actionType={}, userId={})",
                    domain.actionType(), domain.userId(), e);
        } catch (Exception e) {
            log.warn("action.log 발행 실패 — skip (actionType={}, userId={})",
                    domain.actionType(), domain.userId(), e);
        }
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
