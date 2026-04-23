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
import com.devticket.settlement.infrastructure.client.SettlementToPaymentClient;
import com.devticket.settlement.infrastructure.external.dto.AdminSettlementDetailResponse;
import com.devticket.settlement.infrastructure.external.dto.AdminSettlementDetailResponse.CarriedInSettlement;
import com.devticket.settlement.infrastructure.client.SettlementToCommerceClient;
import com.devticket.settlement.infrastructure.client.SettlementToMemberClient;
import com.devticket.settlement.infrastructure.client.dto.req.InternalSettlementDataRequest;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementPageResponse;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementResponse;
import com.devticket.settlement.presentation.dto.EventItemResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementInternalServiceImpl implements SettlementInternalService {

    private static final int MIN_SETTLEMENT_AMOUNT = 10_000;

    private final SettlementToCommerceClient settlementToCommerceClient;
    private final SettlementToPaymentClient settlementToPaymentClient;
    private final SettlementToMemberClient settlementToMemberClient;
    private final FeePolicyRepository feePolicyRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;

    // ────────────────────────────────────────────────
    // 정산서 목록 조회 (관리자)
    // ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InternalSettlementPageResponse getSettlements(
        String status, UUID sellerId, String yearMonth, Pageable pageable
    ) {
        SettlementStatus filterStatus = parseStatus(status);
        LocalDateTime monthStart = null;
        LocalDateTime monthEnd = null;
        if (yearMonth != null && !yearMonth.isBlank()) {
            YearMonth ym = YearMonth.parse(yearMonth, java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
            monthStart = ym.atDay(1).atStartOfDay();
            monthEnd = ym.plusMonths(1).atDay(1).atStartOfDay();
        }

        Page<Settlement> page = settlementRepository.search(filterStatus, sellerId, monthStart, monthEnd, pageable);

        List<InternalSettlementResponse> content = page.getContent().stream()
            .map(s -> new InternalSettlementResponse(
                s.getSettlementId(),
                s.getPeriodStartAt().toLocalDate().toString(),
                s.getPeriodEndAt().toLocalDate().toString(),
                (long) s.getTotalSalesAmount(),
                (long) s.getTotalRefundAmount(),
                (long) s.getTotalFeeAmount(),
                (long) s.getCarriedInAmount(),
                (long) s.getFinalSettlementAmount(),
                s.getStatus().name(),
                s.getSettledAt() != null ? s.getSettledAt().toString() : null,
                s.getCarriedToSettlementId(),
                s.getSellerId()
            ))
            .toList();

        return new InternalSettlementPageResponse(
            content, page.getNumber(), page.getSize(),
            (int) page.getTotalElements(), page.getTotalPages()
        );
    }

    // ────────────────────────────────────────────────
    // 정산서 생성 (Commerce 직접 호출 방식 - Legacy)
    // ────────────────────────────────────────────────

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
                        .status(SettlementStatus.CONFIRMED)
                        .settledAt(LocalDateTime.now())
                        .build();

                    settlementRepository.save(settlement);
                }
            } catch (Exception e) {
                log.warn("[정산 실패] sellerId: {}", sellerId, e);
            }
        }
    }

    // ────────────────────────────────────────────────
    // 정산서 생성 (SettlementItem 기반 Batch)
    // ────────────────────────────────────────────────

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
    // 정산서 상세 조회 (관리자)
    // ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminSettlementDetailResponse getSettlementDetail(UUID settlementId) {
        Settlement settlement = settlementRepository.findBySettlementId(settlementId)
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BAD_REQUEST));

        List<EventItemResponse> settlementItems = settlementItemRepository
            .findBySettlementId(settlementId)
            .stream()
            .map(item -> new EventItemResponse(
                item.getEventId().toString(),
                "Unknown",
                item.getSalesAmount(),
                item.getRefundAmount(),
                item.getFeeAmount(),
                item.getSettlementAmount()
            ))
            .toList();

        // 이 정산서에 이월된 출처 정산서 목록 (역조회)
        List<CarriedInSettlement> carriedInSettlements = settlementRepository
            .findByCarriedToSettlementId(settlementId)
            .stream()
            .map(s -> new CarriedInSettlement(
                s.getSettlementId(),
                s.getPeriodStartAt().toLocalDate().toString(),
                s.getPeriodEndAt().toLocalDate().toString(),
                (long) s.getFinalSettlementAmount(),
                s.getStatus().name()
            ))
            .toList();

        long settlementAmount = settlement.getFinalSettlementAmount() - settlement.getCarriedInAmount();

        return new AdminSettlementDetailResponse(
            settlement.getSettlementId(),
            settlement.getSellerId(),
            settlement.getPeriodStartAt().toLocalDate().toString(),
            settlement.getPeriodEndAt().toLocalDate().toString(),
            (long) settlement.getTotalSalesAmount(),
            (long) settlement.getTotalRefundAmount(),
            (long) settlement.getTotalFeeAmount(),
            settlementAmount,
            (long) settlement.getCarriedInAmount(),
            (long) settlement.getFinalSettlementAmount(),
            settlement.getStatus().name(),
            settlement.getSettledAt() != null ? settlement.getSettledAt().toString() : null,
            settlement.getCarriedToSettlementId(),
            carriedInSettlements,
            settlementItems
        );
    }

    // ────────────────────────────────────────────────
    // 정산서 취소
    // ────────────────────────────────────────────────

    @Transactional
    public void cancelSettlement(UUID settlementId) {
        Settlement settlement = settlementRepository.findBySettlementId(settlementId)
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BAD_REQUEST));

        settlement.updateStatus(SettlementStatus.CANCELLED);
        settlementRepository.save(settlement);

        List<SettlementItem> items = settlementItemRepository.findBySettlementId(settlementId);
        items.forEach(SettlementItem::resetToReady);
        settlementItemRepository.saveAll(items);

        // 이 정산서에 이월됐던 PENDING 정산서들을 복구
        List<Settlement> carriedSettlements = settlementRepository.findByCarriedToSettlementId(settlementId);
        carriedSettlements.forEach(s -> {
            s.updateStatus(SettlementStatus.PENDING_MIN_AMOUNT);
            s.updateCarriedToSettlementId(null);
        });
        settlementRepository.saveAll(carriedSettlements);

        log.info("[정산 취소] settlementId={}, 이월복구={}건", settlementId, carriedSettlements.size());
    }

    // ────────────────────────────────────────────────
    // 지급처리
    // ────────────────────────────────────────────────

    @Transactional(noRollbackFor = BusinessException.class)
    public void processPayment(UUID settlementId) {
        Settlement settlement = settlementRepository.findBySettlementId(settlementId)
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BAD_REQUEST));

        if (settlement.getStatus() != SettlementStatus.CONFIRMED
            && settlement.getStatus() != SettlementStatus.PAID_FAILED) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_BAD_REQUEST);
        }

        try {
            settlementToPaymentClient.transferToDeposit(
                settlement.getSettlementId(), settlement.getSellerId(), settlement.getFinalSettlementAmount()
            );

            settlement.updateStatus(SettlementStatus.PAID);
            settlementRepository.save(settlement);

            markCarriedSettlementsAsPaid(settlementId);

            log.info("[지급 완료] settlementId={}, amount={}", settlementId, settlement.getFinalSettlementAmount());

        } catch (Exception e) {
            settlement.updateStatus(SettlementStatus.PAID_FAILED);
            settlementRepository.save(settlement);
            log.error("[지급 실패] settlementId={}", settlementId, e);
            throw new BusinessException(SettlementErrorCode.PAYMENT_FAILED);
        }
    }

    // ────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────

    private void markCarriedSettlementsAsPaid(UUID settlementId) {
        List<Settlement> carried = settlementRepository.findByCarriedToSettlementId(settlementId);
        carried.forEach(s -> s.updateStatus(SettlementStatus.PAID));
        settlementRepository.saveAll(carried);
    }

    private Map<UUID, List<SettlementItem>> collectItemsBySeller(LocalDate from, LocalDate to) {
        List<SettlementItem> items = settlementItemRepository
            .findByStatusAndEventDateTimeBetween(SettlementItemStatus.READY, from, to);
        log.info("[정산 생성] READY 항목: {}건", items.size());
        return new HashMap<>(items.stream()
            .collect(Collectors.groupingBy(SettlementItem::getSellerId)));
    }

    private void includeCarryOverSellers(Map<UUID, List<SettlementItem>> itemsBySeller) {
        // 아직 이월되지 않은 PENDING 판매자도 포함
        settlementRepository.findByStatus(SettlementStatus.PENDING_MIN_AMOUNT).stream()
            .filter(s -> s.getCarriedToSettlementId() == null)
            .map(Settlement::getSellerId)
            .distinct()
            .filter(sellerId -> !itemsBySeller.containsKey(sellerId))
            .forEach(sellerId -> itemsBySeller.put(sellerId, List.of()));
    }

    private void processSellerSettlement(UUID sellerId, List<SettlementItem> items,
        LocalDateTime periodStart, LocalDateTime periodEnd) {

        if (alreadySettledForPeriod(sellerId, periodStart)) {
            log.info("[정산 생성 스킵] 이미 처리된 기간 - sellerId={}, periodStart={}", sellerId, periodStart);
            return;
        }

        SellerAmounts amounts = aggregateAmounts(items);
        Settlement pendingSettlement = resolveLatestPending(sellerId);

        int carriedInAmount = pendingSettlement != null ? pendingSettlement.getFinalSettlementAmount() : 0;
        long finalAmount = amounts.settlementAmount() + carriedInAmount;
        SettlementStatus status = finalAmount >= MIN_SETTLEMENT_AMOUNT
            ? SettlementStatus.CONFIRMED
            : SettlementStatus.PENDING_MIN_AMOUNT;

        Settlement settlement = Settlement.builder()
            .sellerId(sellerId)
            .periodStartAt(periodStart)
            .periodEndAt(periodEnd)
            .totalSalesAmount((int) amounts.totalSales())
            .totalRefundAmount((int) amounts.totalRefund())
            .totalFeeAmount((int) amounts.totalFee())
            .finalSettlementAmount((int) finalAmount)
            .carriedInAmount(carriedInAmount)
            .status(status)
            .settledAt(LocalDateTime.now())
            .build();

        settlementRepository.save(settlement);

        // 이월된 PENDING 정산서에 carriedToSettlementId 설정
        if (pendingSettlement != null) {
            pendingSettlement.updateCarriedToSettlementId(settlement.getSettlementId());
            settlementRepository.save(pendingSettlement);
        }

        if (!items.isEmpty()) {
            items.forEach(item -> item.finalize(settlement.getSettlementId()));
            settlementItemRepository.saveAll(items);
        }

        log.info("[정산 생성] sellerId={}, items={}건, carriedIn={}, finalAmount={}, status={}",
            sellerId, items.size(), carriedInAmount, finalAmount, status);
    }

    private boolean alreadySettledForPeriod(UUID sellerId, LocalDateTime periodStart) {
        return settlementRepository.existsBySellerIdAndPeriodStartAtBetweenAndStatusNot(
            sellerId,
            periodStart,
            periodStart.toLocalDate().atTime(23, 59, 59),
            SettlementStatus.CANCELLED
        );
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
            .findBySellerIdAndStatusAndCarriedToSettlementIdIsNull(sellerId, SettlementStatus.PENDING_MIN_AMOUNT)
            .stream()
            .max(Comparator.comparing(Settlement::getCreatedAt))
            .orElse(null);
    }

    private record SellerAmounts(long totalSales, long totalRefund, long totalFee, long settlementAmount) {}

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
