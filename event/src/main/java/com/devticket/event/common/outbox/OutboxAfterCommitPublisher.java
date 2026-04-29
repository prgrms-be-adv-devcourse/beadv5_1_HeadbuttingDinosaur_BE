package com.devticket.event.common.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox 직접 발행 — 비즈니스 트랜잭션 커밋 직후 비동기로 Kafka 발행.
 *
 * <p>흐름:
 * <pre>
 * [TX] save(PENDING) + afterCommit 훅 등록 → COMMIT
 *      └─ executor 스레드에서 publish(outboxId)
 *          ├─ outboxRepository.findById
 *          ├─ outboxEventProducer.publish
 *          └─ markSent (별도 짧은 TX, REQUIRES_NEW)
 * </pre>
 *
 * <p>실패해도 PENDING row 는 남아 있으므로 OutboxScheduler 가
 * grace period(기본 5초) 경과 후 fallback 으로 발행한다.
 * 따라서 본 클래스의 모든 실패는 throw 하지 않고 warn 로그만 남긴다.
 */
@Slf4j
@Component
public class OutboxAfterCommitPublisher {

    private final OutboxRepository outboxRepository;
    private final OutboxEventProducer outboxEventProducer;
    private final ThreadPoolTaskExecutor outboxAfterCommitExecutor;
    private final TransactionTemplate transactionTemplate;

    public OutboxAfterCommitPublisher(OutboxRepository outboxRepository,
                                      OutboxEventProducer outboxEventProducer,
                                      ThreadPoolTaskExecutor outboxAfterCommitExecutor,
                                      PlatformTransactionManager transactionManager) {
        this.outboxRepository = outboxRepository;
        this.outboxEventProducer = outboxEventProducer;
        this.outboxAfterCommitExecutor = outboxAfterCommitExecutor;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = template;
    }

    /**
     * afterCommit 콜백에서 호출. 실제 발행은 별도 executor 스레드에서 수행한다.
     */
    public void scheduleAfterCommit(Long outboxId) {
        outboxAfterCommitExecutor.execute(() -> publish(outboxId));
    }

    private void publish(Long outboxId) {
        try {
            Outbox outbox = outboxRepository.findById(outboxId).orElse(null);
            if (outbox == null) {
                log.warn("Outbox afterCommit 발행 대상 없음 — outboxId={}", outboxId);
                return;
            }
            if (outbox.getStatus() != OutboxStatus.PENDING) {
                return;
            }

            outboxEventProducer.publish(OutboxEventMessage.from(outbox));
            markSent(outboxId);
        } catch (OutboxPublishException e) {
            log.warn("Outbox 직접 발행 실패 (fallback 위임) — outboxId={}, error={}",
                    outboxId, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Outbox 직접 발행 중 예외 (fallback 위임) — outboxId={}, error={}",
                    outboxId, e.getMessage());
        }
    }

    /**
     * 발행 성공 후 SENT 전이 — 짧은 REQUIRES_NEW 트랜잭션.
     * 실패해도 PENDING 으로 남아 fallback 이 재발행 → consumer dedup 으로 흡수.
     */
    private void markSent(Long outboxId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Outbox outbox = outboxRepository.findById(outboxId).orElse(null);
                if (outbox == null || outbox.getStatus() != OutboxStatus.PENDING) {
                    return;
                }
                outbox.markSent();
                outboxRepository.save(outbox);
            });
        } catch (RuntimeException e) {
            log.warn("Outbox markSent 실패 (fallback 위임) — outboxId={}, error={}",
                    outboxId, e.getMessage());
        }
    }
}
