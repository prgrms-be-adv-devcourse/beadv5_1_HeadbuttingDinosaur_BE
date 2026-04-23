package com.devticket.payment.wallet.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentFailedEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @Test
    @DisplayName("orderItems가 JSON payload에 직렬화된다")
    void orderItems_직렬화_포함() throws Exception {
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        PaymentFailedEvent event = PaymentFailedEvent.builder()
            .orderId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .orderItems(List.of(
                new PaymentFailedEvent.OrderItem(eventId1, 2),
                new PaymentFailedEvent.OrderItem(eventId2, 1)
            ))
            .reason("PG_TIMEOUT")
            .timestamp(Instant.now())
            .build();

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("orderItems")).isTrue();
        JsonNode items = node.get("orderItems");
        assertThat(items.isArray()).isTrue();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("eventId").asText()).isEqualTo(eventId1.toString());
        assertThat(items.get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(items.get(1).get("eventId").asText()).isEqualTo(eventId2.toString());
        assertThat(items.get(1).get("quantity").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("orderItems가 빈 리스트여도 직렬화 가능")
    void 빈_orderItems_직렬화() throws Exception {
        PaymentFailedEvent event = PaymentFailedEvent.builder()
            .orderId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .orderItems(List.of())
            .reason("INSUFFICIENT_BALANCE")
            .timestamp(Instant.now())
            .build();

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("orderItems")).isTrue();
        assertThat(node.get("orderItems").isArray()).isTrue();
        assertThat(node.get("orderItems")).isEmpty();
    }

    @Test
    @DisplayName("역직렬화 후 OrderItem 값이 보존된다")
    void 역직렬화_orderItems_보존() throws Exception {
        UUID eventId = UUID.randomUUID();
        PaymentFailedEvent original = PaymentFailedEvent.builder()
            .orderId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .orderItems(List.of(new PaymentFailedEvent.OrderItem(eventId, 3)))
            .reason("STOCK_FAIL")
            .timestamp(Instant.now())
            .build();

        String json = objectMapper.writeValueAsString(original);
        PaymentFailedEvent deserialized = objectMapper.readValue(json, PaymentFailedEvent.class);

        assertThat(deserialized.orderItems()).hasSize(1);
        assertThat(deserialized.orderItems().get(0).eventId()).isEqualTo(eventId);
        assertThat(deserialized.orderItems().get(0).quantity()).isEqualTo(3);
        assertThat(deserialized.reason()).isEqualTo("STOCK_FAIL");
    }

    @Test
    @DisplayName("알 수 없는 필드가 있어도 역직렬화 성공 (하위 호환성)")
    void 알_수_없는_필드_무시() throws Exception {
        String json = "{"
            + "\"orderId\":\"" + UUID.randomUUID() + "\","
            + "\"userId\":\"" + UUID.randomUUID() + "\","
            + "\"orderItems\":[],"
            + "\"reason\":\"PG_TIMEOUT\","
            + "\"timestamp\":\"2025-01-01T00:00:00Z\","
            + "\"unknownFutureField\":\"some-value\","
            + "\"anotherNewField\":123"
            + "}";

        PaymentFailedEvent event = objectMapper.readValue(json, PaymentFailedEvent.class);

        assertThat(event.reason()).isEqualTo("PG_TIMEOUT");
        assertThat(event.orderItems()).isEmpty();
    }

    @Test
    @DisplayName("OrderItem 내부에 알 수 없는 필드가 있어도 역직렬화 성공")
    void OrderItem_알_수_없는_필드_무시() throws Exception {
        UUID eventId = UUID.randomUUID();
        String json = "{"
            + "\"orderId\":\"" + UUID.randomUUID() + "\","
            + "\"userId\":\"" + UUID.randomUUID() + "\","
            + "\"orderItems\":[{"
            +     "\"eventId\":\"" + eventId + "\","
            +     "\"quantity\":2,"
            +     "\"futureFieldOnItem\":\"x\""
            + "}],"
            + "\"reason\":\"WALLET_PG_TIMEOUT\","
            + "\"timestamp\":\"2025-01-01T00:00:00Z\""
            + "}";

        PaymentFailedEvent event = objectMapper.readValue(json, PaymentFailedEvent.class);

        assertThat(event.orderItems()).hasSize(1);
        assertThat(event.orderItems().get(0).eventId()).isEqualTo(eventId);
        assertThat(event.orderItems().get(0).quantity()).isEqualTo(2);
    }
}
