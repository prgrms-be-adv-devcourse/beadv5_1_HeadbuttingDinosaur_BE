package com.devticket.commerce.common.outbox;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final OutboxService outboxService;

    // @Transactional 없음 — Kafka 발행은 트랜잭션 밖에서 처리
    // 건당 독립 처리: OutboxService.processOne()에 위임
    @Scheduled(fixedDelay = 3_000)
    @SchedulerLock(name = "outbox-scheduler", lockAtMostFor = "30s", lockAtLeastFor = "5s")
    public void publishPendingEvents() {
        List<Outbox> pendingList = outboxRepository
                .findPendingToPublish(OutboxStatus.PENDING, Instant.now());

        if (pendingList.isEmpty()) {
            return;
        }

        log.debug("[Outbox] 발행 대상: {}건", pendingList.size());

        for (Outbox outbox : pendingList) {
            outboxService.processOne(outbox);
        }
    }
}
