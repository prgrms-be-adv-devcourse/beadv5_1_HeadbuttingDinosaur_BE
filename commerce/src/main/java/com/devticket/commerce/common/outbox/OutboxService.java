package com.devticket.commerce.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final OutboxEventProducer outboxEventProducer;
    private final OutboxAfterCommitPublisher outboxAfterCommitPublisher;
    private final ObjectMapper objectMapper;

    // 비즈니스 서비스에서 호출 — 반드시 호출자의 @Transactional 안에서 실행
    // MANDATORY: 활성 트랜잭션 없으면 IllegalTransactionStateException 발생
    //
    // 저장 후 afterCommit 훅을 등록해 커밋 직후 별도 executor 스레드에서 직접 발행한다.
    // 직접 발행 실패/누락 시에도 row 는 PENDING 으로 남아 OutboxScheduler 가 보완 발행한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String aggregateId, String partitionKey,
            String eventType, String topic, Object event) {
        String payload = serialize(event);
        Outbox saved = outboxRepository.save(
                Outbox.create(aggregateId, partitionKey, eventType, topic, payload));
        registerAfterCommitPublish(saved.getId());
    }

    private void registerAfterCommitPublish(Long outboxId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                outboxAfterCommitPublisher.schedulePublish(outboxId);
            }
        });
    }

    // OutboxScheduler에서 호출 — Kafka 발행 후 상태 갱신
    // @Transactional 없음 — Kafka 발행은 트랜잭션 밖에서 처리
    // outboxRepository.save()가 자체 트랜잭션을 열고 닫음
    public void processOne(Outbox outbox) {
        try {
            outboxEventProducer.publish(OutboxEventMessage.from(outbox));
            outbox.markSent();
        } catch (OutboxPublishException e) {
            log.warn("[Outbox] 발행 실패 — messageId: {}, topic: {}, retryCount: {}, error: {}",
                    outbox.getMessageId(), outbox.getTopic(), outbox.getRetryCount(), e.getMessage());
            outbox.markFailed();
            if (outbox.getStatus() == OutboxStatus.FAILED) {
                log.error("[Outbox] 최대 재시도 소진 — messageId: {}, topic: {}. 수동 재발행 필요.",
                        outbox.getMessageId(), outbox.getTopic());
            }
        }
        outboxRepository.save(outbox);
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Outbox 이벤트 직렬화 실패: " + event.getClass().getSimpleName(), e);
        }
    }
}
