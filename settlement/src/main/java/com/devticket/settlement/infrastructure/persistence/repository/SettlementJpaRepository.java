package com.devticket.settlement.infrastructure.persistence.repository;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementJpaRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findBySellerId(UUID sellerId);

    Optional<Settlement> findBySettlementId(UUID settlementId);

    List<Settlement> findBySellerIdAndStatus(UUID sellerId, SettlementStatus status);

    List<Settlement> findByStatus(SettlementStatus status);

    Optional<Settlement> findFirstBySellerIdAndPeriodStartAtBetweenAndStatusNotOrderByCreatedAtDesc(
        UUID sellerId, LocalDateTime from, LocalDateTime to, SettlementStatus status);

    boolean existsBySellerIdAndPeriodStartAtBetweenAndStatusNot(UUID sellerId, LocalDateTime from, LocalDateTime to, SettlementStatus status);

    @Query("""
    SELECT s FROM Settlement s
    WHERE (CAST(:status AS string) IS NULL OR s.status = :status)
      AND (CAST(:sellerId AS string) IS NULL OR s.sellerId = :sellerId)
      AND (CAST(:monthStart AS string) IS NULL OR s.settledAt >= :monthStart)
      AND (CAST(:monthEnd AS string) IS NULL OR s.settledAt < :monthEnd)
    ORDER BY s.createdAt DESC
    """)
    Page<Settlement> search(
        @Param("status") SettlementStatus status,
        @Param("sellerId") UUID sellerId,
        @Param("monthStart") LocalDateTime monthStart,
        @Param("monthEnd") LocalDateTime monthEnd,
        Pageable pageable
    );

    List<Settlement> findByCarriedToSettlementId(UUID carriedToSettlementId);

    List<Settlement> findBySellerIdAndStatusAndCarriedToSettlementIdIsNull(UUID sellerId, SettlementStatus status);

}
