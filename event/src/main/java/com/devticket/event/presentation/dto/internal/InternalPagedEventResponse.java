package com.devticket.event.presentation.dto.internal;

import java.util.List;
import org.springframework.data.domain.Page;

public record InternalPagedEventResponse(
    List<InternalAdminEventResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static InternalPagedEventResponse from(Page<InternalAdminEventResponse> page) {
        return new InternalPagedEventResponse(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}