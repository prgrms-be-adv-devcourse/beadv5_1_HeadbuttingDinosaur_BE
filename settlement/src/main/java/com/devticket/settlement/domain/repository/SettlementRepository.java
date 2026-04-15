package com.devticket.settlement.domain.repository;

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

public interface SettlementRepository {

    List<Settlement> findBySellerId(UUID sellerId);

    Optional<Settlement> findBySettlementId(UUID settlementId);

    List<Settlement> saveAll(List<? extends Settlement> settlements);

    Settlement save(Settlement settlement);

    Page<Settlement> search(SettlementStatus status, UUID sellerId,
        LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
}
