package com.devticket.settlement.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fee_policy", schema = "settlement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fee_policy_id", unique = true, nullable = false, updatable = false)
    private UUID feePolicyId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false)
    private FeeType feeType;

    @Column(name = "fee_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal feeValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static FeePolicy create(String name, FeeType feeType, BigDecimal feeValue) {
        FeePolicy fp = new FeePolicy();
        fp.feePolicyId = UUID.randomUUID();
        fp.name = name;
        fp.feeType = feeType;
        fp.feeValue = feeValue;
        fp.createdAt = LocalDateTime.now();
        return fp;
    }

    public long calculateFee(long salesAmount) {
        if (feeType == FeeType.PERCENTAGE) {
            return feeValue.multiply(BigDecimal.valueOf(salesAmount))
                .divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP)
                .longValue();
        }
        return 0;
    }
}
