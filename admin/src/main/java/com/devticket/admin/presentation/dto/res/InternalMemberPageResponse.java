package com.devticket.admin.presentation.dto.res;

import com.devticket.admin.infrastructure.external.dto.res.InternalMemberInfoResponse;
import java.util.List;

public record InternalMemberPageResponse(
    List<InternalMemberInfoResponse> content,
    int page,
    int size,
    long totalElements
) {}
