package com.devticket.event.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxEventProducer outboxEventProducer;

    @Spy
    private ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @InjectMocks
    private OutboxService outboxService;

    @Test
    void save_호출시_직렬화된_이벤트를_Outbox로_저장한다() {
        // given
        String aggregateId = UUID.randomUUID().toString();
        String partitionKey = aggregateId;
        String eventType = "STOCK_DEDUCTED";
        String topic = "stock.deducted";
        SamplePayload event = new SamplePayload("v1", 42);

        // when
        outboxService.save(aggregateId, partitionKey, eventType, topic, event);

        // then
        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        then(outboxRepository).should().save(captor.capture());
        Outbox saved = captor.getValue();
        assertThat(saved.getAggregateId()).isEqualTo(aggregateId);
        assertThat(saved.getPartitionKey()).isEqualTo(partitionKey);
        assertThat(saved.getEventType()).isEqualTo(eventType);
        assertThat(saved.getTopic()).isEqualTo(topic);
        assertThat(saved.getPayload()).contains("\"name\":\"v1\"").contains("\"value\":42");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getMessageId()).isNotBlank();
    }

    @Test
    void processOne_성공시_SENT로_변경하고_저장한다() {
        // given
        Outbox outbox = pendingOutbox();
        given(outboxEventProducer.publish(any(OutboxEventMessage.class))).willReturn(true);

        // when
        outboxService.processOne(outbox);

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
        assertThat(outbox.getRetryCount()).isZero();
        then(outboxRepository).should().save(outbox);
    }

    @Test
    void processOne_발행_실패시_markFailed후_저장한다() {
        // given
        Outbox outbox = pendingOutbox();
        given(outboxEventProducer.publish(any(OutboxEventMessage.class))).willReturn(false);

        // when
        outboxService.processOne(outbox);

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextRetryAt()).isNotNull();
        assertThat(outbox.getSentAt()).isNull();
        then(outboxRepository).should().save(outbox);
    }

    @Test
    void processOne_여섯번째_실패시_FAILED로_저장한다() {
        // given
        Outbox outbox = pendingOutbox();
        ReflectionTestUtils.setField(outbox, "retryCount", 5);
        given(outboxEventProducer.publish(any(OutboxEventMessage.class))).willReturn(false);

        // when
        outboxService.processOne(outbox);

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(6);
        then(outboxRepository).should().save(outbox);
    }

    private Outbox pendingOutbox() {
        return Outbox.create(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "STOCK_DEDUCTED",
                "stock.deducted",
                "{\"dummy\":true}"
        );
    }

    private record SamplePayload(String name, int value) {
    }
}
