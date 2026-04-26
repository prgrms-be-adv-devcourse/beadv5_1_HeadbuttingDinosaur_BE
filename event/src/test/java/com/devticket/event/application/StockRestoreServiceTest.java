package com.devticket.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.repository.ProcessedMessageRepository;
import com.devticket.event.infrastructure.persistence.EventRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.devticket.event.common.config.JacksonConfig;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({StockRestoreService.class, MessageDeduplicationService.class, JacksonConfig.class})
class StockRestoreServiceTest {

    @Autowired
    private StockRestoreService stockRestoreService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    private Event testEvent;
    private UUID eventId;

    @BeforeEach
    void setUp() {
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
    }

    private String buildPayload(UUID orderId, UUID targetEventId, int quantity) {
        return """
            {"orderId":"%s","userId":"%s","orderItems":[{"eventId":"%s","quantity":%d}],"reason":"PG 실패","timestamp":"2026-04-16T10:00:00Z"}
            """.formatted(orderId, UUID.randomUUID(), targetEventId, quantity).trim();
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("payment.failed 수신 시 재고를 복구한다")
        void restoreStock_success() {
            UUID messageId = UUID.randomUUID();
            String payload = buildPayload(UUID.randomUUID(), eventId, 5);

            stockRestoreService.restoreStockForPaymentFailed(messageId, "payment.failed", payload);

            Event updated = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(updated.getRemainingQuantity()).isEqualTo(100);
            assertThat(processedMessageRepository.existsByMessageId(messageId.toString())).isTrue();
        }

        @Test
        @DisplayName("다중 이벤트 재고를 한 트랜잭션에서 벌크 복구한다")
        void restoreStock_bulk() {
            Event event2 = Event.create(
                UUID.randomUUID(), "이벤트2", "설명", "부산",
                LocalDateTime.now().plusDays(15),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10),
                10000, 50, 3, EventCategory.HACKATHON
            );
            ReflectionTestUtils.setField(event2, "remainingQuantity", 47);
            event2 = eventRepository.saveAndFlush(event2);
            UUID eventId2 = event2.getEventId();

            UUID messageId = UUID.randomUUID();
            String payload = """
                {"orderId":"%s","userId":"%s","orderItems":[{"eventId":"%s","quantity":5},{"eventId":"%s","quantity":3}],"reason":"PG 실패","timestamp":"2026-04-16T10:00:00Z"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), eventId, eventId2).trim();

            stockRestoreService.restoreStockForPaymentFailed(messageId, "payment.failed", payload);

            assertThat(eventRepository.findByEventId(eventId).orElseThrow().getRemainingQuantity()).isEqualTo(100);
            assertThat(eventRepository.findByEventId(eventId2).orElseThrow().getRemainingQuantity()).isEqualTo(50);
        }

        @Test
        @DisplayName("SOLD_OUT 상태에서 재고 복구 시 ON_SALE로 전환된다")
        void restoreStock_soldOutToOnSale() {
            ReflectionTestUtils.setField(testEvent, "remainingQuantity", 0);
            ReflectionTestUtils.setField(testEvent, "status", EventStatus.SOLD_OUT);
            eventRepository.saveAndFlush(testEvent);

            UUID messageId = UUID.randomUUID();
            String payload = buildPayload(UUID.randomUUID(), eventId, 5);

            stockRestoreService.restoreStockForPaymentFailed(messageId, "payment.failed", payload);

            Event updated = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(updated.getRemainingQuantity()).isEqualTo(5);
            assertThat(updated.getStatus()).isEqualTo(EventStatus.ON_SALE);
        }
    }

    @Nested
    @DisplayName("멱등성 — Dedup")
    class Idempotency {

        @Test
        @DisplayName("동일 messageId 재처리 시 재고가 이중 복구되지 않는다")
        void dedup_skipsDuplicate() {
            UUID messageId = UUID.randomUUID();
            String payload = buildPayload(UUID.randomUUID(), eventId, 5);

            stockRestoreService.restoreStockForPaymentFailed(messageId, "payment.failed", payload);
            stockRestoreService.restoreStockForPaymentFailed(messageId, "payment.failed", payload);

            Event updated = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(updated.getRemainingQuantity()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("정책적 스킵")
    class PolicySkip {

        @Test
        @DisplayName("FORCE_CANCELLED 이벤트는 재고 복구를 스킵한다")
        void skip_forceCancelled() {
            ReflectionTestUtils.setField(testEvent, "status", EventStatus.FORCE_CANCELLED);
            eventRepository.saveAndFlush(testEvent);

            UUID messageId = UUID.randomUUID();
            String payload = buildPayload(UUID.randomUUID(), eventId, 5);

            stockRestoreService.restoreStockForPaymentFailed(messageId, "payment.failed", payload);

            Event updated = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(updated.getRemainingQuantity()).isEqualTo(95);
            assertThat(processedMessageRepository.existsByMessageId(messageId.toString())).isTrue();
        }

        @Test
        @DisplayName("CANCELLED 이벤트는 재고 복구를 스킵한다")
        void skip_cancelled() {
            ReflectionTestUtils.setField(testEvent, "status", EventStatus.CANCELLED);
            eventRepository.saveAndFlush(testEvent);

            UUID messageId = UUID.randomUUID();
            String payload = buildPayload(UUID.randomUUID(), eventId, 5);

            stockRestoreService.restoreStockForPaymentFailed(messageId, "payment.failed", payload);

            Event updated = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(updated.getRemainingQuantity()).isEqualTo(95);
        }
    }

    @Nested
    @DisplayName("에러 케이스")
    class ErrorCases {

        @Test
        @DisplayName("존재하지 않는 eventId면 예외를 던진다")
        void throwsOnMissingEvent() {
            UUID messageId = UUID.randomUUID();
            UUID fakeEventId = UUID.randomUUID();
            String payload = buildPayload(UUID.randomUUID(), fakeEventId, 5);

            assertThatThrownBy(() ->
                stockRestoreService.restoreStockForPaymentFailed(messageId, "payment.failed", payload)
            ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Event not found");
        }

        @Test
        @DisplayName("잘못된 JSON payload면 예외를 던진다")
        void throwsOnInvalidPayload() {
            UUID messageId = UUID.randomUUID();

            assertThatThrownBy(() ->
                stockRestoreService.restoreStockForPaymentFailed(messageId, "payment.failed", "invalid json")
            ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("역직렬화 실패");
        }
    }
}
