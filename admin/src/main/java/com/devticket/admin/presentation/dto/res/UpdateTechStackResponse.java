package com.devticket.admin.presentation.dto.res;

import com.devticket.admin.domain.model.TechStack;

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
