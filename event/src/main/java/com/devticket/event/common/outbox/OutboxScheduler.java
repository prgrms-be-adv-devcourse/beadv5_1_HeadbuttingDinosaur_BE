package com.devticket.event.common.outbox;

import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 스케줄러 — 누락/실패 메시지 fallback 발행.
 *
 * <p>정상 흐름은 {@link OutboxAfterCommitPublisher} 가 트랜잭션 커밋 직후
 * 비동기로 즉시 Kafka 에 발행한다. 본 스케줄러는 직접 발행 경로가 실패했거나
 * 프로세스 다운 등으로 누락된 row 를 grace period 경과 후 보완 발행한다.
 *
 * <p>재시도 정책 (지수 백오프): 총 6회, 누적 최대 31초 대기
 * <ul>
 *   <li>1회: 즉시</li>
 *   <li>2회: 1초 후</li>
 *   <li>3회: 2초 후</li>
 *   <li>4회: 4초 후</li>
 *   <li>5회: 8초 후</li>
 *   <li>6회: 16초 후 → 실패 시 FAILED 처리</li>
 * </ul>
 *
 * <p>건별 발행 처리는 {@link OutboxService#processOne(Outbox)} 으로 위임한다.
 * 자기 자신 호출(self-invocation)은 Spring AOP 프록시를 우회하여
 * {@code @Transactional} 이 무효화되므로 별도 빈(OutboxService) 분리가 필수.
 *
 * <p>ShedLock 으로 분산 환경 중복 실행 방지.
 */
@Slf4j
@Component
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final OutboxService outboxService;
    private final long graceSeconds;

    public OutboxScheduler(OutboxRepository outboxRepository,
                           OutboxService outboxService,
                           @Value("${outbox.publish-grace-seconds:5}") long graceSeconds) {
        this.outboxRepository = outboxRepository;
        this.outboxService = outboxService;
        this.graceSeconds = graceSeconds;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:60000}")
    @SchedulerLock(name = "outbox-scheduler", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void publishPendingEvents() {
        Instant now = Instant.now();
        Instant graceCutoff = now.minusSeconds(graceSeconds);

        List<Outbox> pendingOutboxes =
                outboxRepository.findPendingToPublish(OutboxStatus.PENDING, now, graceCutoff);

        if (pendingOutboxes.isEmpty()) {
            return;
        }

        log.debug("Outbox fallback 발행 대상: {}건 (graceSeconds={})",
                pendingOutboxes.size(), graceSeconds);

        for (Outbox outbox : pendingOutboxes) {
            outboxService.processOne(outbox);
        }
    }
}
