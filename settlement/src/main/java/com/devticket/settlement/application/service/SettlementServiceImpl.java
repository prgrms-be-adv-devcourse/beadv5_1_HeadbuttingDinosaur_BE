package com.devticket.settlement.application.service;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import com.devticket.settlement.domain.exception.SettlementErrorCode;
import com.devticket.settlement.domain.model.FeePolicy;
import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.model.SettlementItemStatus;
import com.devticket.settlement.domain.model.SettlementStatus;
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
import com.devticket.settlement.presentation.dto.SettlementPeriodResponse;
import com.devticket.settlement.presentation.dto.SettlementResponse;
import com.devticket.settlement.presentation.dto.SettlementTargetPreviewResponse;
import com.devticket.settlement.presentation.dto.SettlementTargetPreviewResponse.EventSettlementPreview;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        Map<UUID, String> eventTitles = fetchEventTitles(settlementItems);
        return toResponse(settlement, settlementItems, eventTitles);
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
            EndedEventResponse event = eventMap.get(ticketItem.eventId());
            if (event == null) {
                log.warn("[collectSettlementTargets] Event 서비스 응답에 없는 eventId 스킵 - orderItemId: {}, eventId: {}",
                    ticketItem.orderItemId(), ticketItem.eventId());
                skippedCount++;
                continue;
            }

            Long feeAmount = feePolicy.calculateFee(ticketItem.salesAmount());
            Long settlementAmount = ticketItem.salesAmount() - ticketItem.refundAmount() - feeAmount;

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

            log.info(event.toString());

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
                .eventDateTime(event.eventDateTime())
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


    @Override
    public SettlementPeriodResponse getSettlementByPeriod(UUID sellerId, String yearMonth) {
        log.debug("[getSettlementByPeriod] 시작 - sellerId={}, yearMonth={}", sellerId, yearMonth);
        try {
            YearMonth ym = parseYearMonth(yearMonth);
            LocalDate periodStartDate = toPeriodStartDate(ym);
            log.debug("[getSettlementByPeriod] 조회 기간 - periodStartDate={} ({} ~ {})",
                periodStartDate, periodStartDate.atStartOfDay(), periodStartDate.atTime(23, 59, 59));

            return settlementRepository
                .findFirstBySellerIdAndPeriodStartAtBetweenAndStatusNotOrderByCreatedAtDesc(
                    sellerId,
                    periodStartDate.atStartOfDay(),
                    periodStartDate.atTime(23, 59, 59),
                    SettlementStatus.CANCELLED)
                .map(settlement -> {
                    log.debug("[getSettlementByPeriod] 정산서 조회 성공 - settlementId={}, status={}, finalAmount={}",
                        settlement.getSettlementId(), settlement.getStatus(), settlement.getFinalSettlementAmount());
                    return toSettlementPeriodResponse(settlement);
                })
                .orElseGet(() -> {
                    log.debug("[getSettlementByPeriod] 정산서 없음 - sellerId={}, periodStartDate={}", sellerId, periodStartDate);
                    return new SettlementPeriodResponse(0, 0, 0, 0, List.of());
                });
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[getSettlementByPeriod] 처리 중 오류 - sellerId={}, yearMonth={}", sellerId, yearMonth, e);
            throw e;
        }
    }

    private YearMonth parseYearMonth(String yearMonth) {
        try {
            return YearMonth.parse(yearMonth, DateTimeFormatter.ofPattern("yyyyMM"));
        } catch (DateTimeParseException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private LocalDate toPeriodStartDate(YearMonth ym) {
        return ym.minusMonths(1).atDay(26);
    }

    private SettlementPeriodResponse toSettlementPeriodResponse(Settlement settlement) {
        log.debug("[toSettlementPeriodResponse] SettlementItem 조회 시작 - settlementId={}",
            settlement.getSettlementId());
        List<SettlementItem> settlementItems = settlementItemRepository
            .findBySettlementId(settlement.getSettlementId());
        Map<UUID, String> eventTitles = fetchEventTitles(settlementItems);
        List<EventItemResponse> items = settlementItems.stream()
            .map(item -> toResponse(item, eventTitles))
            .toList();
        log.debug("[toSettlementPeriodResponse] SettlementItem 조회 완료 - {}건", items.size());
        return new SettlementPeriodResponse(
            settlement.getFinalSettlementAmount(),
            settlement.getTotalFeeAmount(),
            settlement.getTotalSalesAmount(),
            settlement.getCarriedInAmount(),
            items
        );
    }

    @Override
    public SettlementPeriodResponse getSettlementPreview(UUID sellerId) {
        LocalDate periodFrom = YearMonth.now().minusMonths(1).atDay(26);
        LocalDate periodTo = YearMonth.now().atDay(25);

        List<SettlementItem> readyItems = settlementItemRepository
            .findBySellerIdAndStatusAndEventDateTimeBetween(sellerId, SettlementItemStatus.READY, periodFrom, periodTo);

        long totalSales = readyItems.stream().mapToLong(SettlementItem::getSalesAmount).sum();
        long totalFee = readyItems.stream().mapToLong(SettlementItem::getFeeAmount).sum();
        long newSettlementAmount = readyItems.stream().mapToLong(SettlementItem::getSettlementAmount).sum();

        int carriedInAmount = settlementRepository
            .findBySellerIdAndStatusAndCarriedToSettlementIdIsNull(sellerId, SettlementStatus.PENDING_MIN_AMOUNT)
            .stream()
            .max(Comparator.comparing(Settlement::getCreatedAt))
            .map(Settlement::getFinalSettlementAmount)
            .orElse(0);

        long finalSettlementAmount = newSettlementAmount + carriedInAmount;

        Map<UUID, String> eventTitles = fetchEventTitles(readyItems);
        List<EventItemResponse> items = readyItems.stream()
            .map(item -> toResponse(item, eventTitles))
            .toList();

        return new SettlementPeriodResponse(
            (int) finalSettlementAmount,
            (int) totalFee,
            (int) totalSales,
            carriedInAmount,
            items
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

    private SellerSettlementDetailResponse toResponse(Settlement settlement,
        List<SettlementItem> settlementItems, Map<UUID, String> eventTitles) {
        List<EventItemResponse> eventItems = settlementItems.stream()
            .map(item -> toResponse(item, eventTitles))
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

    private EventItemResponse toResponse(SettlementItem settlementItem, Map<UUID, String> eventTitles) {
        UUID eventUUID = settlementItem.getEventUUID();
        String title = eventTitles.getOrDefault(eventUUID, "Unknown");
        return new EventItemResponse(
            eventUUID != null ? eventUUID.toString() : null,
            title,
            settlementItem.getSalesAmount(),
            settlementItem.getRefundAmount(),
            settlementItem.getFeeAmount(),
            settlementItem.getSettlementAmount()
        );
    }

    /**
     * SettlementItem 목록에서 eventUUID 를 모아 Event 서비스에 일괄 조회한다.
     * 항목별 단건 호출(N+1) 대신 1회 호출로 eventTitle 을 채운다.
     */
    private Map<UUID, String> fetchEventTitles(List<SettlementItem> settlementItems) {
        if (settlementItems.isEmpty()) {
            return Map.of();
        }
        List<UUID> eventUUIDs = settlementItems.stream()
            .map(SettlementItem::getEventUUID)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (eventUUIDs.isEmpty()) {
            return Map.of();
        }
        return settlementToEventClient.getEventTitles(eventUUIDs);
    }
}
