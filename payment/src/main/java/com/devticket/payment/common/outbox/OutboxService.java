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
     * @param aggregateType 발행 주체 도메인 (예: "WALLET", "PAYMENT", "REFUND")
     * @param aggregateId   관련 엔티티 PK
     * @param eventType     Kafka 토픽명 (예: "payment.completed")
     * @param payload       이벤트 페이로드 객체 (JSON 직렬화됨)
     * @return 생성된 Outbox 엔티티
     */
    public Outbox save(String aggregateType, Long aggregateId,
                       String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            Outbox outbox = Outbox.create(aggregateType, aggregateId, eventType, json);
            return outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("[Outbox] 페이로드 직렬화 실패 — aggregateType={}, aggregateId={}, eventType={}",
                aggregateType, aggregateId, eventType, e);
            throw new IllegalStateException("Outbox 페이로드 직렬화 실패", e);
        }
    }
}
