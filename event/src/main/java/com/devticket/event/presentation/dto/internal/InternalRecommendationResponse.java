package com.devticket.event.presentation.dto.internal;

import com.devticket.event.presentation.dto.EventListContentResponse;
import java.util.List;

public record InternalRecommendationResponse(List<EventListContentResponse> events) {

    public static InternalRecommendationResponse empty() {
        return new InternalRecommendationResponse(List.of());
    }
}
