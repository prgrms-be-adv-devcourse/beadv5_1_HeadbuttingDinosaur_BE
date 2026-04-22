package com.devticket.member.presentation.dto.internal.response;

import java.util.List;

public record InternalUserTechStackResponse(
    String userId,
    List<TechStackInfo> techStacks
) {
    public record TechStackInfo(
        String techStackId,
        String name
    ) {}
}
