package com.devticket.event.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devticket.event.common.messaging.event.RefundCompletedEvent;
import com.devticket.event.domain.enums.PaymentMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RefundCompletedService 역직렬화 회귀 가드.
 *
 * <p>Payment 의 PaymentMethod enum 이 추가/변경될 때 Event 측이 동기화되지 않아 발생한
 * 운영 장애(2026-05-01 KST, "Cannot deserialize value of type PaymentMethod from String 'WALLET_PG'")
 * 를 방지한다.
 */
@ExtendWith(MockitoExtension.class)
class RefundCompletedServiceTest {

    @Mock
    MessageDeduplicationService deduplicationService;

    private ObjectMapper objectMapper;
    private RefundCompletedService service;

    @BeforeEach
    void setUp() {
        // Spring Boot 의 ObjectMapper 와 동일하게 JavaTimeModule 등록
        objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();
        service = new RefundCompletedService(deduplicationService, objectMapper);
    }

    @Nested
    @DisplayName("paymentMethod enum 동기화 — 모든 값이 역직렬화 가능해야 한다 (Payment 와 동기화)")
    class PaymentMethodSyncTest {

        @Test
        @DisplayName("WALLET_PG — Payment 가 추가한 값 역직렬화 성공 (회귀 가드: 2026-05-01 KST 운영 장애)")
        void walletPg_역직렬화() {
            UUID messageId = UUID.randomUUID();
            String payload = buildPayload("WALLET_PG");
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);

            assertThatCode(() -> service.recordRefundCompleted(messageId, "refund.completed", payload))
                .doesNotThrowAnyException();

            verify(deduplicationService).markProcessed(messageId, "refund.completed");
        }

        @Test
        @DisplayName("WALLET — 기존 값 역직렬화 성공")
        void wallet_역직렬화() {
            assertThatCode(() -> service.recordRefundCompleted(
                UUID.randomUUID(), "refund.completed", buildPayload("WALLET")))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PG — 기존 값 역직렬화 성공")
        void pg_역직렬화() {
            assertThatCode(() -> service.recordRefundCompleted(
                UUID.randomUUID(), "refund.completed", buildPayload("PG")))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("enum 정의가 Payment 와 어긋나지 않도록 PaymentMethod 의 모든 값이 직렬화/역직렬화 라운드트립을 통과해야 한다")
        void enum_라운드트립() {
            for (PaymentMethod method : PaymentMethod.values()) {
                String payload = buildPayload(method.name());
                assertThatCode(() -> service.recordRefundCompleted(
                    UUID.randomUUID(), "refund.completed", payload))
                    .as("PaymentMethod.%s round-trip", method.name())
                    .doesNotThrowAnyException();
            }
        }
    }

    @Nested
    @DisplayName("기본 동작")
    class BasicTest {

        @Test
        @DisplayName("dedup hit — 역직렬화/markProcessed 모두 스킵")
        void dedup_스킵() {
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(true);

            // 일부러 잘못된 페이로드를 줘도 역직렬화 진입 안 하므로 통과해야 함
            service.recordRefundCompleted(messageId, "refund.completed", "{ broken json");

            verify(deduplicationService, never()).markProcessed(any(), any());
        }

        @Test
        @DisplayName("역직렬화 실패 — IllegalArgumentException")
        void 역직렬화_실패() {
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);

            assertThatThrownBy(() -> service.recordRefundCompleted(
                messageId, "refund.completed", "{\"paymentMethod\":\"UNKNOWN_METHOD\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RefundCompletedEvent 역직렬화 실패");
        }
    }

    private String buildPayload(String paymentMethod) {
        try {
            RefundCompletedEvent event = RefundCompletedEvent.builder()
                .refundId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .paymentMethod(PaymentMethod.valueOf(paymentMethod))
                .refundAmount(10_000)
                .refundRate(100)
                .timestamp(Instant.now())
                .build();
            // wrapper 없이 직접 보내는 경우 — PayloadExtractor 가 원본 반환
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
