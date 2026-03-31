package com.devticket.settlement.application.service;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import com.devticket.settlement.domain.exception.SettlementErrorCode;
import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.repository.SettlementItemRepository;
import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.infrastructure.client.SettlementToCommerceClient;
import com.devticket.settlement.infrastructure.client.dto.req.InternalSettlementDataRequest;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
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
    private final SettlementToCommerceClient settlementToCommerceClient;

    @Override
    public InternalSettlementDataResponse fetchSettlementData(UUID sellerId, String periodStart, String periodEnd) {
        InternalSettlementDataRequest request = new InternalSettlementDataRequest(sellerId, periodStart, periodEnd);
        return settlementToCommerceClient.getSettlementData(request);
    }

    // 정산 내역 목록 조회
    @Override
    public List<SettlementResponse> getSellerSettlements(UUID sellerId) {
        List<Settlement> settlements = settlementRepository.findBySellerId(sellerId);
        if (settlements.isEmpty()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        }
        return settlements.stream()
            .map(this::toResponse)
            .toList();
    }


    // 정산 내역 상세 조회
    @Override
    public SellerSettlementDetailResponse getSellerSettlementDetail(UUID sellerId, UUID settlementId) {
        Settlement settlement = settlementRepository.findBySettlementId(settlementId)
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BAD_REQUEST));

        validateSellerAccess(sellerId, settlement);

        List<SettlementItem> settlementItems = settlementItemRepository.findBySettlementId(
            settlement.getSettlementId());

        return toResponse(settlement, settlementItems);
    }


    //    사용자 인가 확인 메서드
    private void validateSellerAccess(UUID sellerId, Settlement settlement) {
        if (!sellerId.equals(settlement.getSellerId())) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
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
