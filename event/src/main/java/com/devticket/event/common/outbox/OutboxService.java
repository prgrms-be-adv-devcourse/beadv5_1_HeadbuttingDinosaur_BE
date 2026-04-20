package com.devticket.event.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Outbox 저장·건별 발행 서비스
 *
 * <p>저장 경로({@link #save}) — 비즈니스 트랜잭션 내부에서 호출.
 * 트랜잭션 커밋이 되어야 비로소 OutboxScheduler가 발행 대상으로 인식한다.
 *
 * <p>발행 경로({@link #processOne}) — OutboxScheduler가 건별로 호출.
 * Scheduler와 동일 클래스에서 호출하면 self-invocation으로 @Transactional이 무효화되므로
 * 별도 빈으로 분리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final OutboxEventProducer outboxEventProducer;
    private final OutboxAfterCommitPublisher outboxAfterCommitPublisher;
    private final ObjectMapper objectMapper;

    /**
     * 이벤트를 Outbox에 저장한다.
     * 반드시 비즈니스 로직과 동일한 트랜잭션 안에서 호출해야 한다.
     *
     * @param aggregateId  비즈니스 키 UUID (운영 추적용 — orderId, eventId 등)
     * @param partitionKey Kafka Partition Key (순서 보장 기준 — orderId 또는 eventId)
     * @param eventType    이벤트 유형 식별자 (예: EVENT_FORCE_CANCELLED)
     * @param topic        Kafka 토픽명
     * @param event        직렬화할 이벤트 DTO 객체
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String aggregateId, String partitionKey,
                     String eventType, String topic, Object event) {
        String payload = serialize(event);
        Outbox outbox = Outbox.create(aggregateId, partitionKey, eventType, topic, payload);
        outboxRepository.save(outbox);
        // IDENTITY 전략 — save() 직후 outbox.getId() 가 채워진다.
        registerAfterCommitPublish(outbox.getId());
    }

    /**
     * 비즈니스 트랜잭션 커밋 직후 비동기 직접 발행을 예약한다.
     * 실패/누락 시에는 OutboxScheduler 가 grace period 경과 후 fallback 으로 발행한다.
     */
    private void registerAfterCommitPublish(Long outboxId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // MANDATORY 라 도달 불가지만 방어적으로 처리.
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                outboxAfterCommitPublisher.scheduleAfterCommit(outboxId);
            }
        });
    }

    /**
     * Outbox 한 건을 Kafka에 발행하고 상태를 반영한다.
     * Kafka 발행은 트랜잭션 밖, 상태 저장은 {@code save()} 자체 트랜잭션에서 수행.
     *
     * <p>예외 처리 정책:
     * <ul>
     *   <li>{@link OutboxPublishException} — Producer가 감싼 발행 실패 → markFailed</li>
     *   <li>{@link RuntimeException} — 예상 외 전파 (최후 방어선) → markFailed</li>
     * </ul>
     * 어느 쪽이든 Scheduler 루프 중단·레코드 고착을 막기 위해 markFailed/save를 보장한다.
     */
    public void processOne(Outbox outbox) {
        try {
            outboxEventProducer.publish(OutboxEventMessage.from(outbox));
            outbox.markSent();
        } catch (OutboxPublishException e) {
            log.warn("Outbox 발행 실패 — outboxId={}, topic={}, messageId={}, error={}",
                    outbox.getId(), outbox.getTopic(), outbox.getMessageId(), e.getMessage());
            markFailedAndLog(outbox);
        } catch (RuntimeException e) {
            log.error("Outbox 발행 중 예외 전파 — outboxId={}, topic={}, messageId={}, error={}",
                    outbox.getId(), outbox.getTopic(), outbox.getMessageId(), e.getMessage(), e);
            markFailedAndLog(outbox);
        }
        outboxRepository.save(outbox);
    }

    private void markFailedAndLog(Outbox outbox) {
        outbox.markFailed();
        if (outbox.getStatus() == OutboxStatus.FAILED) {
            log.error("Outbox 최대 재시도 초과 → FAILED — outboxId={}, topic={}, messageId={}",
                    outbox.getId(), outbox.getTopic(), outbox.getMessageId());
        }
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Outbox 이벤트 직렬화 실패: " + event.getClass().getSimpleName(), e);
        }
    }
}
