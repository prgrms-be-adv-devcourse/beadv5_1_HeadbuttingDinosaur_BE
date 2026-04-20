package com.devticket.commerce.ticket.infrastructure.persistence;

import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import com.devticket.commerce.ticket.presentation.dto.req.SellerEventParticipantListRequest;
import com.devticket.commerce.ticket.presentation.dto.req.TicketListRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class TicketRepositoryAdapter implements TicketRepository {

    public final TicketJpaRepository ticketJpaRepository;

    @Override
    public Ticket save(Ticket ticket) {
        return ticketJpaRepository.save(ticket);
    }

    @Override
    public Optional<Ticket> findById(Long id) {
        return ticketJpaRepository.findById(id);
    }

    @Override
    public Page<Ticket> findAllByUserId(UUID userId, TicketListRequest request) {
        return ticketJpaRepository.findAllByUserId(userId, request.toPageable());
    }

    @Override
    public List<Ticket> saveAll(List<Ticket> ticketList) {
        return ticketJpaRepository.saveAll(ticketList);
    }

    @Override
    public Optional<Ticket> findByTicketId(UUID ticketId) {
        return ticketJpaRepository.findByTicketId(ticketId);
    }

    @Override
    public Page<Ticket> findAllByEventIdAndStatus(UUID eventId, TicketStatus status, SellerEventParticipantListRequest request) {
        return ticketJpaRepository.findAllByEventIdAndStatus(eventId, status, request.toPageable());
    }

    @Override
    public List<Ticket> findAllByEventIdIn(List<UUID> eventIds) {
        return ticketJpaRepository.findAllByEventIdIn(eventIds);
    }

    @Override
    public List<Ticket> findAllByTicketIdIn(List<UUID> ticketIds) {
        return ticketJpaRepository.findAllByTicketIdIn(ticketIds);
    }

    @Override
    public List<Ticket> findAllByOrderId(Long orderId) {
        return ticketJpaRepository.findAllByOrderId(orderId);
    }

    @Override
    public List<Ticket> findAllByOrderIdAndStatus(Long orderId, TicketStatus status) {
        return ticketJpaRepository.findAllByOrderIdAndStatus(orderId, status);
    }

    @Override
    public int countByUserIdAndEventIdAndStatus(UUID userId, UUID eventId, TicketStatus status) {
        return ticketJpaRepository.countByUserIdAndEventIdAndStatus(userId, eventId, status);
    }
}
