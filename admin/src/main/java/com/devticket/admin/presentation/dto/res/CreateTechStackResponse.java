package com.devticket.admin.presentation.dto.res;

import com.devticket.admin.domain.model.TechStack;

public record CreateTechStackResponse(
    Long id,
    String name
) {
    public static CreateTechStackResponse from(TechStack techStack) {
        return new CreateTechStackResponse(
            techStack.getId(),
            techStack.getName()
        );
    }
}
