package com.devticket.payment.wallet.domain.model;

import com.devticket.payment.common.entity.BaseEntity;
import com.devticket.payment.wallet.domain.enums.WalletChargeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "wallet_charge", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "idempotency_key"})
}, schema = "payment")
public class WalletCharge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charge_id", nullable = false, unique = true)
    private UUID chargeId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletChargeStatus status;

    @Column(name = "payment_key", unique = true)
    private String paymentKey;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    public static WalletCharge create(Long walletId, UUID userId,
        Integer amount, String idempotencyKey) {
        WalletCharge charge = new WalletCharge();
        charge.chargeId = UUID.randomUUID();
        charge.walletId = walletId;
        charge.userId = userId;
        charge.amount = amount;
        charge.status = WalletChargeStatus.PENDING;
        charge.idempotencyKey = idempotencyKey;
        return charge;
    }

    public void markProcessing() {
        if (this.status != WalletChargeStatus.PENDING) {
            throw new IllegalStateException(
                "PROCESSING 전이는 PENDING 상태에서만 가능합니다. 현재: " + this.status);
        }
        this.status = WalletChargeStatus.PROCESSING;
    }

    public void complete(String paymentKey) {
        this.status = WalletChargeStatus.COMPLETED;
        this.paymentKey = paymentKey;
    }

    public void fail() {
        this.status = WalletChargeStatus.FAILED;
    }

    public boolean isPending() {
        return this.status == WalletChargeStatus.PENDING;
    }

    public boolean isProcessing() {
        return this.status == WalletChargeStatus.PROCESSING;
    }

    public void revertToPending() {
        if (this.status != WalletChargeStatus.PROCESSING) {
            throw new IllegalStateException(
                "PENDING 원복은 PROCESSING 상태에서만 가능합니다. 현재: " + this.status);
        }
        this.status = WalletChargeStatus.PENDING;
    }
}
