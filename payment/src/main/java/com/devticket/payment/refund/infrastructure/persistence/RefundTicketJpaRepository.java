package com.devticket.payment.refund.infrastructure.persistence;

import com.devticket.payment.refund.domain.model.RefundTicket;
import com.devticket.payment.refund.domain.enums.RefundTicketStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundTicketJpaRepository extends JpaRepository<RefundTicket, Long> {
    List<RefundTicket> findByRefundId(UUID refundId);
    boolean existsByTicketIdAndStatusIn(UUID ticketId, List<RefundTicketStatus> statuses);

    @Modifying
    @Query("UPDATE RefundTicket rt SET rt.status = :toStatus WHERE rt.refundId = :refundId AND rt.status = :fromStatus")
    void updateStatusByRefundId(@Param("refundId") UUID refundId,
                                @Param("fromStatus") RefundTicketStatus fromStatus,
                                @Param("toStatus") RefundTicketStatus toStatus);
}
