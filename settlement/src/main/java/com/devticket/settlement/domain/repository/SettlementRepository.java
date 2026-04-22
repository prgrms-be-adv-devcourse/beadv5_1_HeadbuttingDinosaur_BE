package com.devticket.settlement.domain.repository;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SettlementRepository {

    List<Settlement> findBySellerId(UUID sellerId);

    Optional<Settlement> findBySettlementId(UUID settlementId);

    List<Settlement> findBySellerIdAndStatus(UUID sellerId, SettlementStatus status);

    List<Settlement> findByStatus(SettlementStatus status);

    Optional<Settlement> findBySellerIdAndPeriodStartAtBetween(UUID sellerId, LocalDateTime from, LocalDateTime to);

    boolean existsBySellerIdAndPeriodStartAtBetweenAndStatusNot(UUID sellerId, LocalDateTime from, LocalDateTime to, SettlementStatus status);

    List<Settlement> saveAll(List<? extends Settlement> settlements);

    Settlement save(Settlement settlement);

    Page<Settlement> search(SettlementStatus status, UUID sellerId,
        LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
}
