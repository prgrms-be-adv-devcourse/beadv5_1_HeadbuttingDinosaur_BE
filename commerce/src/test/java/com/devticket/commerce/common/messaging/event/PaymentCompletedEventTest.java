package com.devticket.commerce.common.messaging.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.commerce.common.config.JacksonConfig;
import com.devticket.commerce.common.enums.PaymentMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentCompletedEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 운영 ObjectMapper와 동일 설정 — FAIL_ON_UNKNOWN_PROPERTIES=false 포함
        objectMapper = new JacksonConfig().objectMapper();
    }

    @Test
    @DisplayName("신버전 payload(orderItems 포함) 역직렬화 성공")
    void orderItems_포함_payload_역직렬화_성공() throws Exception {
        // given — Payment Producer 신버전 발행 payload
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        String payload = "{"
                + "\"orderId\":\"" + orderId + "\","
                + "\"userId\":\"" + userId + "\","
                + "\"paymentId\":\"" + paymentId + "\","
                + "\"paymentMethod\":\"PG\","
                + "\"totalAmount\":30000,"
                + "\"orderItems\":["
                + "{\"eventId\":\"" + eventId1 + "\",\"quantity\":2},"
                + "{\"eventId\":\"" + eventId2 + "\",\"quantity\":1}"
                + "],"
                + "\"timestamp\":\"2026-04-20T10:00:00Z\""
                + "}";

        // when
        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);

        // then
        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.paymentId()).isEqualTo(paymentId);
        assertThat(event.paymentMethod()).isEqualTo(PaymentMethod.PG);
        assertThat(event.totalAmount()).isEqualTo(30000);
        assertThat(event.orderItems()).hasSize(2);
        assertThat(event.orderItems().get(0).eventId()).isEqualTo(eventId1);
        assertThat(event.orderItems().get(0).quantity()).isEqualTo(2);
        assertThat(event.orderItems().get(1).eventId()).isEqualTo(eventId2);
        assertThat(event.orderItems().get(1).quantity()).isEqualTo(1);
        assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-04-20T10:00:00Z"));
    }

    @Test
    @DisplayName("구버전 payload(orderItems 없음) 역직렬화 성공 — 배포 순서 역전 방어")
    void orderItems_없는_구버전_payload_역직렬화_성공() throws Exception {
        // given — Payment Producer 구버전 payload (orderItems 필드 없음)
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String payload = "{"
                + "\"orderId\":\"" + orderId + "\","
                + "\"userId\":\"" + userId + "\","
                + "\"paymentId\":\"" + paymentId + "\","
                + "\"paymentMethod\":\"WALLET\","
                + "\"totalAmount\":20000,"
                + "\"timestamp\":\"2026-04-20T10:00:00Z\""
                + "}";

        // when
        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);

        // then — orderItems 생략 시 Record 기본 null
        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.paymentMethod()).isEqualTo(PaymentMethod.WALLET);
        assertThat(event.totalAmount()).isEqualTo(20000);
        assertThat(event.orderItems()).isNull();
    }

    @Test
    @DisplayName("알 수 없는 필드 포함 payload 역직렬화 성공 — FAIL_ON_UNKNOWN_PROPERTIES=false 효과")
    void 알수없는_필드_포함_payload_역직렬화_성공() throws Exception {
        // given — 미래 Producer가 추가할 수 있는 미지 필드 포함
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String payload = "{"
                + "\"orderId\":\"" + orderId + "\","
                + "\"userId\":\"" + userId + "\","
                + "\"paymentId\":\"" + paymentId + "\","
                + "\"paymentMethod\":\"PG\","
                + "\"totalAmount\":15000,"
                + "\"timestamp\":\"2026-04-20T10:00:00Z\","
                + "\"futureField\":\"unexpectedValue\","
                + "\"anotherUnknown\":123"
                + "}";

        // when & then — 알 수 없는 필드 무시하고 역직렬화 성공
        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.totalAmount()).isEqualTo(15000);
    }
}
