package com.devticket.event.common.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Outbox afterCommit 비동기 발행 전용 Executor.
 *
 * <p>큐가 가득 차거나 reject 되어도 PENDING row 가 남아 있으므로
 * OutboxScheduler 가 grace period 경과 후 fallback 발행한다.
 * 따라서 손실 허용(DiscardPolicy) 정책을 사용한다.
 */
@Configuration
public class OutboxAsyncConfig {

    @Bean("outboxAfterCommitExecutor")
    public ThreadPoolTaskExecutor outboxAfterCommitExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("outbox-after-commit-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
