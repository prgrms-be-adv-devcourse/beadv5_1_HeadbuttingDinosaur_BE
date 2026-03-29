package com.devticket.commerce.ticket.application.usecase;

import com.devticket.commerce.ticket.presentation.dto.req.TicketRequest;
import com.devticket.commerce.ticket.presentation.dto.res.TicketResponse;
import java.util.UUID;

public interface TicketUsecase {

    TicketResponse createTicket(UUID userId, TicketRequest request);
}
