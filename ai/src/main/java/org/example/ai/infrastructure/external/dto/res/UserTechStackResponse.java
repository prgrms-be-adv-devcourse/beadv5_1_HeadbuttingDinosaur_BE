package org.example.ai.infrastructure.external.dto.res;

import java.util.List;

public record UserTechStackResponse(
    String userId,
    List<TechStackInfo> techStacks
) {
    public record TechStackInfo(
        String techStackId,
        String name
    ){}
}
