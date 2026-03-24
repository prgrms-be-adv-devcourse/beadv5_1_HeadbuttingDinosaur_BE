package com.devticket.settlement.infrastructure.persistence.repository;

import com.devticket.settlement.domain.model.Settlement;

import com.devticket.settlement.infrastructure.persistence.entity.SettlementEntity;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementJpaRepository extends JpaRepository<Settlement, UUID> {

    List<SettlementEntity> findBySellerId(UUID sellerId);

}
