package com.devticket.commerce.common.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 처리용 TaskExecutor 설정.
 * {@code actionLogTaskExecutor}는 action.log Kafka Publisher 전용 — 큐 포화 시 DiscardPolicy로
 * 신규 task를 폐기하여 at-most-once 정책과 일관성 유지 (손실 허용).
 * {@code outboxAfterCommitExecutor}는 트랜잭션 커밋 직후 Outbox 직접 발행 전용 — 큐 포화 시
 * DiscardPolicy로 폐기하여 사용자 응답 지연을 막는다. 폐기된 row는 PENDING 상태로 남아
 * OutboxScheduler가 grace period 경과 후 보완 발행한다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("actionLogTaskExecutor")
    public ThreadPoolTaskExecutor actionLogTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("action-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }

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
