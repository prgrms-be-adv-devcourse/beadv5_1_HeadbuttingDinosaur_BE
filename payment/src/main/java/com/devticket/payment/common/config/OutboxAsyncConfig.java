package com.devticket.payment.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class OutboxAsyncConfig {

    public static final String OUTBOX_AFTER_COMMIT_EXECUTOR = "outboxAfterCommitExecutor";

    @Bean(name = OUTBOX_AFTER_COMMIT_EXECUTOR, destroyMethod = "shutdown")
    public Executor outboxAfterCommitExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("outbox-after-commit-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
