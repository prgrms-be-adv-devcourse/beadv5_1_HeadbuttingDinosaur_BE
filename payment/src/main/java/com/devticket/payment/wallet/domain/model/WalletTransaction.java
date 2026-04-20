package com.devticket.payment.wallet.domain.model;

import com.devticket.payment.common.entity.BaseEntity;
import com.devticket.payment.wallet.domain.enums.WalletTransactionType;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "wallet_transaction", schema = "payment")
public class WalletTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_transaction_id", nullable = false, unique = true)
    private UUID walletTransactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "transaction_key", nullable = false, unique = true, length = 255)
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private WalletTransactionType type;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "related_order_id")
    private UUID relatedOrderId;

    @Column(name = "related_refund_id")
    private Long relatedRefundId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static WalletTransaction createCharge(
        Long walletId,
        UUID userId,
        String transactionKey,
        Integer amount,
        Integer balanceAfter
    ) {
        WalletTransaction walletTransaction = new WalletTransaction();
        walletTransaction.walletTransactionId = UUID.randomUUID();
        walletTransaction.walletId = walletId;
        walletTransaction.userId = userId;
        walletTransaction.transactionKey = transactionKey;
        walletTransaction.type = WalletTransactionType.CHARGE;
        walletTransaction.amount = amount;
        walletTransaction.balanceAfter = balanceAfter;
        return walletTransaction;
    }

    public static WalletTransaction createUse(
        Long walletId,
        UUID userId,
        String transactionKey,
        Integer amount,
        Integer balanceAfter,
        UUID relatedOrderId
    ) {
        WalletTransaction walletTransaction = new WalletTransaction();
        walletTransaction.walletTransactionId = UUID.randomUUID();
        walletTransaction.walletId = walletId;
        walletTransaction.userId = userId;
        walletTransaction.transactionKey = transactionKey;
        walletTransaction.type = WalletTransactionType.USE;
        walletTransaction.amount = amount;
        walletTransaction.balanceAfter = balanceAfter;
        walletTransaction.relatedOrderId = relatedOrderId;
        return walletTransaction;
    }

    public static WalletTransaction createRefund(
        Long walletId,
        UUID userId,
        String transactionKey,
        Integer amount,
        Integer balanceAfter,
        UUID relatedOrderId,
        Long relatedRefundId
    ) {
        WalletTransaction walletTransaction = new WalletTransaction();
        walletTransaction.walletTransactionId = UUID.randomUUID();
        walletTransaction.walletId = walletId;
        walletTransaction.userId = userId;
        walletTransaction.transactionKey = transactionKey;
        walletTransaction.type = WalletTransactionType.REFUND;
        walletTransaction.amount = amount;
        walletTransaction.balanceAfter = balanceAfter;
        walletTransaction.relatedOrderId = relatedOrderId;
        walletTransaction.relatedRefundId = relatedRefundId;
        return walletTransaction;
    }

    public static WalletTransaction createWithdraw(
        Long walletId,
        UUID userId,
        String transactionKey,
        Integer amount,
        Integer balanceAfter
    ) {
        WalletTransaction walletTransaction = new WalletTransaction();
        walletTransaction.walletTransactionId = UUID.randomUUID();
        walletTransaction.walletId = walletId;
        walletTransaction.userId = userId;
        walletTransaction.transactionKey = transactionKey;
        walletTransaction.type = WalletTransactionType.WITHDRAW;
        walletTransaction.amount = amount;
        walletTransaction.balanceAfter = balanceAfter;
        return walletTransaction;
    }

    public static WalletTransaction createSettlement(
        Long walletId,
        UUID userId,
        String transactionKey,
        Integer amount,
        Integer balanceAfter
    ) {
        WalletTransaction walletTransaction = new WalletTransaction();
        walletTransaction.walletTransactionId = UUID.randomUUID();
        walletTransaction.walletId = walletId;
        walletTransaction.userId = userId;
        walletTransaction.transactionKey = transactionKey;
        walletTransaction.type = WalletTransactionType.SETTLEMENT;
        walletTransaction.amount = amount;
        walletTransaction.balanceAfter = balanceAfter;
        return walletTransaction;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * transactionKey 를 무효화한다 — 재시도 흐름에서 같은 orderId 로 새 차감/환원이 가능하도록
     * 기존 키를 unique 충돌 없는 형태로 rename + softDelete.
     * Why: WALLET_PG 재시도(예: 2000→3000) 시 기존 USE_<orderId> 행이 멱등 키를 점유해
     *      두 번째 deduct 가 skip 되며 차감 누락이 발생. 환원 시 이 메서드로 USE 키를 해제한다.
     */
    public void revoke() {
        this.transactionKey = "REVOKED_" + this.transactionKey + "_" + UUID.randomUUID();
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isSameTransactionKey(String transactionKey) {
        return this.transactionKey.equals(transactionKey);
    }
}
