package com.devticket.settlement.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class Settlement {

    @Id
    private UUID id;

    private UUID sellerId;

    private LocalDateTime period_start_at;

    private LocalDateTime period_end_at;

    private Integer total_sales_amount;

    private Integer total_refund_amount;

    private Integer totalFeeAmount; 
    
    private Integer finalSettlementAmount; 
    
    private SettlementStatus status; 
    
    private LocalDateTime settledAt;
    
    
    // 생성자

    public Settlement() {

    }

    public Settlement(UUID id, UUID sellerId, LocalDateTime period_start_at, LocalDateTime period_end_at,
        Integer total_sales_amount, Integer total_refund_amount, Integer totalFeeAmount, Integer finalSettlementAmount,
        SettlementStatus status, LocalDateTime settledAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.period_start_at = period_start_at;
        this.period_end_at = period_end_at;
        this.total_sales_amount = total_sales_amount;
        this.total_refund_amount = total_refund_amount;
        this.totalFeeAmount = totalFeeAmount;
        this.finalSettlementAmount = finalSettlementAmount;
        this.status = status;
        this.settledAt = settledAt;
    }


    // Getter/Setter
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public void setSellerId(UUID sellerId) {
        this.sellerId = sellerId;
    }

    public LocalDateTime getPeriod_start_at() {
        return period_start_at;
    }

    public void setPeriod_start_at(LocalDateTime period_start_at) {
        this.period_start_at = period_start_at;
    }

    public LocalDateTime getPeriod_end_at() {
        return period_end_at;
    }

    public void setPeriod_end_at(LocalDateTime period_end_at) {
        this.period_end_at = period_end_at;
    }

    public Integer getTotal_sales_amount() {
        return total_sales_amount;
    }

    public void setTotal_sales_amount(Integer total_sales_amount) {
        this.total_sales_amount = total_sales_amount;
    }

    public Integer getTotal_refund_amount() {
        return total_refund_amount;
    }

    public void setTotal_refund_amount(Integer total_refund_amount) {
        this.total_refund_amount = total_refund_amount;
    }

    public Integer getTotalFeeAmount() {
        return totalFeeAmount;
    }

    public void setTotalFeeAmount(Integer totalFeeAmount) {
        this.totalFeeAmount = totalFeeAmount;
    }

    public Integer getFinalSettlementAmount() {
        return finalSettlementAmount;
    }

    public void setFinalSettlementAmount(Integer finalSettlementAmount) {
        this.finalSettlementAmount = finalSettlementAmount;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public void setStatus(SettlementStatus status) {
        this.status = status;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(LocalDateTime settledAt) {
        this.settledAt = settledAt;
    }

}

