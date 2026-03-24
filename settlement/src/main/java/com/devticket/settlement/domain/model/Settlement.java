package com.devticket.settlement.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

public class Settlement {

    private UUID id;
    private UUID sellerId;
    private LocalDateTime periodStartAt;
    private LocalDateTime periodEndAt;
    private Integer totalSalesAmount;
    private Integer totalRefundAmount;
    private Integer totalFeeAmount;
    private Integer finalSettlementAmount;
    private SettlementStatus status;
    private LocalDateTime settledAt;
    
    
    // 기본 생성자
    public Settlement() {

    }

    // Builder
    @Builder
    private Settlement(
        UUID id,
        UUID sellerId,
        LocalDateTime periodStartAt,
        LocalDateTime periodEndAt,
        Integer totalSalesAmount,
        Integer totalRefundAmount,
        Integer totalFeeAmount,
        Integer finalSettlementAmount,
        SettlementStatus status,
        LocalDateTime settledAt
    ) {
        this.id = id;
        this.sellerId = sellerId;
        this.periodStartAt = periodStartAt;
        this.periodEndAt = periodEndAt;
        this.totalSalesAmount = totalSalesAmount;
        this.totalRefundAmount = totalRefundAmount;
        this.totalFeeAmount = totalFeeAmount;
        this.finalSettlementAmount = finalSettlementAmount;
        this.status = status;
        this.settledAt = settledAt;
    }

    // ==============================
    // 생성 (Factory Method)
    // ==============================
    // 1. 생성
    public static Settlement create(
        UUID sellerId,
        LocalDateTime periodStartAt,
        LocalDateTime periodEndAt,
        Integer totalSalesAmount,
        Integer totalRefundAmount,
        Integer totalFeeAmount
    ){
        return Settlement.builder()
            .id(UUID.randomUUID())
            .sellerId(sellerId)
            .periodStartAt(periodStartAt)
            .periodEndAt(periodEndAt)
            .totalSalesAmount(totalSalesAmount)
            .totalRefundAmount(totalRefundAmount)
            .totalFeeAmount(totalFeeAmount)
            .finalSettlementAmount(
                totalSalesAmount - totalRefundAmount - totalFeeAmount
            )
            .status(SettlementStatus.PENDING)
            .settledAt(null)
            .build();
    }

    // 2.

    // ==============================
    // 상태 변경 (비즈니스 로직)
    // ==============================
    // 1. 정상 완료
    public void complete(LocalDateTime settledAt){
        if(!isPending()){
            throw new IllegalStateException("이미 처리된 정산입니다.");
        }
        this.status = SettlementStatus.COMPLETED;
        this.settledAt = settledAt;
    }

    public void fail(){
        if(!isPending()){
            throw new IllegalStateException("이미 처리된 정산입니다.");
        }
        this.status = SettlementStatus.FAILED;
    }

    // ==============================
    // 비즈니스 조회 로직
    // ==============================
    public boolean isPending(){
        return this.status == SettlementStatus.PENDING;
    }

    // Getter
    public UUID getId() {
        return id;
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
}

