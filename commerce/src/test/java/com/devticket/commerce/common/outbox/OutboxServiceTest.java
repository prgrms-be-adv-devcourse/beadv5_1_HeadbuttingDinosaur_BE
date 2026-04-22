package com.devticket.commerce.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxEventProducer outboxEventProducer;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxService outboxService;

    @Test
    void save_호출시_직렬화된_이벤트를_Outbox로_저장한다() throws Exception {
        // given
        TestEvent event = new TestEvent(1L, "created");
        given(objectMapper.writeValueAsString(event)).willReturn("{\"id\":1,\"type\":\"created\"}");
        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);

        // when
        outboxService.save("aggregate-1", "partition-1", "OrderCreated", "order.created", event);

        // then
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox saved = outboxCaptor.getValue();
        assertThat(saved.getAggregateId()).isEqualTo("aggregate-1");
        assertThat(saved.getPartitionKey()).isEqualTo("partition-1");
        assertThat(saved.getEventType()).isEqualTo("OrderCreated");
        assertThat(saved.getTopic()).isEqualTo("order.created");
        assertThat(saved.getPayload()).isEqualTo("{\"id\":1,\"type\":\"created\"}");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void processOne_성공시_SENT로_변경하고_저장한다() {
        // given
        Outbox outbox = Outbox.create("aggregate-1", "partition-1", "OrderCreated", "order.created", "{\"id\":1}");

        // when
        outboxService.processOne(outbox);

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
        verify(outboxRepository).save(outbox);
    }

    @Test
    void processOne_발행_실패시_markFailed후_저장한다() {
        // given
        Outbox outbox = Outbox.create("aggregate-1", "partition-1", "OrderCreated", "order.created", "{\"id\":1}");
        doThrow(new OutboxPublishException("publish failed", new RuntimeException("kafka error")))
                .when(outboxEventProducer).publish(any());

        // when
        outboxService.processOne(outbox);

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextRetryAt()).isNotNull();
        verify(outboxRepository).save(outbox);
    }

    @Test
    void processOne_여섯번째_실패시_FAILED로_저장한다() {
        // given
        Outbox outbox = Outbox.create("aggregate-1", "partition-1", "OrderCreated", "order.created", "{\"id\":1}");
        for (int i = 0; i < 5; i++) {
            outbox.markFailed();
        }
        doThrow(new OutboxPublishException("publish failed", new RuntimeException("kafka error")))
                .when(outboxEventProducer).publish(any());

        // when
        outboxService.processOne(outbox);

        // then
        assertThat(outbox.getRetryCount()).isEqualTo(6);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(outboxRepository).save(outbox);
    }

    private record TestEvent(Long id, String type) {
    }
}
