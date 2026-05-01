package com.devticket.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.event.application.RefundStockRestoreService.EventNotFoundForRefundException;
import com.devticket.event.common.config.JacksonConfig;
import com.devticket.event.common.messaging.KafkaTopics;
import com.devticket.event.common.outbox.Outbox;
import com.devticket.event.common.outbox.OutboxAfterCommitPublisher;
import com.devticket.event.common.outbox.OutboxEventProducer;
import com.devticket.event.common.outbox.OutboxRepository;
import com.devticket.event.common.outbox.OutboxService;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.repository.ProcessedMessageRepository;
import com.devticket.event.infrastructure.persistence.EventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
    RefundStockRestoreService.class,
    MessageDeduplicationService.class,
    OutboxService.class,
    JacksonConfig.class
})
class RefundStockRestoreServiceTest {

    @MockitoBean
    private OutboxEventProducer outboxEventProducer;

    @MockitoBean
    private OutboxAfterCommitPublisher outboxAfterCommitPublisher;

    @Autowired
    private RefundStockRestoreService refundStockRestoreService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private Event testEvent;
    private UUID eventId;
    private UUID refundId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        processedMessageRepository.deleteAll();

        testEvent = Event.create(
            UUID.randomUUID(), "테스트 이벤트", "설명", "서울",
            LocalDateTime.now().plusDays(15),
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(10),
            10000, 100, 5, EventCategory.CONFERENCE
        );
        ReflectionTestUtils.setField(testEvent, "remainingQuantity", 95);
        testEvent = eventRepository.saveAndFlush(testEvent);
        eventId = testEvent.getEventId();
        refundId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    private String buildPayload(UUID refundId, UUID orderId, UUID targetEventId, int quantity) {
        return """
            {"refundId":"%s","orderId":"%s","items":[{"eventId":"%s","quantity":%d}]}
            """.formatted(refundId, orderId, targetEventId, quantity).trim();
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("refund.stock.restore 수신 시 재고를 복구하고 refund.stock.done 을 발행한다")
        void restore_publishesDone() {
            UUID messageId = UUID.randomUUID();
            String payload = buildPayload(refundId, orderId, eventId, 5);

            refundStockRestoreService.handleRefundStockRestore(
                messageId, KafkaTopics.REFUND_STOCK_RESTORE, payload);

            Event updated = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(updated.getRemainingQuantity()).isEqualTo(100);
            assertThat(processedMessageRepository.existsByMessageId(messageId.toString())).isTrue();

            List<Outbox> outboxes = outboxRepository.findAll();
            assertThat(outboxes).hasSize(1);
            assertThat(outboxes.get(0).getTopic()).isEqualTo(KafkaTopics.REFUND_STOCK_DONE);
            assertThat(outboxes.get(0).getPartitionKey()).isEqualTo(refundId.toString());
            assertThat(outboxes.get(0).getEventType()).isEqualTo("REFUND_STOCK_DONE");
        }
    }

    @Nested
    @DisplayName("멱등성 — Dedup")
    class Idempotency {

        @Test
        @DisplayName("동일 messageId 재처리 시 재고가 이중 복구되지 않는다")
        void dedup_skipsDuplicate() {
            UUID messageId = UUID.randomUUID();
            String payload = buildPayload(refundId, orderId, eventId, 5);

            refundStockRestoreService.handleRefundStockRestore(
                messageId, KafkaTopics.REFUND_STOCK_RESTORE, payload);
            refundStockRestoreService.handleRefundStockRestore(
                messageId, KafkaTopics.REFUND_STOCK_RESTORE, payload);

            Event updated = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(updated.getRemainingQuantity()).isEqualTo(100);
            assertThat(outboxRepository.findAll()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("정책적 스킵 — cancelledQuantity 누적")
    class PolicySkip {

        @Test
        @DisplayName("FORCE_CANCELLED — 재고 복구 스킵 + cancelledQuantity 누적 + done 발행")
        void skip_forceCancelled() {
            ReflectionTestUtils.setField(testEvent, "status", EventStatus.FORCE_CANCELLED);
            eventRepository.saveAndFlush(testEvent);

            UUID messageId = UUID.randomUUID();
            String payload = buildPayload(refundId, orderId, eventId, 5);

            refundStockRestoreService.handleRefundStockRestore(
                messageId, KafkaTopics.REFUND_STOCK_RESTORE, payload);

            Event updated = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(updated.getRemainingQuantity()).isEqualTo(95);          // 재고 복구 안 됨
            assertThat(updated.getCancelledQuantity()).isEqualTo(5);           // cancelled 누적
            assertThat(updated.getStatus()).isEqualTo(EventStatus.FORCE_CANCELLED);
            assertThat(processedMessageRepository.existsByMessageId(messageId.toString())).isTrue();

            List<Outbox> outboxes = outboxRepository.findAll();
            assertThat(outboxes).hasSize(1);
            assertThat(outboxes.get(0).getTopic()).isEqualTo(KafkaTopics.REFUND_STOCK_DONE);
        }

        @Test
        @DisplayName("CANCELLED — 재고 복구 스킵 + cancelledQuantity 누적")
        void skip_cancelled() {
            ReflectionTestUtils.setField(testEvent, "status", EventStatus.CANCELLED);
            eventRepository.saveAndFlush(testEvent);

            UUID messageId = UUID.randomUUID();
            String payload = buildPayload(refundId, orderId, eventId, 5);

            refundStockRestoreService.handleRefundStockRestore(
                messageId, KafkaTopics.REFUND_STOCK_RESTORE, payload);

            Event updated = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(updated.getRemainingQuantity()).isEqualTo(95);
            assertThat(updated.getCancelledQuantity()).isEqualTo(5);
            assertThat(outboxRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("ENDED — 재고 복구 스킵 + cancelledQuantity 누적")
        void skip_ended() {
            ReflectionTestUtils.setField(testEvent, "status", EventStatus.ENDED);
            eventRepository.saveAndFlush(testEvent);

            UUID messageId = UUID.randomUUID();
            String payload = buildPayload(refundId, orderId, eventId, 3);

            refundStockRestoreService.handleRefundStockRestore(
                messageId, KafkaTopics.REFUND_STOCK_RESTORE, payload);

            Event updated = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(updated.getRemainingQuantity()).isEqualTo(95);
            assertThat(updated.getCancelledQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("정상 분기는 cancelledQuantity 변경 없음 — restoreStock 만 호출")
        void normalRestore_doesNotTouchCancelled() {
            UUID messageId = UUID.randomUUID();
            String payload = buildPayload(refundId, orderId, eventId, 5);

            refundStockRestoreService.handleRefundStockRestore(
                messageId, KafkaTopics.REFUND_STOCK_RESTORE, payload);

            Event updated = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(updated.getRemainingQuantity()).isEqualTo(100);
            assertThat(updated.getCancelledQuantity()).isZero();
        }

        @Test
        @DisplayName("동일 messageId 재처리 — cancelledQuantity 도 dedup 으로 1회만 누적")
        void cancelledQuantity_dedup() {
            ReflectionTestUtils.setField(testEvent, "status", EventStatus.FORCE_CANCELLED);
            eventRepository.saveAndFlush(testEvent);

            UUID messageId = UUID.randomUUID();
            String payload = buildPayload(refundId, orderId, eventId, 5);

            refundStockRestoreService.handleRefundStockRestore(
                messageId, KafkaTopics.REFUND_STOCK_RESTORE, payload);
            refundStockRestoreService.handleRefundStockRestore(
                messageId, KafkaTopics.REFUND_STOCK_RESTORE, payload);

            Event updated = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(updated.getCancelledQuantity()).isEqualTo(5);  // 5 + 5 = 10 이 아님 (dedup)
        }
    }

    @Nested
    @DisplayName("에러 케이스")
    class ErrorCases {

        @Test
        @DisplayName("존재하지 않는 eventId면 EventNotFoundForRefundException 을 던진다")
        void throwsOnMissingEvent() {
            UUID messageId = UUID.randomUUID();
            UUID fakeEventId = UUID.randomUUID();
            String payload = buildPayload(refundId, orderId, fakeEventId, 5);

            Assertions.assertThatThrownBy(() ->
                refundStockRestoreService.handleRefundStockRestore(
                    messageId, KafkaTopics.REFUND_STOCK_RESTORE, payload)
            ).isInstanceOf(EventNotFoundForRefundException.class)
                .hasMessageContaining("Event not found for refund stock restore");
        }
    }

    @Nested
    @DisplayName("실패 발행 — REQUIRES_NEW")
    class PublishFailed {

        @Test
        @DisplayName("publishFailedAndMarkProcessed 호출 시 refund.stock.failed 가 발행되고 dedup 기록된다")
        void publishesFailed() {
            UUID messageId = UUID.randomUUID();

            refundStockRestoreService.publishFailedAndMarkProcessed(
                messageId, KafkaTopics.REFUND_STOCK_RESTORE,
                refundId, orderId, "Event not found");

            assertThat(processedMessageRepository.existsByMessageId(messageId.toString())).isTrue();

            List<Outbox> outboxes = outboxRepository.findAll();
            assertThat(outboxes).hasSize(1);
            assertThat(outboxes.get(0).getTopic()).isEqualTo(KafkaTopics.REFUND_STOCK_FAILED);
            assertThat(outboxes.get(0).getEventType()).isEqualTo("REFUND_STOCK_FAILED");
            assertThat(outboxes.get(0).getPartitionKey()).isEqualTo(refundId.toString());
        }

        @Test
        @DisplayName("동일 messageId 의 publishFailedAndMarkProcessed 재호출은 dedup 으로 스킵된다")
        void publishFailed_dedup() {
            UUID messageId = UUID.randomUUID();

            refundStockRestoreService.publishFailedAndMarkProcessed(
                messageId, KafkaTopics.REFUND_STOCK_RESTORE,
                refundId, orderId, "Event not found");
            refundStockRestoreService.publishFailedAndMarkProcessed(
                messageId, KafkaTopics.REFUND_STOCK_RESTORE,
                refundId, orderId, "Event not found");

            assertThat(outboxRepository.findAll()).hasSize(1);
        }
    }
}
