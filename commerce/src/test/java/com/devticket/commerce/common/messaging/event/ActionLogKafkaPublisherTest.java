package com.devticket.commerce.common.messaging.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class ActionLogKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, String> actionLogKafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private ActionLogKafkaPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ActionLogKafkaPublisher(actionLogKafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("정상 발행 — topic=action.log, key=userId, value=JSON payload")
    void publish_정상_발행시_topic_key_payload를_포함해_전송한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        ActionLogDomainEvent domain = createDomain(userId, ActionType.CART_ADD, 2, 20000L);
        String expectedPayload = "{\"userId\":\"" + userId + "\"}";
        given(objectMapper.writeValueAsString(any(ActionLogEvent.class))).willReturn(expectedPayload);

        // when
        publisher.publish(domain);

        // then
        verify(actionLogKafkaTemplate).send("action.log", userId.toString(), expectedPayload);
    }

    @Test
    @DisplayName("JsonProcessingException 발생 시 예외 전파 없이 스킵 — send 미호출")
    void publish_JsonProcessingException_발생시_스킵한다() throws Exception {
        // given
        ActionLogDomainEvent domain = createDomain(UUID.randomUUID(), ActionType.CART_ADD, 1, 1000L);
        given(objectMapper.writeValueAsString(any(ActionLogEvent.class)))
                .willThrow(new JsonProcessingException("직렬화 실패") {});

        // when — 예외 전파 없음
        publisher.publish(domain);

        // then — send 미호출 (at-most-once)
        verifyNoInteractions(actionLogKafkaTemplate);
    }

    @Test
    @DisplayName("KafkaTemplate.send RuntimeException 발생 시 예외 전파 없이 스킵")
    void publish_RuntimeException_발생시_스킵한다() throws Exception {
        // given — send()에서 RuntimeException
        ActionLogDomainEvent domain = createDomain(UUID.randomUUID(), ActionType.CART_REMOVE, 1, 1000L);
        given(objectMapper.writeValueAsString(any(ActionLogEvent.class))).willReturn("{}");
        given(actionLogKafkaTemplate.send(anyString(), anyString(), anyString()))
                .willThrow(new RuntimeException("broker down"));

        // when — 예외 전파 없음
        publisher.publish(domain);

        // then — send 1회 호출 후 예외 소화 (비즈니스 영향 없음)
        verify(actionLogKafkaTemplate).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("ActionLogEvent.from() 매핑 — 9필드 정확히 ObjectMapper에 전달된다")
    void publish_ActionLogEvent_9필드_매핑을_ObjectMapper에_전달한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-04-21T00:00:00Z");
        ActionLogDomainEvent domain = new ActionLogDomainEvent(
                userId, eventId, ActionType.CART_ADD,
                "concert", "rock", null,
                2, 20000L, now);
        ArgumentCaptor<ActionLogEvent> captor = ArgumentCaptor.forClass(ActionLogEvent.class);
        given(objectMapper.writeValueAsString(captor.capture())).willReturn("{}");

        // when
        publisher.publish(domain);

        // then
        ActionLogEvent event = captor.getValue();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.actionType()).isEqualTo(ActionType.CART_ADD);
        assertThat(event.searchKeyword()).isEqualTo("concert");
        assertThat(event.stackFilter()).isEqualTo("rock");
        assertThat(event.dwellTimeSeconds()).isNull();
        assertThat(event.quantity()).isEqualTo(2);
        assertThat(event.totalAmount()).isEqualTo(20000L);
        assertThat(event.timestamp()).isEqualTo(now);
    }

    private ActionLogDomainEvent createDomain(UUID userId, ActionType type, int quantity, Long totalAmount) {
        return new ActionLogDomainEvent(
                userId, UUID.randomUUID(), type,
                null, null, null,
                quantity, totalAmount, Instant.now());
    }
}
