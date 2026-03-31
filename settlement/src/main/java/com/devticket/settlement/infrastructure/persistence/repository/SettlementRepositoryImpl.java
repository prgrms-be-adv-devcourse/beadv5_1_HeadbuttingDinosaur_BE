package com.devticket.settlement.infrastructure.persistence.repository;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.repository.SettlementRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class SettlementRepositoryImpl implements SettlementRepository {

    private final SettlementJpaRepository settlementJpaRepository;
    private final SettlementItemJpaRepository settlementItemJpaRepository;

    // 판매자 아이디 -> settlement 목록 조회
    @Override
    public List<Settlement> findBySellerId(UUID sellerId) {
        return settlementJpaRepository.findBySellerId(sellerId);
    }

    // settlement_id -> 정산 단건 조회
    @Override
    public Optional<Settlement> findBySettlementId(UUID settlementId) {
        return settlementJpaRepository.findBySettlementId(settlementId);
    }


}
