package com.devticket.payment.common.outbox;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
        LocalDateTime graceCutoff =
            LocalDateTime.ofInstant(now.minusSeconds(graceSeconds), ZoneId.systemDefault());
        List<Outbox> pendingList =
            outboxRepository.findPendingToPublish(OutboxStatus.PENDING, now, graceCutoff);

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
