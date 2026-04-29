package com.devticket.event.common.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Outbox afterCommit 비동기 발행 전용 Executor.
 *
 * <p>순서 보장 — 단일 스레드.
 * Outbox 의 {@code partitionKey} 는 Kafka 파티션 라우팅 + 순서 보장 기준이다
 * (예: 같은 eventId 의 CREATED → CANCELLED). 두 워커가 같은 partitionKey 의
 * 두 outbox 를 동시에 {@code kafkaTemplate.send()} 하면 RecordAccumulator 진입
 * 순서가 비결정적이 되어 broker 에 도달하는 순서가 뒤집힐 수 있다.
 * afterCommit 발행 경로는 단일 스레드로 직렬화해 커밋 순서 = 발행 순서를 보장한다.
 *
 * <p>처리량 한계 — 단일 스레드가 병목이 되면 큐(1000) 에 적재되고,
 * 큐 포화 시 DiscardPolicy 로 거부된 row 는 PENDING 으로 남아
 * OutboxScheduler 가 grace period 경과 후 fallback 발행한다.
 * 즉, 본 executor 의 throughput 부족은 latency 저하로만 흡수되고
 * 메시지 손실/순서 역전으로는 이어지지 않는다.
 */
@Configuration
public class OutboxAsyncConfig {

    @Bean("outboxAfterCommitExecutor")
    public ThreadPoolTaskExecutor outboxAfterCommitExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 단일 스레드 — partitionKey 순서 보장 (클래스 Javadoc 참조).
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("outbox-after-commit-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
