package com.devticket.commerce.ticket.infrastructure.external.client.dto;

import java.util.List;
import java.util.UUID;

public record InternalBulkEventInfoRequest(
    List<UUID> eventIds
) {

}
