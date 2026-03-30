package com.devticket.payment.common.outbox;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final OutboxEventProducer outboxEventProducer;

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void publishPendingEvents() {
        List<Outbox> pendingList =
            outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingList.isEmpty()) {
            return;
        }

        log.info("[OutboxScheduler] PENDING 이벤트 {}건 처리 시작", pendingList.size());

        for (Outbox outbox : pendingList) {
            try {
                OutboxEventMessage message = OutboxEventMessage.from(outbox);
                String key = outbox.getAggregateId().toString();

                boolean sent = outboxEventProducer.send(
                    outbox.getEventType(), key, message);

                if (sent) {
                    outbox.markSent();
                } else {
                    outbox.markFailed();
                }
            } catch (Exception e) {
                log.error("[OutboxScheduler] 이벤트 발행 실패 — outboxId={}, eventType={}, error={}",
                    outbox.getId(), outbox.getEventType(), e.getMessage());
                outbox.markFailed();
            }
        }

        log.info("[OutboxScheduler] PENDING 이벤트 처리 완료");
    }
}
