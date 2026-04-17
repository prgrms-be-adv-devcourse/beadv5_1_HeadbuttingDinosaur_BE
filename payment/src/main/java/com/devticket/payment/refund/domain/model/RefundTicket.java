package com.devticket.payment.refund.domain.model;

import com.devticket.payment.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Refund 1건이 포함하는 개별 ticketId 를 저장하는 조인 엔티티.
 * 오더 전체 환불 시 refundId 하나에 N개의 티켓을 묶는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "refund_ticket",
    schema = "payment",
    indexes = {
        @Index(name = "idx_refund_ticket_refund_id", columnList = "refund_id"),
        @Index(name = "idx_refund_ticket_ticket_id", columnList = "ticket_id")
    }
)
public class RefundTicket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", nullable = false)
    private UUID refundId;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    public static RefundTicket of(UUID refundId, UUID ticketId) {
        RefundTicket rt = new RefundTicket();
        rt.refundId = refundId;
        rt.ticketId = ticketId;
        return rt;
    }
}
