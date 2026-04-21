package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.TechStack;

public record UpdateTechStackResponse(
    Long id,
    String name
) {
    public static UpdateTechStackResponse from(TechStack techStack){
        return new UpdateTechStackResponse(
            techStack.getId(),
            techStack.getName()
        );
    }
}
