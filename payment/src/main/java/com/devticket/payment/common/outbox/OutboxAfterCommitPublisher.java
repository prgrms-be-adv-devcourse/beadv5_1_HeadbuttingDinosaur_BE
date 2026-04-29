package com.devticket.payment.common.outbox;

import com.devticket.payment.common.config.OutboxAsyncConfig;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 트랜잭션 커밋 직후 Outbox row를 비동기로 Kafka에 발행한다.
 * 발행 실패는 throw 하지 않고 로그만 남기며, 미처리 row는 OutboxScheduler가 grace period 이후 보완한다.
 */
@Slf4j
@Component
public class OutboxAfterCommitPublisher {

    private final OutboxRepository outboxRepository;
    private final OutboxEventProducer outboxEventProducer;
    private final Executor executor;
    private final TransactionTemplate markSentTx;

    public OutboxAfterCommitPublisher(
        OutboxRepository outboxRepository,
        OutboxEventProducer outboxEventProducer,
        PlatformTransactionManager transactionManager,
        @Qualifier(OutboxAsyncConfig.OUTBOX_AFTER_COMMIT_EXECUTOR) Executor executor
    ) {
        this.outboxRepository = outboxRepository;
        this.outboxEventProducer = outboxEventProducer;
        this.executor = executor;
        this.markSentTx = new TransactionTemplate(transactionManager);
        this.markSentTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 별도 executor 스레드에서 발행을 실행한다.
     * executor 큐가 가득 차면 그냥 로그만 남기고 통과 — 스케줄러 fallback에 맡긴다.
     */
    public void publishAsync(Long outboxId) {
        try {
            executor.execute(() -> publish(outboxId));
        } catch (RejectedExecutionException e) {
            log.warn("[OutboxAfterCommit] executor reject — outboxId={}, fallback to scheduler",
                outboxId, e);
        }
    }

    private void publish(Long outboxId) {
        OutboxEventMessage message = loadMessage(outboxId);
        if (message == null) {
            return;
        }
        try {
            outboxEventProducer.publish(message);
        } catch (OutboxPublishException e) {
            log.warn("[OutboxAfterCommit] Kafka 발행 실패 — outboxId={}, eventType={}, error={}",
                outboxId, message.eventType(), e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.warn("[OutboxAfterCommit] 예기치 못한 발행 오류 — outboxId={}, error={}",
                outboxId, e.getMessage(), e);
            return;
        }
        markSentSafely(outboxId);
    }

    private OutboxEventMessage loadMessage(Long outboxId) {
        Outbox outbox = outboxRepository.findById(outboxId).orElse(null);
        if (outbox == null) {
            log.warn("[OutboxAfterCommit] Outbox row not found — outboxId={}", outboxId);
            return null;
        }
        if (!outbox.isPending()) {
            return null;
        }
        return OutboxEventMessage.from(outbox);
    }

    private void markSentSafely(Long outboxId) {
        try {
            markSentTx.executeWithoutResult(status ->
                outboxRepository.findById(outboxId).ifPresent(o -> {
                    if (o.isPending()) {
                        o.markSent();
                        outboxRepository.save(o);
                    }
                })
            );
        } catch (RuntimeException e) {
            log.warn("[OutboxAfterCommit] markSent 실패 — outboxId={}, fallback to scheduler",
                outboxId, e);
        }
    }
}
