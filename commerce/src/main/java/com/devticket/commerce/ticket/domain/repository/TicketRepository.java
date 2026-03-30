package com.devticket.commerce.ticket.domain.repository;

import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.presentation.dto.req.SellerEventParticipantListRequest;
import com.devticket.commerce.ticket.presentation.dto.req.TicketListRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface TicketRepository {

    Ticket save(Ticket ticket);

    Page<Ticket> findAllByUserId(UUID userId, TicketListRequest request);

    List<Ticket> saveAll(List<Ticket> ticketList);

    Page<Ticket> findAllByEventId(Long eventId, SellerEventParticipantListRequest request);
}
