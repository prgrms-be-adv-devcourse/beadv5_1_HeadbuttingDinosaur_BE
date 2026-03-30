package com.devticket.commerce.ticket.infrastructure.persistence;

import com.devticket.commerce.ticket.domain.model.Ticket;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketJpaRepository extends JpaRepository<Ticket, Long> {

    Page<Ticket> findAllByUserId(UUID userId, Pageable pageable);
}
