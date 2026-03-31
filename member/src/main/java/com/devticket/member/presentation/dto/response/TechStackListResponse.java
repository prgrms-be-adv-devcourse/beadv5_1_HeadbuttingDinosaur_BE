package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.TechStack;
import java.util.List;

public record TechStackListResponse(
    List<TechStackItem> techStacks
) {
    public record TechStackItem(Long techStackId, String name) {}

    public static TechStackListResponse from(List<TechStack> stacks) {
        List<TechStackItem> items = stacks.stream()
            .map(s -> new TechStackItem(s.getId(), s.getName()))
            .toList();
        return new TechStackListResponse(items);
    }
}

