package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.TechStack;

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
