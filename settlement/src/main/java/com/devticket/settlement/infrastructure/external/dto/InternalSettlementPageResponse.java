package com.devticket.settlement.infrastructure.external.dto;

import java.util.List;

public record InternalSettlementPageResponse(
    List<InternalSettlementResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}