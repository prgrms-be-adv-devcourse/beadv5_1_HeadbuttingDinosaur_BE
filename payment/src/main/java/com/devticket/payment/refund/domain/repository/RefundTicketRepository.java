package com.devticket.payment.refund.domain.repository;

import com.devticket.payment.refund.domain.model.RefundTicket;
import java.util.List;
import java.util.UUID;

public interface RefundTicketRepository {
    RefundTicket save(RefundTicket refundTicket);
    List<RefundTicket> saveAll(List<RefundTicket> refundTickets);
    List<RefundTicket> findByRefundId(UUID refundId);
}
