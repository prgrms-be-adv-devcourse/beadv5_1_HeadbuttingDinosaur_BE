package org.example.ai.presentation.dto.res;

import java.util.List;

public record RecommendationResponse(
    String userId,
    List<String> eventIdList
) {

}
