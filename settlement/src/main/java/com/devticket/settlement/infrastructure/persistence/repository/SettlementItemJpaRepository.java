package com.devticket.settlement.infrastructure.persistence.repository;

import com.devticket.settlement.domain.model.SettlementItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemJpaRepository extends JpaRepository<SettlementItem, UUID> {

    List<SettlementItem> findBySettlementId(UUID settlementId);

}
