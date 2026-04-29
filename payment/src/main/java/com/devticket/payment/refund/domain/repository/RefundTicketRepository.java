package com.devticket.payment.refund.domain.repository;

import com.devticket.payment.refund.domain.model.RefundTicket;
import com.devticket.payment.refund.domain.enums.RefundTicketStatus;
import java.util.List;
import java.util.UUID;

public interface RefundTicketRepository {
    RefundTicket save(RefundTicket refundTicket);
    List<RefundTicket> saveAll(List<RefundTicket> refundTickets);
    List<RefundTicket> findByRefundId(UUID refundId);
    boolean existsByTicketIdAndStatusIn(UUID ticketId, List<RefundTicketStatus> statuses);
    void markFailedByRefundId(UUID refundId);
    void markCompletedByRefundId(UUID refundId);
}
