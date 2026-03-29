package com.devticket.commerce.ticket.infrastructure.persistence;

import com.devticket.commerce.ticket.domain.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketJpaRepository extends JpaRepository<Ticket, Long> {

}
