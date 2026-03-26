package com.devticket.member.presentation.domain.model;

import com.devticket.member.presentation.domain.SellerApplicationStatus;
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
@Table(name = "seller_application", schema = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_application_id", unique = true, nullable = false, updatable = false)
    private UUID sellerApplicationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 100)
    private String accountNumber;

    @Column(name = "account_holder", nullable = false, length = 100)
    private String accountHolder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerApplicationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SellerApplication(Long userId, String bankName,
        String accountNumber, String accountHolder) {
        this.sellerApplicationId = UUID.randomUUID();
        this.userId = userId;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.status = SellerApplicationStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void approve() {
        this.status = SellerApplicationStatus.APPROVED;
        this.updatedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = SellerApplicationStatus.REJECTED;
        this.updatedAt = LocalDateTime.now();
    }
}
