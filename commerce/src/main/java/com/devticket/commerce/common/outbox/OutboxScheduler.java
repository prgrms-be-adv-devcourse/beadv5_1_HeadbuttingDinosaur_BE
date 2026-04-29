package com.devticket.commerce.common.outbox;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 누락/실패 메시지 보완(fallback) 전용 스케줄러.
 * <p>
 * 정상 흐름은 {@link OutboxAfterCommitPublisher} 가 트랜잭션 커밋 직후 직접 발행한다.
 * 본 스케줄러는 직접 발행 경로가 누락(executor 폐기, 프로세스 다운, Kafka 일시 장애 등)된
 * row 를 grace period 경과 후 보완 발행한다.
 * <p>
 * 시스템 부하 감소를 위해 폴링 주기는 60초로 설정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final OutboxService outboxService;

    // 직접 발행 경로가 우선 처리할 수 있도록 최근 N초 이내 row 는 fallback 대상에서 제외한다.
    @Value("${devticket.outbox.publish-grace-seconds:5}")
    private long publishGraceSeconds;

    // @Transactional 없음 — Kafka 발행은 트랜잭션 밖에서 처리
    // 건당 독립 처리: OutboxService.processOne()에 위임
    // 폴링 주기는 운영 부하 감소를 위해 60초 — 테스트 프로파일에서는 application-test.yml 로 단축한다.
    @Scheduled(fixedDelayString = "${devticket.outbox.scheduler-delay-ms:60000}")
    @SchedulerLock(name = "outbox-scheduler", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void publishPendingEvents() {
        Instant now = Instant.now();
        Instant graceCutoff = now.minusSeconds(publishGraceSeconds);
        List<Outbox> pendingList = outboxRepository
                .findPendingToPublish(OutboxStatus.PENDING, now, graceCutoff);

        if (pendingList.isEmpty()) {
            return;
        }

        log.debug("[Outbox] fallback 발행 대상: {}건", pendingList.size());

        for (Outbox outbox : pendingList) {
            outboxService.processOne(outbox);
        }
    }
}
