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
@Table(name = "wallet_transaction")
public class WalletTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_transaction_id", nullable = false, unique = true)
    private UUID walletTransactionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

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
    private Long relatedOrderId;

    @Column(name = "related_refund_id")
    private Long relatedRefundId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static WalletTransaction createCharge(
        Long walletId,
        Long userId,
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
        Long userId,
        String transactionKey,
        Integer amount,
        Integer balanceAfter,
        Long relatedOrderId
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
        Long userId,
        String transactionKey,
        Integer amount,
        Integer balanceAfter,
        Long relatedOrderId,
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
        Long userId,
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

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isSameTransactionKey(String transactionKey) {
        return this.transactionKey.equals(transactionKey);
    }
}
