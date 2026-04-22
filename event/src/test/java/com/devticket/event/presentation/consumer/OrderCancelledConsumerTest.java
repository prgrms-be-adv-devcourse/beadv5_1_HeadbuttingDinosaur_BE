package com.devticket.event.presentation.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devticket.event.application.MessageDeduplicationService;
import com.devticket.event.application.OrderCancelledService;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class OrderCancelledConsumerTest {

    @InjectMocks
    private OrderCancelledConsumer consumer;

    @Mock
    private OrderCancelledService orderCancelledService;

    @Mock
    private MessageDeduplicationService deduplicationService;

    @Mock
    private Acknowledgment ack;

    private ConsumerRecord<String, String> createRecord(UUID messageId, String payload) {
        RecordHeaders headers = new RecordHeaders();
        headers.add("X-Message-Id", messageId.toString().getBytes(StandardCharsets.UTF_8));
        return new ConsumerRecord<>("order.cancelled", 0, 0L, 0L,
            TimestampType.CREATE_TIME, 0, payload.length(), null, payload,
            headers, Optional.empty());
    }

    @Nested
    @DisplayName("정상 처리")
    class NormalProcessing {

        @Test
        @DisplayName("메시지 처리 성공 시 ACK한다")
        void success_thenAck() {
            UUID messageId = UUID.randomUUID();
            ConsumerRecord<String, String> record = createRecord(messageId, "{}");
            doNothing().when(orderCancelledService)
                .restoreStockForOrderCancelled(any(), any(), any());

            consumer.consume(record, ack);

            verify(orderCancelledService).restoreStockForOrderCancelled(
                eq(messageId), eq("order.cancelled"), eq("{}"));
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("헤더 누락")
    class MissingHeader {

        @Test
        @DisplayName("X-Message-Id 헤더가 없으면 예외를 던진다")
        void noHeader_throwsException() {
            RecordHeaders headers = new RecordHeaders();
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "order.cancelled", 0, 0L, 0L, TimestampType.CREATE_TIME,
                0, 2, null, "{}", headers, Optional.empty());

            assertThatThrownBy(() -> consumer.consume(record, ack))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("X-Message-Id");

            verify(ack, never()).acknowledge();
        }
    }

    @Nested
    @DisplayName("OptimisticLockException 처리")
    class OptimisticLockHandling {

        @Test
        @DisplayName("@Version 충돌 + 이미 처리됨 → 스킵 + ACK")
        void optimisticLock_alreadyProcessed_thenSkip() {
            UUID messageId = UUID.randomUUID();
            ConsumerRecord<String, String> record = createRecord(messageId, "{}");

            doThrow(new ObjectOptimisticLockingFailureException("Event", messageId))
                .when(orderCancelledService).restoreStockForOrderCancelled(any(), any(), any());
            given(deduplicationService.isDuplicate(messageId)).willReturn(true);

            consumer.consume(record, ack);

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("@Version 충돌 + 아직 미처리 → rethrow (재시도)")
        void optimisticLock_notProcessed_thenRethrow() {
            UUID messageId = UUID.randomUUID();
            ConsumerRecord<String, String> record = createRecord(messageId, "{}");

            doThrow(new ObjectOptimisticLockingFailureException("Event", messageId))
                .when(orderCancelledService).restoreStockForOrderCancelled(any(), any(), any());
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);

            assertThatThrownBy(() -> consumer.consume(record, ack))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

            verify(ack, never()).acknowledge();
        }
    }

    @Nested
    @DisplayName("DataIntegrityViolation 처리")
    class UniqueConstraintHandling {

        @Test
        @DisplayName("UNIQUE 충돌 → 스킵 + ACK")
        void uniqueViolation_thenSkip() {
            UUID messageId = UUID.randomUUID();
            ConsumerRecord<String, String> record = createRecord(messageId, "{}");

            doThrow(new DataIntegrityViolationException("unique constraint"))
                .when(orderCancelledService).restoreStockForOrderCancelled(any(), any(), any());

            consumer.consume(record, ack);

            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("기타 예외")
    class OtherExceptions {

        @Test
        @DisplayName("예상치 못한 예외 → rethrow (재시도) + ACK 안 함")
        void unexpectedException_thenRethrow() {
            UUID messageId = UUID.randomUUID();
            ConsumerRecord<String, String> record = createRecord(messageId, "{}");

            doThrow(new RuntimeException("unexpected"))
                .when(orderCancelledService).restoreStockForOrderCancelled(any(), any(), any());

            assertThatThrownBy(() -> consumer.consume(record, ack))
                .isInstanceOf(RuntimeException.class);

            verify(ack, never()).acknowledge();
        }
    }
}
