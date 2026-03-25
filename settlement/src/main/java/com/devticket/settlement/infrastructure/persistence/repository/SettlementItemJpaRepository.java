package com.devticket.settlement.infrastructure.persistence.repository;

import com.devticket.settlement.domain.model.SettlementItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemJpaRepository extends JpaRepository<SettlementItem, Long> {

    Optional<SettlementItem> findById(Long id);

}
