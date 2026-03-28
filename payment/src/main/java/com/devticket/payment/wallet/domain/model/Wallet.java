package com.devticket.payment.wallet.domain.model;

import com.devticket.payment.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "wallet")
public class Wallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false, unique = true)
    private UUID walletId;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private Integer balance;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /* =======================
        정적 팩토리 메서드
       ======================= */

    public static Wallet create(UUID userId) {
        Wallet wallet = new Wallet();
        wallet.walletId = UUID.randomUUID();
        wallet.userId = userId;
        wallet.balance = 0;
        return wallet;
    }

    /* =======================
        비즈니스 로직
       ======================= */

    public void charge(int amount) {
        this.balance += amount;
    }

    public void use(int amount) {
        if (this.balance < amount) {
            throw new IllegalArgumentException("잔액 부족");
        }
        this.balance -= amount;
    }

    public void refund(int amount) {
        this.balance += amount;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
