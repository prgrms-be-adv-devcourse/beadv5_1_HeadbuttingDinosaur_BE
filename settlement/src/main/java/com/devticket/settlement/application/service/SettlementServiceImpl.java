package com.devticket.settlement.application.service;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.repository.SettlementItemRepository;
import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.presentation.dto.EventItemResponse;
import com.devticket.settlement.presentation.dto.SellerSettlementDetailResponse;
import com.devticket.settlement.presentation.dto.SettlementResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementServiceImpl implements SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;

    // 정산 내역 목록 조회
    @Override
    public List<SettlementResponse> getSellerSettlements(Long sellerId) {
        return settlementRepository.findBySellerId(sellerId).stream()
            .map(this::toResponse)
            .toList();
    }

    // 정산 내역 상세 조회
    @Override
    public SellerSettlementDetailResponse getSellerSettlementDetail(Long sellerId, UUID settlementId) {
        Settlement settlement = settlementRepository.findBySettlementId(settlementId)
            .orElseThrow(() -> new IllegalArgumentException("해당 정산 내역이 존재하지 않습니다."));

        validateSellerAccess(sellerId, settlement);

        List<SettlementItem> settlementItems = settlementItemRepository.findBySettlementId(
            settlement.getSettlementId());

        return toResponse(settlement, settlementItems);

    }


    //    사용자 인가 확인 메서드
    private void validateSellerAccess(Long sellerId, Settlement settlement) {
        if (!sellerId.equals(settlement.getSellerId())) {
            throw new IllegalArgumentException("해당 정산 조회 권한이 없습니다.");
        }
    }


    //    dto 변환 메서드 : "toResponse"
    // 1. Settlement -> SettlementResponse 변환 메서드
    private SettlementResponse toResponse(Settlement settlement) {
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

    // 2. SettlementItem, settlement -> SellerSettlementDetailResponse 변환 메서드
    private SellerSettlementDetailResponse toResponse(Settlement settlement, List<SettlementItem> settlementItems) {
        // e
        List<EventItemResponse> eventItems = settlementItems.stream()
            .map(this::toResponse)
            .toList();

        return new SellerSettlementDetailResponse(
            settlement.getSettlementId().toString(),
            settlement.getPeriodStartAt().toString(),
            settlement.getPeriodEndAt().toString(),
            settlement.getTotalSalesAmount(),
            settlement.getTotalRefundAmount(),
            settlement.getTotalFeeAmount(),
            settlement.getFinalSettlementAmount(),
            settlement.getStatus().name(),
            settlement.getSettledAt() != null ? settlement.getSettledAt().toString() : null,
            eventItems
        );
    }

    // 3. SettlementItem -> EventItems
    private EventItemResponse toResponse(SettlementItem settlementItem) {
        return new EventItemResponse(
            settlementItem.getEventId().toString(),
            "Unknown",
            settlementItem.getSalesAmount(),
            settlementItem.getRefundAmount(),
            settlementItem.getFeeAmount(),
            settlementItem.getSettlementAmount()
        );
    }

}
