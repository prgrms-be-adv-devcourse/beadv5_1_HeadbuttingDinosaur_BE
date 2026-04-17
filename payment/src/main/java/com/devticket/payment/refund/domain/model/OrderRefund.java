package com.devticket.payment.refund.domain.model;

import com.devticket.payment.common.entity.BaseEntity;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.refund.domain.enums.OrderRefundStatus;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 오더 단위 환불 원장. 한 Order 의 누적 환불 상태를 1:1 로 집계한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_refund", schema = "payment")
public class OrderRefund extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "order_refund_id", nullable = false, unique = true)
    private UUID orderRefundId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;

    @Column(name = "refunded_amount", nullable = false)
    private Integer refundedAmount;

    @Column(name = "total_tickets", nullable = false)
    private Integer totalTickets;

    @Column(name = "refunded_tickets", nullable = false)
    private Integer refundedTickets;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderRefundStatus status;

    public static OrderRefund create(
        UUID orderId,
        UUID userId,
        UUID paymentId,
        PaymentMethod paymentMethod,
        int totalAmount,
        int totalTickets
    ) {
        OrderRefund ledger = new OrderRefund();
        ledger.orderRefundId = UUID.randomUUID();
        ledger.orderId = orderId;
        ledger.userId = userId;
        ledger.paymentId = paymentId;
        ledger.paymentMethod = paymentMethod;
        ledger.totalAmount = totalAmount;
        ledger.totalTickets = totalTickets;
        ledger.refundedAmount = 0;
        ledger.refundedTickets = 0;
        ledger.status = OrderRefundStatus.NONE;
        return ledger;
    }

    /**
     * 실제 환불이 완료된 금액·티켓 수를 누적한다.
     * 상태는 누적 결과에 따라 NONE → PARTIAL → FULL 로 전이한다.
     */
    public void applyRefund(int amount, int ticketCount) {
        if (status == OrderRefundStatus.FULL) {
            throw new RefundException(RefundErrorCode.ALREADY_REFUNDED);
        }
        if (amount < 0 || ticketCount < 0) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }

        this.refundedAmount += amount;
        this.refundedTickets += ticketCount;

        if (this.refundedAmount > this.totalAmount
            || this.refundedTickets > this.totalTickets) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }

        if (this.refundedTickets.equals(this.totalTickets)) {
            this.status = OrderRefundStatus.FULL;
        } else if (this.refundedTickets > 0) {
            this.status = OrderRefundStatus.PARTIAL;
        }
    }

    public void markFailed() {
        this.status = OrderRefundStatus.FAILED;
    }

    public boolean isFullyRefunded() {
        return this.status == OrderRefundStatus.FULL;
    }

    public int getRemainingAmount() {
        return this.totalAmount - this.refundedAmount;
    }

    public int getRemainingTickets() {
        return this.totalTickets - this.refundedTickets;
    }
}
