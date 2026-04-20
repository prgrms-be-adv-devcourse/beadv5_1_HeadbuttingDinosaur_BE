package com.devticket.payment.common.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OutboxEventProducer 가 메시지를 `{messageId, eventType, payload, timestamp}` 형태의
 * 래퍼로 감싸 Kafka 에 발행하므로, 소비자는 래퍼의 `payload` 필드를 추출하여 실제 이벤트로
 * 역직렬화해야 한다.
 */
public final class OutboxPayloadExtractor {

    private OutboxPayloadExtractor() {
    }

    public static <T> T extract(ObjectMapper objectMapper, String recordValue, Class<T> eventClass) {
        try {
            JsonNode root = objectMapper.readTree(recordValue);
            JsonNode payloadNode = root.get("payload");
            if (payloadNode != null && payloadNode.isTextual()) {
                return objectMapper.readValue(payloadNode.asText(), eventClass);
            }
            // Fallback — 메시지가 래퍼 없이 직접 페이로드 형태일 수도 있음
            return objectMapper.treeToValue(root, eventClass);
        } catch (Exception e) {
            throw new IllegalStateException("Outbox payload 역직렬화 실패: " + eventClass.getSimpleName(), e);
        }
    }
}
