package com.devticket.event.common.outbox;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 스케줄러 — 미발행 이벤트를 Kafka로 폴링 발행
 *
 * <p>재시도 정책 (지수 백오프): 총 6회, 총 최대 대기 31초
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
 * <p>ShedLock으로 분산 환경 중복 실행 방지.
 * shedlock 테이블 DDL:
 * <pre>
 * CREATE TABLE shedlock (
 *     name        VARCHAR(64)  NOT NULL,
 *     lock_until  TIMESTAMP    NOT NULL,
 *     locked_at   TIMESTAMP    NOT NULL,
 *     locked_by   VARCHAR(255) NOT NULL,
 *     PRIMARY KEY (name)
 * );
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final OutboxService outboxService;

    @Scheduled(fixedDelay = 3_000)
    @SchedulerLock(name = "outbox-scheduler", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void publishPendingEvents() {
        List<Outbox> pendingOutboxes =
                outboxRepository.findPendingOutboxes(OutboxStatus.PENDING, Instant.now());

        if (pendingOutboxes.isEmpty()) {
            return;
        }

        log.debug("Outbox 발행 대상: {}건", pendingOutboxes.size());

        for (Outbox outbox : pendingOutboxes) {
            outboxService.processOne(outbox);
        }
    }
}
