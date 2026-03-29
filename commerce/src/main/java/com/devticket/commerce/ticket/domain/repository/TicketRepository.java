package com.devticket.commerce.ticket.domain.repository;

import com.devticket.commerce.ticket.domain.model.Ticket;
import java.util.List;

public interface TicketRepository {

    Ticket save(Ticket ticket);

    List<Ticket> saveAll(List<Ticket> ticketList);
}
