package com.devticket.settlement.infrastructure.persistence.repository;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.infrastructure.persistence.entity.SettlementEntity;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class SettlementRepositoryImpl implements SettlementRepository {

    private final SettlementJpaRepository settlementJpaRepository;

    @Override
    public List<Settlement> findBySellerId(UUID sellerId) {
        return settlementJpaRepository.findBySellerId(sellerId).stream()
            .map(SettlementEntity::toDomain)
            .toList();
    }

}
