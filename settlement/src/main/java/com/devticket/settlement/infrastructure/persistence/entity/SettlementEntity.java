package com.devticket.settlement.infrastructure.persistence.entity;

import com.devticket.settlement.domain.model.Settlement;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.devticket.settlement.domain.model.SettlementStatus;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Access(AccessType.FIELD)
@Entity
@Table(name = "settlement")
public class SettlementEntity {

    @Id
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "period_start_at", nullable = false)
    private LocalDateTime periodStartAt;

    @Column(name = "period_end_at", nullable = false)
    private LocalDateTime periodEndAt;

    @Column(name = "total_sales_amount", nullable = false)
    private Integer totalSalesAmount;

    @Column(name = "total_refund_amount", nullable = false)
    private Integer totalRefundAmount;

    @Column(name = "total_fee_amount", nullable = false)
    private Integer totalFeeAmount;

    @Column(name = "final_settlement_amount", nullable = false)
    private Integer finalSettlementAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementStatus status;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    // Settlement(도메인) -> SettlementEntity(엔티티)
    public static SettlementEntity from(Settlement settlement){
        SettlementEntity entity = new SettlementEntity();
        entity.id = settlement.getId();
        entity.sellerId = settlement.getSellerId();
        entity.periodStartAt = settlement.getPeriodStartAt();
        entity.periodEndAt = settlement.getPeriodEndAt();
        entity.totalSalesAmount = settlement.getTotalSalesAmount();
        entity.totalRefundAmount = settlement.getTotalRefundAmount();
        entity.totalFeeAmount = settlement.getTotalFeeAmount();
        entity.finalSettlementAmount = settlement.getFinalSettlementAmount();
        entity.status = settlement.getStatus();
        entity.settledAt = settlement.getSettledAt();
        return entity;
    }

    // SettlementEntity(엔티티) -> Settlement(도메인)
    public Settlement toDomain(){
        return  Settlement.builder()
            .id(this.id)
            .sellerId(this.sellerId)
            .periodStartAt(this.periodStartAt)
            .periodEndAt(this.periodEndAt)
            .totalSalesAmount(this.totalSalesAmount)
            .totalRefundAmount(this.totalRefundAmount)
            .totalFeeAmount(this.totalFeeAmount)
            .finalSettlementAmount(this.finalSettlementAmount)
            .status(this.status)
            .settledAt(this.settledAt)
            .build();
    }

}
