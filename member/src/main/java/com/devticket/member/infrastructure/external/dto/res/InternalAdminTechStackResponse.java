package com.devticket.member.infrastructure.external.dto.res;

import java.util.List;

public record InternalAdminTechStackResponse(
    List<TechStackInfo> techStacks
) {
    public record TechStackInfo(
        Long id,
        String name
    ) {}
}


