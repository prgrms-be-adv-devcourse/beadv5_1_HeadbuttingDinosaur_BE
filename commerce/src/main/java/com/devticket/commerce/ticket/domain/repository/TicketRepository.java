package com.devticket.commerce.ticket.domain.repository;

import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.presentation.dto.req.SellerEventParticipantListRequest;
import com.devticket.commerce.ticket.presentation.dto.req.TicketListRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface TicketRepository {

    Ticket save(Ticket ticket);

    Optional<Ticket> findById(Long id);

    Page<Ticket> findAllByUserId(UUID userId, TicketListRequest request);

    List<Ticket> saveAll(List<Ticket> ticketList);

    Optional<Ticket> findByTicketId(UUID ticketId);

    Page<Ticket> findAllByEventId(UUID eventId, SellerEventParticipantListRequest request);

    List<Ticket> findAllByEventIdIn(List<UUID> eventIds);

    List<Ticket> findAllByTicketIdIn(List<UUID> ticketIds);

    List<Ticket> findAllByOrderId(Long orderId);

    List<Ticket> findAllByOrderIdAndStatus(Long orderId, TicketStatus status);
}
