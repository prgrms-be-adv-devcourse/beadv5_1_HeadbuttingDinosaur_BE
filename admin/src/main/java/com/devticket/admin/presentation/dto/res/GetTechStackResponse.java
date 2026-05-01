package com.devticket.admin.presentation.dto.res;

import com.devticket.admin.domain.model.TechStack;

public record GetTechStackResponse(
    Long id,
    String name
) {
    public static GetTechStackResponse from(TechStack techStack) {
        return new GetTechStackResponse(
            techStack.getId(),
            techStack.getName()
        );
    }
}
