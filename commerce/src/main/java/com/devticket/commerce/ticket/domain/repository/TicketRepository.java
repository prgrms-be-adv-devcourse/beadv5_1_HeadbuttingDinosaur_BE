package com.devticket.commerce.ticket.domain.repository;

import com.devticket.commerce.ticket.domain.model.Ticket;

public interface TicketRepository {

    Ticket save(Ticket ticket);
}
