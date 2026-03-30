package com.devticket.commerce.ticket.infrastructure.persistence;

import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import com.devticket.commerce.ticket.presentation.dto.req.TicketListRequest;
import java.util.List;
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
    public Page<Ticket> findAllByUserId(UUID userId, TicketListRequest request) {
        return ticketJpaRepository.findAllByUserId(userId, request.toPageable());
    }

    @Override
    public List<Ticket> saveAll(List<Ticket> ticketList) {
        return ticketJpaRepository.saveAll(ticketList);
    }
}
