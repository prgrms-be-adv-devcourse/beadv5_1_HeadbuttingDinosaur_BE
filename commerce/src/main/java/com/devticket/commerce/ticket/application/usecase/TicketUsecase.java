package com.devticket.commerce.ticket.application.usecase;

import com.devticket.commerce.ticket.presentation.dto.req.TicketRequest;
import com.devticket.commerce.ticket.presentation.dto.res.TicketResponse;

public interface TicketUsecase {

    // --- internal request
    TicketResponse createTicket(TicketRequest request);
}
