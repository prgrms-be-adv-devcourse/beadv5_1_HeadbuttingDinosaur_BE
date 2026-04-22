package com.devticket.payment.common.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox 스케줄러 (OutboxScheduler)")
class OutboxSchedulerTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private OutboxScheduler scheduler;

    private Outbox createOutbox(String topic, String partitionKey) {
        return Outbox.create(
            "agg-id-001",
            "payment.completed",
            topic,
            partitionKey,
            "{\"orderId\":\"order-uuid-001\"}"
        );
    }

    @Test
    void PENDING_없으면_processOne_미호출() {
        given(outboxRepository.findPendingForRetry(any(), any())).willReturn(List.of());

        scheduler.publishPendingEvents();

        then(outboxService).should(never()).processOne(any());
    }

    @Test
    void PENDING_단건이면_processOne_1회_호출() {
        Outbox outbox = createOutbox("payment.completed", "order-uuid-001");
        given(outboxRepository.findPendingForRetry(any(), any())).willReturn(List.of(outbox));

        scheduler.publishPendingEvents();

        then(outboxService).should(times(1)).processOne(outbox);
    }

    @Test
    void PENDING_여러건이면_processOne_각각_호출() {
        Outbox o1 = createOutbox("payment.completed", "order-001");
        Outbox o2 = createOutbox("payment.completed", "order-002");
        Outbox o3 = createOutbox("payment.completed", "order-003");
        given(outboxRepository.findPendingForRetry(any(), any())).willReturn(List.of(o1, o2, o3));

        scheduler.publishPendingEvents();

        then(outboxService).should(times(3)).processOne(any());
        then(outboxService).should().processOne(o1);
        then(outboxService).should().processOne(o2);
        then(outboxService).should().processOne(o3);
    }
}
