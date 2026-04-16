package com.devticket.payment.wallet.infrastructure.persistence;

import com.devticket.payment.wallet.domain.model.WalletCharge;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletChargeJpaRepository extends JpaRepository<WalletCharge, Long> {
    
    Optional<WalletCharge> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<WalletCharge> findByChargeId(UUID chargeId);

    @Query("SELECT COALESCE(SUM(wc.amount), 0) FROM WalletCharge wc "
        + "WHERE wc.userId = :userId "
        + "AND wc.status IN (com.devticket.payment.wallet.domain.enums.WalletChargeStatus.PENDING, "
        + "com.devticket.payment.wallet.domain.enums.WalletChargeStatus.PROCESSING, "
        + "com.devticket.payment.wallet.domain.enums.WalletChargeStatus.COMPLETED) "
        + "AND wc.createdAt >= :startOfDay")
    int sumTodayChargeAmount(@Param("userId") UUID userId,
        @Param("startOfDay") LocalDateTime startOfDay);

    // 사후 보정용: 일정 시간 이상 PENDING 상태인 chargeId 목록 조회 (락 없이 ID만)
    @Query("SELECT wc.chargeId FROM WalletCharge wc "
        + "WHERE wc.status = com.devticket.payment.wallet.domain.enums.WalletChargeStatus.PENDING "
        + "AND wc.createdAt < :before "
        + "AND wc.createdAt >= :after "
        + "ORDER BY wc.createdAt ASC")
    List<UUID> findStalePendingChargeIds(
        @Param("before") LocalDateTime before,
        @Param("after") LocalDateTime after,
        Pageable pageable);

    // 사후 보정용: 개별 건 비관적 락 획득 (다른 인스턴스와의 중복 처리 방지)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wc FROM WalletCharge wc WHERE wc.chargeId = :chargeId")
    Optional<WalletCharge> findByChargeIdForUpdate(@Param("chargeId") UUID chargeId);
}