package com.devticket.event.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.devticket.event.common.messaging.event.PaymentFailedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JacksonConfig — 하위호환성 역직렬화 검증")
class JacksonConfigTest {

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    @Test
    void 알수없는_필드가_포함된_PaymentFailedEvent_JSON_역직렬화_성공() {
        // given — 상류 서비스가 신규 필드(unknownField, futureField)를 추가한 payload
        String json = """
            {
              "orderId": "123e4567-e89b-12d3-a456-426614174000",
              "userId": "223e4567-e89b-12d3-a456-426614174000",
              "orderItems": [
                {"eventId": "323e4567-e89b-12d3-a456-426614174000", "quantity": 2}
              ],
              "reason": "PAYMENT_DECLINED",
              "timestamp": "2026-04-20T10:00:00Z",
              "unknownField": "future-added-by-upstream",
              "futureField": 42
            }
            """;

        // when & then — FAIL_ON_UNKNOWN_PROPERTIES=false 로 unknown 필드 무시
        assertThatCode(() -> {
            PaymentFailedEvent event = objectMapper.readValue(json, PaymentFailedEvent.class);
            assertThat(event.orderId()).isNotNull();
            assertThat(event.orderItems()).hasSize(1);
            assertThat(event.orderItems().get(0).quantity()).isEqualTo(2);
            assertThat(event.reason()).isEqualTo("PAYMENT_DECLINED");
        }).doesNotThrowAnyException();
    }

    @Test
    void 기존_필드만_포함된_PaymentFailedEvent_JSON_역직렬화_성공() {
        // given — 확장 이전 payload (하위호환성 회귀 방어)
        String json = """
            {
              "orderId": "123e4567-e89b-12d3-a456-426614174000",
              "userId": "223e4567-e89b-12d3-a456-426614174000",
              "orderItems": [
                {"eventId": "323e4567-e89b-12d3-a456-426614174000", "quantity": 1}
              ],
              "reason": "PAYMENT_DECLINED",
              "timestamp": "2026-04-20T10:00:00Z"
            }
            """;

        // when & then
        assertThatCode(() -> {
            PaymentFailedEvent event = objectMapper.readValue(json, PaymentFailedEvent.class);
            assertThat(event).isNotNull();
            assertThat(event.orderItems()).hasSize(1);
        }).doesNotThrowAnyException();
    }

    @Test
    void 중첩_OrderItem_에_unknown_필드_포함된_JSON_역직렬화_성공() {
        // given — nested record 에도 동일 정책 적용되는지 검증
        String json = """
            {
              "orderId": "123e4567-e89b-12d3-a456-426614174000",
              "userId": "223e4567-e89b-12d3-a456-426614174000",
              "orderItems": [
                {
                  "eventId": "323e4567-e89b-12d3-a456-426614174000",
                  "quantity": 3,
                  "unitPrice": 10000,
                  "futureMeta": {"key": "value"}
                }
              ],
              "reason": "PG_TIMEOUT",
              "timestamp": "2026-04-20T10:00:00Z"
            }
            """;

        // when & then
        assertThatCode(() -> {
            PaymentFailedEvent event = objectMapper.readValue(json, PaymentFailedEvent.class);
            assertThat(event.orderItems().get(0).quantity()).isEqualTo(3);
        }).doesNotThrowAnyException();
    }
}
