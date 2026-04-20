package com.devticket.event.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devticket.event.common.messaging.event.ActionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class ActionLogKafkaPublisherTest {

    @Mock
    KafkaTemplate<String, String> actionLogKafkaTemplate;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    ActionLogKafkaPublisher publisher;

    @Test
    void publish_정상_시나리오_send_호출_topic_key_payload_검증() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ActionLogDomainEvent domain = new ActionLogDomainEvent(
            userId, eventId, ActionType.DETAIL_VIEW,
            null, null, null, null, null, Instant.now()
        );
        String payload = "{\"userId\":\"" + userId + "\"}";
        given(objectMapper.writeValueAsString(any())).willReturn(payload);

        publisher.publish(domain);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(actionLogKafkaTemplate).send(topic.capture(), key.capture(), body.capture());

        assertThat(topic.getValue()).isEqualTo("action.log");
        assertThat(key.getValue()).isEqualTo(userId.toString());
        assertThat(body.getValue()).isEqualTo(payload);
    }

    @Test
    void publish_직렬화_실패시_send_미호출_예외_전파_없음() throws Exception {
        UUID userId = UUID.randomUUID();
        ActionLogDomainEvent domain = new ActionLogDomainEvent(
            userId, null, ActionType.VIEW,
            null, null, null, null, null, Instant.now()
        );
        given(objectMapper.writeValueAsString(any())).willThrow(mock(JsonProcessingException.class));

        assertThatCode(() -> publisher.publish(domain)).doesNotThrowAnyException();
        verify(actionLogKafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void publish_Kafka_send_예외시_skip_예외_전파_없음() throws Exception {
        UUID userId = UUID.randomUUID();
        ActionLogDomainEvent domain = new ActionLogDomainEvent(
            userId, null, ActionType.VIEW,
            null, null, null, null, null, Instant.now()
        );
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(actionLogKafkaTemplate.send(anyString(), anyString(), anyString()))
            .willThrow(new RuntimeException("broker down"));

        assertThatCode(() -> publisher.publish(domain)).doesNotThrowAnyException();
    }
}
