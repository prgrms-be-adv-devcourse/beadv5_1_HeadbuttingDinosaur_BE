package com.devticket.commerce.common.messaging.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.commerce.common.config.JacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActionLogEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 운영 ObjectMapper 동일 설정 — JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS=false + FAIL_ON_UNKNOWN_PROPERTIES=false
        objectMapper = new JacksonConfig().objectMapper();
    }

    @Test
    @DisplayName("from(ActionLogDomainEvent) — 9필드 매핑 정확성")
    void from_9필드_매핑_정확성() {
        // given
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-04-21T00:00:00Z");
        ActionLogDomainEvent domain = new ActionLogDomainEvent(
                userId, eventId, ActionType.CART_ADD,
                "concert", "rock", 30,
                2, 20000L, now);

        // when
        ActionLogEvent event = ActionLogEvent.from(domain);

        // then
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.actionType()).isEqualTo(ActionType.CART_ADD);
        assertThat(event.searchKeyword()).isEqualTo("concert");
        assertThat(event.stackFilter()).isEqualTo("rock");
        assertThat(event.dwellTimeSeconds()).isEqualTo(30);
        assertThat(event.quantity()).isEqualTo(2);
        assertThat(event.totalAmount()).isEqualTo(20000L);
        assertThat(event.timestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("Instant ISO-8601 직렬화 — WRITE_DATES_AS_TIMESTAMPS=false 적용")
    void Instant_ISO_8601_직렬화() throws Exception {
        // given
        Instant fixed = Instant.parse("2026-04-21T00:00:00Z");
        ActionLogEvent event = new ActionLogEvent(
                UUID.randomUUID(), UUID.randomUUID(), ActionType.VIEW,
                null, null, null, null, null, fixed);

        // when
        String json = objectMapper.writeValueAsString(event);

        // then — 숫자 timestamp가 아닌 ISO-8601 문자열
        assertThat(json).contains("\"timestamp\":\"2026-04-21T00:00:00Z\"");
    }

    @Test
    @DisplayName("nullable 필드 직렬화 안전 — eventId/quantity/totalAmount null")
    void nullable_필드_직렬화_안전() throws Exception {
        // given
        ActionLogEvent event = new ActionLogEvent(
                UUID.randomUUID(), null, ActionType.VIEW,
                null, null, null, null, null, Instant.now());

        // when
        String json = objectMapper.writeValueAsString(event);

        // then — JSON null로 직렬화 (kafkajs가 null/undefined 처리 가능)
        assertThat(json).contains("\"eventId\":null");
        assertThat(json).contains("\"quantity\":null");
        assertThat(json).contains("\"totalAmount\":null");
    }

    @Test
    @DisplayName("미지 필드 포함 payload 역직렬화 성공 — @JsonIgnoreProperties(ignoreUnknown=true)")
    void 미지_필드_포함_payload_역직렬화_성공() throws Exception {
        // given — 미래 Producer가 추가할 수 있는 미지 필드 포함
        UUID userId = UUID.randomUUID();
        String payload = "{"
                + "\"userId\":\"" + userId + "\","
                + "\"eventId\":null,"
                + "\"actionType\":\"VIEW\","
                + "\"searchKeyword\":null,"
                + "\"stackFilter\":null,"
                + "\"dwellTimeSeconds\":null,"
                + "\"quantity\":null,"
                + "\"totalAmount\":null,"
                + "\"timestamp\":\"2026-04-21T00:00:00Z\","
                + "\"futureField\":\"ignored\","
                + "\"anotherUnknown\":42"
                + "}";

        // when & then — 미지 필드 무시하고 역직렬화 성공
        ActionLogEvent event = objectMapper.readValue(payload, ActionLogEvent.class);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.actionType()).isEqualTo(ActionType.VIEW);
        assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-04-21T00:00:00Z"));
    }

    @Test
    @DisplayName("CART_ADD 7종 actionType round-trip — 직렬화·역직렬화 대칭")
    void actionType_7종_round_trip() throws Exception {
        for (ActionType type : ActionType.values()) {
            ActionLogEvent original = new ActionLogEvent(
                    UUID.randomUUID(), UUID.randomUUID(), type,
                    null, null, null, null, null,
                    Instant.parse("2026-04-21T00:00:00Z"));

            String json = objectMapper.writeValueAsString(original);
            ActionLogEvent restored = objectMapper.readValue(json, ActionLogEvent.class);

            assertThat(restored.actionType()).isEqualTo(type);
        }
    }
}
