package com.devticket.payment.refund.infrastructure.persistence;

import com.devticket.payment.refund.domain.model.RefundTicket;
import com.devticket.payment.refund.domain.repository.RefundTicketRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefundTicketRepositoryImpl implements RefundTicketRepository {

    private final RefundTicketJpaRepository jpa;

    @Override
    public RefundTicket save(RefundTicket refundTicket) {
        return jpa.save(refundTicket);
    }

    @Override
    public List<RefundTicket> saveAll(List<RefundTicket> refundTickets) {
        return jpa.saveAll(refundTickets);
    }

    @Override
    public List<RefundTicket> findByRefundId(UUID refundId) {
        return jpa.findByRefundId(refundId);
    }

    @Override
    public boolean existsByTicketId(UUID ticketId) {
        return jpa.existsByTicketId(ticketId);
    }
}
