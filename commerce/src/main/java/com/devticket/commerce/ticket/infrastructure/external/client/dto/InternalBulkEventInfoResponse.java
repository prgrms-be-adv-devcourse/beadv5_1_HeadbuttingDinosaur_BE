package com.devticket.commerce.ticket.infrastructure.external.client.dto;

import java.util.List;

public record InternalBulkEventInfoResponse(
    List<InternalEventInfoResponse> events
) {

}

