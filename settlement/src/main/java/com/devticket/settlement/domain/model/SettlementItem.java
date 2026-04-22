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
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "settlement_item")
public class SettlementItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "settlement_id")
    private UUID settlementId;

    // Commerce 서비스의 orderItemId(UUID) - 멱등성 관리용 유니크 키
    @Column(name = "order_item_id", unique = true, nullable = false)
    private UUID orderItemId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "event_uuid", nullable = false)
    private UUID eventUUID;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementItemStatus status;

    @Column(name = "sales_amount", nullable = false)
    private Long salesAmount;

    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount;

    @Column(name = "fee_amount", nullable = false)
    private Long feeAmount;

    @Column(name = "settlement_amount", nullable = false)
    private Long settlementAmount;

    @Column(name = "event_date_time", nullable = false)
    private LocalDate eventDateTime;


    @Builder
    public SettlementItem(UUID settlementId, UUID orderItemId, Long eventId, UUID eventUUID,
        UUID sellerId, SettlementItemStatus status,
        Long salesAmount, Long refundAmount, Long feeAmount, Long settlementAmount,
        LocalDate eventDateTime) {
        this.settlementId = settlementId;
        this.orderItemId = orderItemId;
        this.eventId = eventId;
        this.eventUUID = eventUUID;
        this.sellerId = sellerId;
        this.status = (status != null) ? status : SettlementItemStatus.READY;
        this.salesAmount = salesAmount;
        this.refundAmount = refundAmount;
        this.feeAmount = feeAmount;
        this.settlementAmount = settlementAmount;
        this.eventDateTime = eventDateTime;
    }

    public void finalize(UUID settlementId) {
        this.settlementId = settlementId;
        this.status = SettlementItemStatus.FINALIZED;
    }

}
