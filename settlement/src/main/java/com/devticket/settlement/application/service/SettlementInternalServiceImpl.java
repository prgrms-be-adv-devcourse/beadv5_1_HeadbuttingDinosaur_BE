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
import java.util.Comparator;
import java.util.HashMap;
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

    // ────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────

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
        LocalDateTime periodStart = periodFrom.atStartOfDay();
        LocalDateTime periodEnd = periodTo.atTime(23, 59, 59);

        Map<UUID, List<SettlementItem>> itemsBySeller = collectItemsBySeller(periodFrom, periodTo);
        includeCarryOverSellers(itemsBySeller);

        log.info("[정산 생성] 대상 기간: {} ~ {}, 판매자: {}명", periodFrom, periodTo, itemsBySeller.size());

        for (Map.Entry<UUID, List<SettlementItem>> entry : itemsBySeller.entrySet()) {
            processSellerSettlement(entry.getKey(), entry.getValue(), periodStart, periodEnd);
        }
    }

    // ────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────

    private Map<UUID, List<SettlementItem>> collectItemsBySeller(LocalDate from, LocalDate to) {
        List<SettlementItem> items = settlementItemRepository
            .findByStatusAndEventDateTimeBetween(SettlementItemStatus.READY, from, to);
        log.info("[정산 생성] READY 항목: {}건", items.size());
        return new HashMap<>(items.stream()
            .collect(Collectors.groupingBy(SettlementItem::getSellerId)));
    }

    private void includeCarryOverSellers(Map<UUID, List<SettlementItem>> itemsBySeller) {
        settlementRepository.findByStatus(SettlementStatus.PENDING_MIN_AMOUNT).stream()
            .map(Settlement::getSellerId)
            .distinct()
            .filter(sellerId -> !itemsBySeller.containsKey(sellerId))
            .forEach(sellerId -> itemsBySeller.put(sellerId, List.of()));
    }

    private void processSellerSettlement(UUID sellerId, List<SettlementItem> items,
        LocalDateTime periodStart, LocalDateTime periodEnd) {

        SellerAmounts amounts = aggregateAmounts(items);
        Settlement latestPending = resolveLatestPending(sellerId);

        int carriedInAmount = latestPending != null ? latestPending.getFinalSettlementAmount() : 0;
        UUID carriedInSettlementId = latestPending != null ? latestPending.getSettlementId() : null;
        long totalFinalAmount = amounts.settlementAmount() + carriedInAmount;
        SettlementStatus status = totalFinalAmount >= MIN_SETTLEMENT_AMOUNT
            ? SettlementStatus.COMPLETED
            : SettlementStatus.PENDING_MIN_AMOUNT;

        Settlement settlement = Settlement.builder()
            .sellerId(sellerId)
            .periodStartAt(periodStart)
            .periodEndAt(periodEnd)
            .totalSalesAmount((int) amounts.totalSales())
            .totalRefundAmount((int) amounts.totalRefund())
            .totalFeeAmount((int) amounts.totalFee())
            .finalSettlementAmount((int) totalFinalAmount)
            .carriedInAmount(carriedInAmount)
            .carriedInSettlementId(carriedInSettlementId)
            .status(status)
            .settledAt(LocalDateTime.now())
            .build();

        settlementRepository.save(settlement);

        if (status == SettlementStatus.COMPLETED && latestPending != null) {
            markChainAsPaidByCarryForward(latestPending);
        }

        if (!items.isEmpty()) {
            items.forEach(item -> item.finalize(settlement.getSettlementId()));
            settlementItemRepository.saveAll(items);
        }

        log.info("[정산 생성] sellerId={}, items={}건, carriedIn={}, finalAmount={}, status={}",
            sellerId, items.size(), carriedInAmount, totalFinalAmount, status);
    }

    private SellerAmounts aggregateAmounts(List<SettlementItem> items) {
        return new SellerAmounts(
            items.stream().mapToLong(SettlementItem::getSalesAmount).sum(),
            items.stream().mapToLong(SettlementItem::getRefundAmount).sum(),
            items.stream().mapToLong(SettlementItem::getFeeAmount).sum(),
            items.stream().mapToLong(SettlementItem::getSettlementAmount).sum()
        );
    }

    private Settlement resolveLatestPending(UUID sellerId) {
        return settlementRepository
            .findBySellerIdAndStatus(sellerId, SettlementStatus.PENDING_MIN_AMOUNT)
            .stream()
            .max(Comparator.comparing(Settlement::getCreatedAt))
            .orElse(null);
    }

    private record SellerAmounts(long totalSales, long totalRefund, long totalFee, long settlementAmount) {}

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