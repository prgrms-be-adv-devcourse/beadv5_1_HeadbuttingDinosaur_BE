package com.devticket.settlement.application.service;

import com.devticket.settlement.domain.model.Settlement;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.presentation.dto.SettlementResponse;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementServiceImpl implements SettlementService{

    private final SettlementRepository settlementRepository;

    // 정산 내역 목록 조회
    @Override
    public List<SettlementResponse> getSellerSettlements(Long sellerId){
        return settlementRepository.findBySellerId(sellerId).stream()
            .map(this::toResponse)
            .toList();
    }

    // Settlement -> SettlementResponse 변환 메서드
    private SettlementResponse toResponse(Settlement settlement){
        return new SettlementResponse(
            settlement.getSettlementId(),
            settlement.getPeriodStartAt().toString(),
            settlement.getPeriodEndAt().toString(),
            settlement.getTotalSalesAmount(),
            settlement.getTotalRefundAmount(),
            settlement.getTotalFeeAmount(),
            settlement.getFinalSettlementAmount(),
            settlement.getStatus(),
            settlement.getSettledAt() != null ? settlement.getSettledAt().toString() : null
        );
    }
}
