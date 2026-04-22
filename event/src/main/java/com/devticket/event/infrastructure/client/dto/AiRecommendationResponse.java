package com.devticket.event.infrastructure.client.dto;

import java.util.List;

public record AiRecommendationResponse(String userId, List<String> eventIdList) {
}
