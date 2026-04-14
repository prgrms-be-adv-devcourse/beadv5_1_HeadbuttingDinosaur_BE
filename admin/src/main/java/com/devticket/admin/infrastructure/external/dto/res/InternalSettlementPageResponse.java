package com.devticket.admin.infrastructure.external.dto.res;

import java.util.List;

public record InternalSettlementPageResponse(
    List<InternalSettlementResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPage
) {
}
