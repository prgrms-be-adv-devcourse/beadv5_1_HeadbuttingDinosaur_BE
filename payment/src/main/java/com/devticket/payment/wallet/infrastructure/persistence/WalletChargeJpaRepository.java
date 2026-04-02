package com.devticket.payment.wallet.infrastructure.persistence;

import com.devticket.payment.wallet.domain.model.WalletCharge;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletChargeJpaRepository extends JpaRepository<WalletCharge, Long> {
    
    Optional<WalletCharge> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<WalletCharge> findByChargeId(UUID chargeId);

    @Query("SELECT COALESCE(SUM(wc.amount), 0) FROM WalletCharge wc "
        + "WHERE wc.userId = :userId "
        + "AND wc.status IN (com.devticket.payment.wallet.domain.enums.WalletChargeStatus.PENDING, "
        + "com.devticket.payment.wallet.domain.enums.WalletChargeStatus.COMPLETED) "
        + "AND wc.createdAt >= :startOfDay")
    int sumTodayChargeAmount(@Param("userId") UUID userId,
        @Param("startOfDay") LocalDateTime startOfDay);
}