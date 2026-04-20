package com.devticket.settlement.application.service;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import com.devticket.settlement.domain.exception.SettlementErrorCode;
import com.devticket.settlement.domain.model.FeePolicy;
import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.model.SettlementItemStatus;
import com.devticket.settlement.domain.repository.FeePolicyRepository;
import com.devticket.settlement.domain.repository.SettlementItemRepository;
import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.infrastructure.client.SettlementToCommerceClient;
import com.devticket.settlement.infrastructure.client.SettlementToEventClient;
import com.devticket.settlement.infrastructure.client.dto.req.InternalSettlementDataRequest;
import com.devticket.settlement.infrastructure.client.dto.res.EndedEventResponse;
import com.devticket.settlement.infrastructure.client.dto.res.EventTicketSettlementResponse;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import com.devticket.settlement.presentation.dto.EventItemResponse;
import com.devticket.settlement.presentation.dto.SellerSettlementDetailResponse;
import com.devticket.settlement.presentation.dto.SettlementResponse;
import com.devticket.settlement.presentation.dto.SettlementTargetPreviewResponse;
import com.devticket.settlement.presentation.dto.SettlementTargetPreviewResponse.EventSettlementPreview;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementServiceImpl implements SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final FeePolicyRepository feePolicyRepository;
    private final SettlementToCommerceClient settlementToCommerceClient;
    private final SettlementToEventClient settlementToEventClient;

    @Override
    public InternalSettlementDataResponse fetchSettlementData(UUID sellerId, String periodStart, String periodEnd) {
        InternalSettlementDataRequest request = new InternalSettlementDataRequest(sellerId, periodStart, periodEnd);
        return settlementToCommerceClient.getSettlementData(request);
    }

    @Override
    public List<SettlementResponse> getSellerSettlements(UUID sellerId) {
        List<Settlement> settlements = settlementRepository.findBySellerId(sellerId);
        return settlements.stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public SellerSettlementDetailResponse getSellerSettlementDetail(UUID sellerId, UUID settlementId) {
        Settlement settlement = settlementRepository.findBySettlementId(settlementId)
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BAD_REQUEST));

        validateSellerAccess(sellerId, settlement);

        List<SettlementItem> settlementItems = settlementItemRepository.findBySettlementId(
            settlement.getSettlementId());

        return toResponse(settlement, settlementItems);
    }

    /**
     * 정산대상데이터 요청기능의 수동테스트 진행건.
     */
    @Override
    @Transactional
    public SettlementTargetPreviewResponse previewSettlementTarget(LocalDate targetDate) {
        return collectSettlementTargets(targetDate);
    }

    /**
     * Event 서비스 → Commerce 서비스 실제 API 호출 후
     * Platform Fee 수수료를 적용하여 orderItem 단위로 SettlementItem을 저장한다.
     * orderItemId 기준으로 중복된 항목은 저장하지 않고 SKIPPED로 표기한다.
     */
    @Override
    @Transactional
    public SettlementTargetPreviewResponse collectSettlementTargets(LocalDate targetDate) {
        FeePolicy feePolicy = feePolicyRepository.findByName("PLATFORM_FEE")
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.FEE_POLICY_NOT_FOUND));
        log.info("[collectSettlementTargets] FeePolicy - name: {}, feeValue: {}",
            feePolicy.getName(), feePolicy.getFeeValue());

        log.info("[collectSettlementTargets] Event 서비스 호출 - targetDate: {}", targetDate);
        List<EndedEventResponse> endedEvents = settlementToEventClient.getEndedEvents(targetDate);
        log.info("[collectSettlementTargets] 종료된 이벤트 {}건 조회됨", endedEvents.size());

        if (endedEvents.isEmpty()) {
            return new SettlementTargetPreviewResponse(
                targetDate.toString(), 0, 0, 0,
                feePolicy.getName(), feePolicy.getFeeValue().toPlainString(),
                List.of()
            );
        }

        Map<UUID, EndedEventResponse> eventMap = endedEvents.stream()
            .collect(Collectors.toMap(EndedEventResponse::eventId, e -> e));

        List<UUID> eventIds = endedEvents.stream().map(EndedEventResponse::eventId).toList();
        List<EventTicketSettlementResponse> ticketItems =
            settlementToCommerceClient.getTicketSettlementData(eventIds);
        log.info("[collectSettlementTargets] Commerce 응답 - orderItem 건수: {}", ticketItems.size());

        List<EventSettlementPreview> previews = new ArrayList<>();
        int savedCount = 0;
        int skippedCount = 0;

        for (EventTicketSettlementResponse ticketItem : ticketItems) {
            Long feeAmount = feePolicy.calculateFee(ticketItem.salesAmount());
            Long settlementAmount = ticketItem.salesAmount() - ticketItem.refundAmount() - feeAmount;
            EndedEventResponse event = eventMap.get(ticketItem.eventId());

            if (settlementItemRepository.existsByOrderItemId(ticketItem.orderItemId())) {
                log.warn("[collectSettlementTargets] 중복 스킵 - orderItemId: {}", ticketItem.orderItemId());
                previews.add(new EventSettlementPreview(
                    ticketItem.orderItemId(), event.id(), ticketItem.eventId(), event.sellerId(),
                    ticketItem.salesAmount(), ticketItem.refundAmount(), feeAmount, settlementAmount,
                    "SKIPPED"
                ));
                skippedCount++;
                continue;
            }

            SettlementItem item = SettlementItem.builder()
                .orderItemId(ticketItem.orderItemId())
                .eventId(event.id())
                .eventUUID(ticketItem.eventId())
                .sellerId(event.sellerId())
                .salesAmount(ticketItem.salesAmount())
                .refundAmount(ticketItem.refundAmount())
                .feeAmount(feeAmount)
                .settlementAmount(settlementAmount)
                .status(SettlementItemStatus.READY)
                .build();

            settlementItemRepository.save(item);
            savedCount++;

            log.info("[collectSettlementTargets] 저장 완료 - orderItemId: {}, eventId: {}, sales: {}, fee: {}, settlement: {}",
                ticketItem.orderItemId(), ticketItem.eventId(),
                ticketItem.salesAmount(), feeAmount, settlementAmount);

            previews.add(new EventSettlementPreview(
                ticketItem.orderItemId(), event.id(), ticketItem.eventId(), event.sellerId(),
                ticketItem.salesAmount(), ticketItem.refundAmount(), feeAmount, settlementAmount,
                "SAVED"
            ));
        }

        log.info("[collectSettlementTargets] 완료 - 저장: {}건, 중복 스킵: {}건", savedCount, skippedCount);
        return new SettlementTargetPreviewResponse(
            targetDate.toString(),
            endedEvents.size(),
            savedCount,
            skippedCount,
            feePolicy.getName(),
            feePolicy.getFeeValue().toPlainString(),
            previews
        );
    }


    private void validateSellerAccess(UUID sellerId, Settlement settlement) {
        if (!sellerId.equals(settlement.getSellerId())) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

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

    private SellerSettlementDetailResponse toResponse(Settlement settlement, List<SettlementItem> settlementItems) {
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