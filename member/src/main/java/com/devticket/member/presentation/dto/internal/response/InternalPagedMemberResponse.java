package com.devticket.member.presentation.dto.internal.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record InternalPagedMemberResponse(
    List<InternalMemberInfoResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static InternalPagedMemberResponse from(Page<InternalMemberInfoResponse> page) {
        return new InternalPagedMemberResponse(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}