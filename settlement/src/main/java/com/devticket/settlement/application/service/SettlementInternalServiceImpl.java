package com.devticket.settlement.application.service;

import com.devticket.settlement.common.exception.BusinessException;
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
import com.devticket.settlement.infrastructure.client.SettlementToMemberClient;
import com.devticket.settlement.infrastructure.client.dto.req.InternalSettlementDataRequest;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementPageResponse;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementInternalServiceImpl implements SettlementInternalService {

    private final SettlementToCommerceClient settlementToCommerceClient;
    private final SettlementToMemberClient settlementToMemberClient;
    private final FeePolicyRepository feePolicyRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;

    @Transactional(readOnly = true)
    public InternalSettlementPageResponse getSettlements(
        String status,
        UUID sellerId,
        String startDate,
        String endDate,
        Pageable pageable
    ) {
        SettlementStatus filterStatus = parseStatus(status);
        LocalDateTime start = startDate != null && !startDate.isBlank()
            ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime end = endDate != null && !endDate.isBlank()
            ? LocalDate.parse(endDate).plusDays(1).atStartOfDay() : null;

        Page<Settlement> page = settlementRepository.search(
            filterStatus, sellerId, start, end, pageable
        );

        List<InternalSettlementResponse> content = page.getContent().stream()
            .map(s -> new InternalSettlementResponse(
                null,
                s.getPeriodStartAt().toLocalDate().toString(),
                s.getPeriodEndAt().toLocalDate().toString(),
                (long) s.getTotalSalesAmount(),
                (long) s.getTotalRefundAmount(),
                (long) s.getTotalFeeAmount(),
                (long) s.getFinalSettlementAmount(),
                s.getStatus().name(),
                s.getSettledAt().toString()
            ))
            .toList();

        return new InternalSettlementPageResponse(
            content,
            page.getNumber(),
            page.getSize(),
            (int) page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Transactional
    public void runSettlement() {
        List<UUID> sellerIds = settlementToMemberClient.getSellerIds();
        FeePolicy feePolicy = feePolicyRepository.findTopByOrderByCreatedAtDesc()
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.FEE_POLICY_NOT_FOUND));

        String periodStart = normalizeStartDate(null);
        String periodEnd = normalizeEndDate(null);

        for (UUID sellerId : sellerIds) {
            try {
                InternalSettlementDataRequest request = new InternalSettlementDataRequest(
                    sellerId, periodStart, periodEnd
                );

                InternalSettlementDataResponse data = settlementToCommerceClient.getSettlementData(request);
                if (data == null || data.eventSettlements() == null) continue;

                for (var eventSettlement : data.eventSettlements()) {
                    long fee = feePolicy.calculateFee(eventSettlement.totalSalesAmount());
                    long finalAmount = eventSettlement.totalSalesAmount()
                        - eventSettlement.totalRefundAmount() - fee;

                    Settlement settlement = Settlement.builder()
                        .sellerId(sellerId)
                        .periodStartAt(LocalDate.parse(periodStart).atStartOfDay())
                        .periodEndAt(LocalDate.parse(periodEnd).atStartOfDay())
                        .totalSalesAmount(eventSettlement.totalSalesAmount())
                        .totalRefundAmount(eventSettlement.totalRefundAmount())
                        .totalFeeAmount((int) fee)
                        .finalSettlementAmount((int) finalAmount)
                        .status(SettlementStatus.COMPLETED)
                        .settledAt(LocalDateTime.now())
                        .build();

                    settlementRepository.save(settlement);
                }
            } catch (Exception e) {
                log.warn("[정산 실패] sellerId: {}", sellerId, e);
            }
        }
    }

    private static final int MIN_SETTLEMENT_AMOUNT = 10_000;

    @Transactional
    public void createSettlementFromItems() {
        LocalDate periodFrom = YearMonth.now().minusMonths(2).atDay(26);
        LocalDate periodTo = YearMonth.now().minusMonths(1).atDay(25);
        LocalDateTime settlementPeriodStart = periodFrom.atStartOfDay();
        LocalDateTime settlementPeriodEnd = periodTo.atTime(23, 59, 59);

        List<SettlementItem> targetItems = settlementItemRepository
            .findByStatusAndEventDateTimeBetween(SettlementItemStatus.READY, periodFrom, periodTo);

        log.info("[정산 생성] 대상 기간: {} ~ {}, 항목: {}건", periodFrom, periodTo, targetItems.size());

        Map<UUID, List<SettlementItem>> itemsBySeller = targetItems.stream()
            .collect(Collectors.groupingBy(SettlementItem::getSellerId));

        // 이번 달 신규 아이템이 없어도 이월 건이 있는 판매자도 처리해야 하므로
        // PENDING_MIN_AMOUNT 판매자 목록도 포함
        List<Settlement> allPendingSettlements = settlementRepository
            .findByStatus(SettlementStatus.PENDING_MIN_AMOUNT);
        allPendingSettlements.stream()
            .map(Settlement::getSellerId)
            .distinct()
            .filter(sellerId -> !itemsBySeller.containsKey(sellerId))
            .forEach(sellerId -> itemsBySeller.put(sellerId, List.of()));

        for (Map.Entry<UUID, List<SettlementItem>> entry : itemsBySeller.entrySet()) {
            UUID sellerId = entry.getKey();
            List<SettlementItem> sellerItems = entry.getValue();

            // 이번 달 신규 집계 금액
            long totalSales = sellerItems.stream().mapToLong(SettlementItem::getSalesAmount).sum();
            long totalRefund = sellerItems.stream().mapToLong(SettlementItem::getRefundAmount).sum();
            long totalFee = sellerItems.stream().mapToLong(SettlementItem::getFeeAmount).sum();
            long newSettlementAmount = sellerItems.stream().mapToLong(SettlementItem::getSettlementAmount).sum();

            // 직전 이월 건 조회 (체인의 최신 노드)
            List<Settlement> pendingSettlements = settlementRepository
                .findBySellerIdAndStatus(sellerId, SettlementStatus.PENDING_MIN_AMOUNT);
            Settlement latestPending = pendingSettlements.stream()
                .max(Comparator.comparing(Settlement::getCreatedAt))
                .orElse(null);

            int carriedInAmount = (latestPending != null) ? latestPending.getFinalSettlementAmount() : 0;
            UUID carriedInSettlementId = (latestPending != null) ? latestPending.getSettlementId() : null;
            long totalFinalAmount = newSettlementAmount + carriedInAmount;

            SettlementStatus newStatus = (totalFinalAmount >= MIN_SETTLEMENT_AMOUNT)
                ? SettlementStatus.COMPLETED
                : SettlementStatus.PENDING_MIN_AMOUNT;

            Settlement settlement = Settlement.builder()
                .sellerId(sellerId)
                .periodStartAt(settlementPeriodStart)
                .periodEndAt(settlementPeriodEnd)
                .totalSalesAmount((int) totalSales)
                .totalRefundAmount((int) totalRefund)
                .totalFeeAmount((int) totalFee)
                .finalSettlementAmount((int) totalFinalAmount)
                .carriedInAmount(carriedInAmount)
                .carriedInSettlementId(carriedInSettlementId)
                .status(newStatus)
                .settledAt(LocalDateTime.now())
                .build();

            settlementRepository.save(settlement);

            // 이월 건 해소 시 체인 전체를 COMPLETED로 변경
            if (newStatus == SettlementStatus.COMPLETED && latestPending != null) {
                markChainAsPaidByCarryForward(latestPending);
            }

            if (!sellerItems.isEmpty()) {
                sellerItems.forEach(item -> item.finalize(settlement.getSettlementId()));
                settlementItemRepository.saveAll(sellerItems);
            }

            log.info("[정산 생성] sellerId={}, items={}건, carriedIn={}, finalAmount={}, status={}",
                sellerId, sellerItems.size(), carriedInAmount, totalFinalAmount, newStatus);
        }
    }

    private void markChainAsPaidByCarryForward(Settlement settlement) {
        Settlement cursor = settlement;
        while (cursor != null) {
            cursor.updateStatus(SettlementStatus.COMPLETED);
            settlementRepository.save(cursor);
            UUID nextId = cursor.getCarriedInSettlementId();
            cursor = (nextId != null)
                ? settlementRepository.findBySettlementId(nextId).orElse(null)
                : null;
        }
    }

    private SettlementStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return SettlementStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String normalizeStartDate(String startDate) {
        if (startDate != null && !startDate.isBlank()) return startDate;
        return LocalDate.now().withDayOfMonth(1).toString();
    }

    private String normalizeEndDate(String endDate) {
        if (endDate != null && !endDate.isBlank()) return endDate;
        return LocalDate.now().toString();
    }
}