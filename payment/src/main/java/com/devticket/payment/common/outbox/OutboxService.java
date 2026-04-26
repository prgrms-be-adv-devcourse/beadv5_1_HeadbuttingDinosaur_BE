package com.devticket.payment.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final OutboxEventProducer outboxEventProducer;
    private final ObjectMapper objectMapper;

    /**
     * Outbox 이벤트를 저장한다.
     * 반드시 비즈니스 로직과 같은 트랜잭션 안에서 호출해야 한다.
     *
     * @param aggregateId  관련 엔티티 식별자 (UUID 문자열)
     * @param partitionKey Kafka 파티션 키 (예: orderId)
     * @param eventType    도메인 이벤트 타입 (예: "payment.completed")
     * @param topic        Kafka 토픽명 (예: "payment.completed")
     * @param event        이벤트 페이로드 객체 (JSON 직렬화됨)
     * @return 생성된 Outbox 엔티티
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Outbox save(String aggregateId, String partitionKey,
                       String eventType, String topic, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            Outbox outbox = Outbox.create(aggregateId, partitionKey, eventType, topic, json);
            return outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("[Outbox] 페이로드 직렬화 실패 — aggregateId={}, eventType={}, topic={}",
                aggregateId, eventType, topic, e);
            throw new IllegalStateException("Outbox 페이로드 직렬화 실패", e);
        }
    }

    // @Transactional 없음 — 스케줄러 루프 전체를 트랜잭션으로 묶지 않고 건별 save()로 개별 커밋
    public void processOne(Outbox outbox) {
        try {
            OutboxEventMessage message = OutboxEventMessage.from(outbox);
            outboxEventProducer.publish(message);
            outbox.markSent();
        } catch (OutboxPublishException e) {
            log.warn("[Outbox] 이벤트 발행 실패 — outboxId={}, eventType={}, retry={}, error={}",
                outbox.getId(), outbox.getEventType(), outbox.getRetryCount() + 1, e.getMessage());
            outbox.markFailed();
        }
        outboxRepository.save(outbox);
    }
}
