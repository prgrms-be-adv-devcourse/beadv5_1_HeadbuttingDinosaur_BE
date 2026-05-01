package com.devticket.admin.presentation.dto.res;

import com.devticket.admin.infrastructure.external.dto.res.InternalMemberInfoResponse;
import java.util.List;
import org.springframework.data.domain.Page;

public record InternalMemberPageResponse(
    List<InternalMemberInfoResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static InternalMemberPageResponse from(Page<InternalMemberInfoResponse> page) {
        return new InternalMemberPageResponse(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
