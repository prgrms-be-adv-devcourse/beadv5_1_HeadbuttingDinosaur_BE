package com.devticket.payment.refund.infrastructure.persistence;

import com.devticket.payment.refund.domain.model.RefundTicket;
import com.devticket.payment.refund.domain.enums.RefundTicketStatus;
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
    public boolean existsByTicketIdAndStatusIn(UUID ticketId, List<RefundTicketStatus> statuses) {
        return jpa.existsByTicketIdAndStatusIn(ticketId, statuses);
    }

    @Override
    public void markFailedByRefundId(UUID refundId) {
        jpa.updateStatusByRefundId(refundId, RefundTicketStatus.ACTIVE, RefundTicketStatus.FAILED);
    }

    @Override
    public void markCompletedByRefundId(UUID refundId) {
        jpa.updateStatusByRefundId(refundId, RefundTicketStatus.ACTIVE, RefundTicketStatus.COMPLETED);
    }
}
