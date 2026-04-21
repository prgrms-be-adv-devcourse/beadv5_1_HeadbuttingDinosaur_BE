package com.devticket.settlement.infrastructure.persistence.repository;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementStatus;
import com.devticket.settlement.domain.repository.SettlementRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 주문(internal api) -> 정산 
    @Override
    public List<Settlement> saveAll(List<? extends Settlement> settlements) {
        return settlementJpaRepository.saveAll((List<Settlement>) settlements);
    }

    @Override
    public Settlement save(Settlement settlement) {
        return settlementJpaRepository.save(settlement);
    }

    @Override
    public List<Settlement> findBySellerIdAndStatus(UUID sellerId, SettlementStatus status) {
        return settlementJpaRepository.findBySellerIdAndStatus(sellerId, status);
    }

    @Override
    public List<Settlement> findByStatus(SettlementStatus status) {
        return settlementJpaRepository.findByStatus(status);
    }

    @Override
    public Optional<Settlement> findBySellerIdAndPeriodStartAtBetween(UUID sellerId, LocalDateTime from, LocalDateTime to) {
        return settlementJpaRepository.findBySellerIdAndPeriodStartAtBetween(sellerId, from, to);
    }

    @Override
    public Page<Settlement> search(SettlementStatus status, UUID sellerId,
        LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return settlementJpaRepository.search(status, sellerId, startDate, endDate, pageable);
    }


}
