package com.devticket.settlement.infrastructure.persistence.repository;

import com.devticket.settlement.domain.model.Settlement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementJpaRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findBySellerId(UUID sellerId);

    Optional<Settlement> findBySettlementId(UUID settlementId);

}
