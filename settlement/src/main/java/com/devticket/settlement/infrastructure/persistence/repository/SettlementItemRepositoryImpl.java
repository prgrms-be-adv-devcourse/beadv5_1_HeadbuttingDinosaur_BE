package com.devticket.settlement.infrastructure.persistence.repository;

import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.model.SettlementItemStatus;
import com.devticket.settlement.domain.repository.SettlementItemRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SettlementItemRepositoryImpl implements SettlementItemRepository {

    private final SettlementItemJpaRepository settlementItemJpaRepository;

    @Override
    public List<SettlementItem> findBySettlementId(UUID settlementId) {
        return settlementItemJpaRepository.findBySettlementId(settlementId);
    }

    @Override
    public boolean existsByOrderItemId(UUID orderItemId) {
        return settlementItemJpaRepository.existsByOrderItemId(orderItemId);
    }

    @Override
    public SettlementItem save(SettlementItem settlementItem) {
        return settlementItemJpaRepository.save(settlementItem);
    }

    @Override
    public List<SettlementItem> findByStatusAndEventDateTimeBetween(
        SettlementItemStatus status, LocalDate from, LocalDate to) {
        return settlementItemJpaRepository.findByStatusAndEventDateTimeBetween(status, from, to);
    }

    @Override
    public List<SettlementItem> saveAll(List<SettlementItem> items) {
        return settlementItemJpaRepository.saveAll(items);
    }
}
