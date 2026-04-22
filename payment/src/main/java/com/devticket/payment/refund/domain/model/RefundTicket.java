package com.devticket.payment.refund.domain.model;

import com.devticket.payment.refund.domain.enums.RefundTicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refund_ticket", schema = "payment",
    indexes = {
        @Index(name = "idx_refund_ticket_refund_id", columnList = "refund_id"),
        @Index(name = "idx_refund_ticket_ticket_id", columnList = "ticket_id")
    }
    // uk_refund_ticket_active: partial unique index (ticket_id) WHERE status IN ('ACTIVE','COMPLETED')
    // JPA @UniqueConstraint는 partial index를 표현할 수 없어 DDL 스크립트(RefundTicketPartialIndexInitializer)로 관리
)
public class RefundTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", nullable = false)
    private UUID refundId;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RefundTicketStatus status;

    public static RefundTicket of(UUID refundId, UUID ticketId) {
        RefundTicket rt = new RefundTicket();
        rt.refundId = refundId;
        rt.ticketId = ticketId;
        rt.status = RefundTicketStatus.ACTIVE;
        return rt;
    }

    public void markFailed() {
        this.status = RefundTicketStatus.FAILED;
    }

    public void markCompleted() {
        this.status = RefundTicketStatus.COMPLETED;
    }
}
