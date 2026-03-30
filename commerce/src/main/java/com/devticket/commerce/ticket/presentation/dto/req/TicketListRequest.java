package com.devticket.commerce.ticket.presentation.dto.req;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record TicketListRequest(
    int page,
    int size
) {

    public Pageable toPageable() {
        int targetPage = (page < 1) ? 0 : page - 1;
        int targetSize = (size < 1) ? 10 : size;

        return PageRequest.of(targetPage, targetSize, Sort.by("issuedAt").descending());
    }


}
