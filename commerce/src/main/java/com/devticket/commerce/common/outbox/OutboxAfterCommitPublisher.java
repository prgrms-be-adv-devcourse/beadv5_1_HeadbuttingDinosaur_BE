package com.devticket.commerce.common.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 트랜잭션 커밋 직후 Outbox 메시지를 곧바로 Kafka로 발행한다.
 * <p>
 * Outbox row 자체는 비즈니스 트랜잭션 안에서 PENDING 상태로 저장된 뒤,
 * {@code afterCommit} 훅에서 별도 executor 스레드로 본 메서드를 호출한다.
 * 발행 성공 시 별도 짧은 트랜잭션으로 SENT 상태 전이까지 완료한다.
 * <p>
 * 직접 발행 경로가 실패하거나(Kafka 장애, executor 큐 폐기, 프로세스 다운 등)
 * markSent 단계가 실패해도 row는 PENDING 으로 남으므로 grace period 경과 후
 * {@link OutboxScheduler} 가 보완 발행한다. consumer 측 {@code X-Message-Id} dedup 으로
 * 중복 발행은 무해화된다.
 */
@Slf4j
@Component
public class OutboxAfterCommitPublisher {

    private final OutboxRepository outboxRepository;
    private final OutboxEventProducer outboxEventProducer;
    private final ThreadPoolTaskExecutor outboxAfterCommitExecutor;
    private final TransactionTemplate markSentTxTemplate;

    public OutboxAfterCommitPublisher(
            OutboxRepository outboxRepository,
            OutboxEventProducer outboxEventProducer,
            @Qualifier("outboxAfterCommitExecutor") ThreadPoolTaskExecutor outboxAfterCommitExecutor,
            PlatformTransactionManager transactionManager) {
        this.outboxRepository = outboxRepository;
        this.outboxEventProducer = outboxEventProducer;
        this.outboxAfterCommitExecutor = outboxAfterCommitExecutor;
        // markSent 는 짧은 신규 TX. 비즈니스 TX와 완전 분리.
        this.markSentTxTemplate = new TransactionTemplate(transactionManager);
        this.markSentTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * afterCommit 훅에서 호출. executor 큐가 가득 차 reject 되면 DiscardPolicy 로 폐기되고
     * 스케줄러 fallback 으로 흡수된다.
     */
    public void schedulePublish(Long outboxId) {
        outboxAfterCommitExecutor.execute(() -> publish(outboxId));
    }

    void publish(Long outboxId) {
        Outbox outbox = outboxRepository.findById(outboxId).orElse(null);
        if (outbox == null) {
            log.warn("[Outbox] afterCommit 발행 대상 없음 — outboxId={}", outboxId);
            return;
        }
        if (outbox.getStatus() != OutboxStatus.PENDING) {
            return;
        }
        try {
            outboxEventProducer.publish(OutboxEventMessage.from(outbox));
        } catch (OutboxPublishException e) {
            log.warn("[Outbox] afterCommit 발행 실패 — messageId={}, topic={}, 스케줄러 fallback 위임",
                    outbox.getMessageId(), outbox.getTopic(), e);
            return;
        } catch (RuntimeException e) {
            log.warn("[Outbox] afterCommit 발행 비정상 종료 — messageId={}, topic={}, 스케줄러 fallback 위임",
                    outbox.getMessageId(), outbox.getTopic(), e);
            return;
        }
        markSentSafely(outboxId);
    }

    private void markSentSafely(Long outboxId) {
        try {
            markSentTxTemplate.executeWithoutResult(status ->
                    outboxRepository.findById(outboxId).ifPresent(o -> {
                        if (o.getStatus() == OutboxStatus.PENDING) {
                            o.markSent();
                        }
                    })
            );
        } catch (RuntimeException e) {
            log.warn("[Outbox] markSent 실패 — outboxId={}, 스케줄러 fallback 위임", outboxId, e);
        }
    }
}
