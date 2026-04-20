package com.devticket.admin.infrastructure.external.dto.res;

import java.util.List;

public record InternalAdminEventPageResponse(
    List<InternalAdminEventResponse> content,
    Integer page,
    Integer size,
    Long totalElements,
    Integer totalPages
) {}

