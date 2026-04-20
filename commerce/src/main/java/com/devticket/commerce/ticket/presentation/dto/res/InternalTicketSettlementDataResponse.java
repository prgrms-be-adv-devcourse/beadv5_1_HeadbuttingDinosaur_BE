package com.devticket.commerce.ticket.presentation.dto.res;

import java.util.List;

public record InternalTicketSettlementDataResponse(
    List<InternalTicketSettlementItemResponse> items
) {

}