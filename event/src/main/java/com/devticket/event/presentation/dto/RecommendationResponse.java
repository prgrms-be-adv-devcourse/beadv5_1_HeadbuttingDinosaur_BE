package com.devticket.event.presentation.dto;

import java.util.List;

public record RecommendationResponse(List<EventListContentResponse> events) {

    public static RecommendationResponse empty() {
        return new RecommendationResponse(List.of());
    }
}
