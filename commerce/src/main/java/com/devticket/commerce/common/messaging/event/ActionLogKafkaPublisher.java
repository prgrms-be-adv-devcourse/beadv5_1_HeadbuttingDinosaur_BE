package com.devticket.commerce.common.messaging.event;

import com.devticket.commerce.common.messaging.KafkaTopics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * action.log 토픽 전용 Kafka Publisher — fire-and-forget (at-most-once).
 * 비즈니스 {@code @Transactional} 커밋 후 비동기 발행 → API 응답 지연 제로.
 * 예외는 로깅 후 스킵 (재시도·DLT·throw 금지).
 */
@Slf4j
@Component
public class ActionLogKafkaPublisher {

    private final KafkaTemplate<String, String> actionLogKafkaTemplate;
    private final ObjectMapper objectMapper;

    public ActionLogKafkaPublisher(
            @Qualifier("actionLogKafkaTemplate") KafkaTemplate<String, String> actionLogKafkaTemplate,
            ObjectMapper objectMapper) {
        this.actionLogKafkaTemplate = actionLogKafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // fallbackExecution=true: 트랜잭션 없는 호출자도 지원 (이중 안전장치 — 원자성은 호출 측 @Transactional로 해결)
    @Async("actionLogTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(ActionLogDomainEvent domain) {
        try {
            String payload = objectMapper.writeValueAsString(ActionLogEvent.from(domain));
            actionLogKafkaTemplate.send(
                    KafkaTopics.ACTION_LOG,
                    domain.userId().toString(),
                    payload
            );
        } catch (JsonProcessingException e) {
            log.warn("action.log 직렬화 실패 — skip: actionType={}, userId={}",
                    domain.actionType(), domain.userId(), e);
        } catch (Exception e) {
            log.warn("action.log 발행 실패 — skip: actionType={}, userId={}",
                    domain.actionType(), domain.userId(), e);
        }
    }
}
