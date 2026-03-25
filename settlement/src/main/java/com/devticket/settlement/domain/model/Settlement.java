package com.devticket.settlement.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "settlement")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false, unique = true)
    private UUID settlementId;

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

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;



//    빌더
    @Builder
    public Settlement(Long id, UUID settlementId, UUID sellerId, LocalDateTime periodStartAt, LocalDateTime periodEndAt,
        Integer totalSalesAmount, Integer totalRefundAmount, Integer totalFeeAmount, Integer finalSettlementAmount,
        SettlementStatus status) {
        this.id = id;
        this.settlementId = (settlementId != null) ? settlementId : UUID.randomUUID();
        this.sellerId = sellerId;
        this.periodStartAt = periodStartAt;
        this.periodEndAt = periodEndAt;
        this.totalSalesAmount = totalSalesAmount;
        this.totalRefundAmount = totalRefundAmount;
        this.totalFeeAmount = totalFeeAmount;
        this.finalSettlementAmount = finalSettlementAmount;
        this.status = (status != null) ? status : SettlementStatus.PENDING;
    }

//    getter
    public Long getId() {
        return id;
    }

    public UUID getSettlementId() {
        return settlementId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public LocalDateTime getPeriodStartAt() {
        return periodStartAt;
    }

    public LocalDateTime getPeriodEndAt() {
        return periodEndAt;
    }

    public Integer getTotalSalesAmount() {
        return totalSalesAmount;
    }

    public Integer getTotalRefundAmount() {
        return totalRefundAmount;
    }

    public Integer getTotalFeeAmount() {
        return totalFeeAmount;
    }

    public Integer getFinalSettlementAmount() {
        return finalSettlementAmount;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

}

