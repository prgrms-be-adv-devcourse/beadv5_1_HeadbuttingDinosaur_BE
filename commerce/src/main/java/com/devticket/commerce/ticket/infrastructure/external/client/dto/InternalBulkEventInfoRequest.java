package com.devticket.commerce.ticket.infrastructure.external.client.dto;

import java.util.List;

public record InternalBulkEventInfoRequest(
    List<Long> eventIds
) {

}
