package com.devticket.payment.refund.infrastructure.persistence;

import com.devticket.payment.refund.domain.model.RefundTicket;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundTicketJpaRepository extends JpaRepository<RefundTicket, Long> {
    List<RefundTicket> findByRefundId(UUID refundId);
    boolean existsByTicketId(UUID ticketId);
}
