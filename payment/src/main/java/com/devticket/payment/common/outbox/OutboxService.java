package com.devticket.payment.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Outbox 이벤트를 저장한다.
     * 반드시 비즈니스 로직과 같은 트랜잭션 안에서 호출해야 한다.
     *
     * @param aggregateId  관련 엔티티 식별자 (UUID 문자열)
     * @param eventType    도메인 이벤트 타입 (예: "payment.completed")
     * @param topic        Kafka 토픽명 (예: "payment.completed")
     * @param partitionKey Kafka 파티션 키 (예: orderId)
     * @param payload      이벤트 페이로드 객체 (JSON 직렬화됨)
     * @return 생성된 Outbox 엔티티
     */
    public Outbox save(String aggregateId, String eventType,
                       String topic, String partitionKey, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            Outbox outbox = Outbox.create(aggregateId, eventType, topic, partitionKey, json);
            return outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("[Outbox] 페이로드 직렬화 실패 — aggregateId={}, eventType={}, topic={}",
                aggregateId, eventType, topic, e);
            throw new IllegalStateException("Outbox 페이로드 직렬화 실패", e);
        }
    }
}
