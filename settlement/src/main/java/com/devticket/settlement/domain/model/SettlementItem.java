package com.devticket.settlement.domain.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Table(name = "settlement_item")
public class SettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "sales_amount", nullable = false)
    private Integer salesAmount;

    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount;

    @Column(name = "fee_amount", nullable = false)
    private Integer feeAmount;

    @Column(name = "settlement_amount", nullable = false)
    private Integer settlementAmount;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


}
