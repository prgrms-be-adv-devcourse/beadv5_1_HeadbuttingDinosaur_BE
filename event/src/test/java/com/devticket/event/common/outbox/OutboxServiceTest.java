package com.devticket.event.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.apache.kafka.common.KafkaException;
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

    // 엔티티 상태 전이 세부(retryCount / sentAt / nextRetryAt 백오프)는 OutboxEntityTest 로 위임.
    // 여기서는 processOne 의 Service 계층 협력(publish 호출 · markSent/markFailed 경유 save) 만 단언.

    @Test
    void processOne_성공시_publish_호출_후_SENT로_전이하고_save를_호출한다() throws OutboxPublishException {
        // given — publish()는 void 이므로 기본 no-op 동작 사용
        Outbox outbox = pendingOutbox();

        // when
        outboxService.processOne(outbox);

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
        then(outboxEventProducer).should().publish(any(OutboxEventMessage.class));
        then(outboxRepository).should().save(outbox);
    }

    @Test
    void processOne_OutboxPublishException_발생시_PENDING_유지하고_save를_호출한다() throws OutboxPublishException {
        // given — Producer 가 OutboxPublishException 으로 감싼 발행 실패
        Outbox outbox = pendingOutbox();
        willThrow(new OutboxPublishException("broker ack 실패", null))
                .given(outboxEventProducer).publish(any(OutboxEventMessage.class));

        // when
        outboxService.processOne(outbox);

        // then — 재시도 가능 상태 유지 + 저장
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        then(outboxRepository).should().save(outbox);
    }

    @Test
    void processOne_publish가_RuntimeException을_전파해도_최후_방어선에서_흡수하고_save를_호출한다() throws OutboxPublishException {
        // given — Producer 가 감싸지 못한 예상 외 RuntimeException 전파 시나리오
        // (Producer 계약 위반이더라도 Scheduler 루프 중단 방지 — processOne 최후 방어선 가드)
        Outbox outbox = pendingOutbox();
        willThrow(new KafkaException("max.block.ms expired while fetching metadata"))
                .given(outboxEventProducer).publish(any(OutboxEventMessage.class));

        // when
        outboxService.processOne(outbox);

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        then(outboxRepository).should().save(outbox);
    }

    @Test
    void processOne_최대_재시도_도달시_FAILED로_전이하고_save를_호출한다() throws OutboxPublishException {
        // given — retryCount 5 에서 실패 1회 추가 → 6회 도달
        Outbox outbox = pendingOutbox();
        ReflectionTestUtils.setField(outbox, "retryCount", 5);
        willThrow(new OutboxPublishException("broker ack 실패", null))
                .given(outboxEventProducer).publish(any(OutboxEventMessage.class));

        // when
        outboxService.processOne(outbox);

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
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
