package com.devticket.commerce.ticket.presentation.dto.res;

import java.util.List;
import org.springframework.data.domain.Page;

public record TicketListResponse(
    int totalPages,
    Long totalElements,
    List<TicketDetailResponse> tickets
) {

    public static TicketListResponse of(
        Page<?> page,
        List<TicketDetailResponse> tickets
    ) {
        return new TicketListResponse(
            page.getTotalPages(),
            page.getTotalElements(),
            tickets
        );
    }
}
