package com.devticket.settlement.domain.model;

import com.devticket.settlement.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "settlement")
public class Settlement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false, unique = true)
    private UUID settlementId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

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

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;


    //    빌더
    @Builder
    public Settlement(Long id, UUID settlementId, Long sellerId, LocalDateTime periodStartAt, LocalDateTime periodEndAt,
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


}

