package com.devticket.event.common.messaging;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class PayloadExtractor {

    private PayloadExtractor() {}

    public static String extract(ObjectMapper objectMapper, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            JsonNode payloadNode = root.get("payload");
            if (payloadNode != null && !payloadNode.isNull()) {
                // payload 가 String 값이면 asText(), 객체/배열이면 toString() 으로 JSON 직렬화
                return payloadNode.isValueNode() ? payloadNode.asString() : payloadNode.toString();
            }
        } catch (Exception ignored) {
            // wrapper 가 아니면 원본 반환
        }
        return value;
    }
}