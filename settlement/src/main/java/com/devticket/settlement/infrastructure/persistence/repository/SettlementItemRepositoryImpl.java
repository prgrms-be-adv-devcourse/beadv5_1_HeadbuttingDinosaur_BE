package com.devticket.settlement.infrastructure.persistence.repository;

import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.repository.SettlementItemRepository;
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
}
