package com.devticket.event.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 저장 서비스 — 비즈니스 트랜잭션 내부에서 호출
 *
 * <p>호출 원칙: 비즈니스 로직과 반드시 단일 @Transactional 경계 안에서 호출해야 한다.
 * 트랜잭션 커밋이 되어야 비로소 OutboxScheduler가 발행 대상으로 인식한다.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * 이벤트를 Outbox에 저장한다.
     * 반드시 비즈니스 로직과 동일한 트랜잭션 안에서 호출해야 한다.
     *
     * @param aggregateId  비즈니스 키 UUID (운영 추적용 — orderId, eventId 등)
     * @param partitionKey Kafka Partition Key (순서 보장 기준 — orderId 또는 eventId)
     * @param eventType    이벤트 유형 식별자 (예: STOCK_DEDUCTED)
     * @param topic        Kafka 토픽명
     * @param event        직렬화할 이벤트 DTO 객체
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String aggregateId, String partitionKey,
                     String eventType, String topic, Object event) {
        String payload = serialize(event);
        Outbox outbox = Outbox.create(aggregateId, partitionKey, eventType, topic, payload);
        outboxRepository.save(outbox);
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Outbox 이벤트 직렬화 실패: " + event.getClass().getSimpleName(), e);
        }
    }
}
