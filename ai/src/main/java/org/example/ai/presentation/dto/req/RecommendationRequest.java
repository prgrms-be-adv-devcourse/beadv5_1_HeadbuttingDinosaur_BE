package org.example.ai.presentation.dto.req;

import jakarta.validation.constraints.NotBlank;

public record RecommendationRequest(
    @NotBlank
    String userId
) {

}
