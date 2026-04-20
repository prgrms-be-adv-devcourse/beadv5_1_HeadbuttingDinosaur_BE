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
        if (totalAmount <= 0) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }
        if (totalTickets <= 0) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }
        OrderRefund ledger = new OrderRefund();
        ledger.orderRefundId = UUID.randomUUID();
        ledger.orderId = orderId;
        ledger.userId = userId;
        ledger.paymentId = paymentId;
        ledger.paymentMethod = paymentMethod;
        ledger.totalAmount = totalAmount;
        ledger.refundedAmount = 0;
        ledger.totalTickets = totalTickets;
        ledger.refundedTickets = 0;
        ledger.status = OrderRefundStatus.NONE;
        return ledger;
    }

    public void applyRefund(int amount, int ticketCount) {
        if (this.status == OrderRefundStatus.FULL) {
            throw new RefundException(RefundErrorCode.ALREADY_REFUNDED);
        }
        if (amount < 0 || ticketCount < 0) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }
        int newAmount = this.refundedAmount + amount;
        int newTickets = this.refundedTickets + ticketCount;
        if (newAmount > this.totalAmount || newTickets > this.totalTickets) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }
        this.refundedAmount = newAmount;
        this.refundedTickets = newTickets;
        if (this.refundedTickets >= this.totalTickets) {
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
