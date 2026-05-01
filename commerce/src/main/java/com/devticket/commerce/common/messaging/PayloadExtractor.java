package com.devticket.commerce.common.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Outbox wrapper 가 씌워진 Kafka record value 에서 실제 payload JSON 을 뽑아낸다. wrapper 없이 들어오면 원본 그대로 반환 (양방향 호환).
 * <p>
 * wrapper 형태: {"messageId":"...","eventType":"...","payload":"{...실제 이벤트 JSON...}","timestamp":"..."}
 */
public final class PayloadExtractor {

    private PayloadExtractor() {
    }

    public static String extract(ObjectMapper objectMapper, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            JsonNode payloadNode = root.get("payload");
            if (payloadNode != null && !payloadNode.isNull()) {
                return payloadNode.isTextual() ? payloadNode.asText() : payloadNode.toString();
            }
        } catch (Exception ignored) {
            // 파싱 실패 → wrapper 가 아닌 것으로 간주, 원본 반환
        }
        return value;
    }
}
