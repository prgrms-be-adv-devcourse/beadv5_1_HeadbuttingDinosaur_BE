package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.TechStack;

public record DeleteTechStackResponse(
    Long id
) {
    public static DeleteTechStackResponse of(Long id) {
        return new DeleteTechStackResponse(id);
    }
}
