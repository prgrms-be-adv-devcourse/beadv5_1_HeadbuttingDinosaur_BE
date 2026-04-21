package com.devticket.member.presentation.dto.internal.response;

import java.util.List;

public record InternalAdminTechStackResponse(
    List<TechStackInfo> techStacks
) {
    public record TechStackInfo(
        Long id,
        String name
    ) {}
}


