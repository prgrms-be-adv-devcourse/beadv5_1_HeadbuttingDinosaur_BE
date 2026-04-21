package com.devticket.payment.common.outbox;

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

    @Scheduled(fixedDelay = 3000)
    @SchedulerLock(name = "outbox-scheduler", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void publishPendingEvents() {
        List<Outbox> pendingList =
            outboxRepository.findPendingForRetry(OutboxStatus.PENDING, Instant.now());

        if (pendingList.isEmpty()) {
            return;
        }

        log.info("[OutboxScheduler] PENDING 이벤트 {}건 처리 시작", pendingList.size());

        for (Outbox outbox : pendingList) {
            outboxService.processOne(outbox);
        }

        log.info("[OutboxScheduler] PENDING 이벤트 처리 완료");
    }
}
