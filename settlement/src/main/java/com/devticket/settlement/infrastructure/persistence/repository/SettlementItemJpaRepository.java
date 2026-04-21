package com.devticket.settlement.infrastructure.persistence.repository;

import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.model.SettlementItemStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemJpaRepository extends JpaRepository<SettlementItem, Long> {

    List<SettlementItem> findBySettlementId(UUID settlementId);

    boolean existsByOrderItemId(UUID orderItemId);

    List<SettlementItem> findByStatusAndEventDateTimeBetween(
        SettlementItemStatus status, LocalDate from, LocalDate to);

}
