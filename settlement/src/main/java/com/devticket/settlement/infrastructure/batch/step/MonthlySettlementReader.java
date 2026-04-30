package com.devticket.settlement.infrastructure.batch.step;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.model.SettlementItemStatus;
import com.devticket.settlement.domain.model.SettlementStatus;
import com.devticket.settlement.domain.repository.SettlementItemRepository;
import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.infrastructure.batch.dto.SellerSettlementData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class MonthlySettlementReader implements ItemReader<SellerSettlementData> {

    private final SettlementItemRepository settlementItemRepository;
    private final SettlementRepository settlementRepository;

    @Value("#{jobParameters['yearMonth']}")
    private String yearMonth;

    private List<Map.Entry<UUID, List<SettlementItem>>> sellerData;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private int index = 0;

    @Override
    public SellerSettlementData read() {
        if (sellerData == null) {
            init();
        }
        if (index >= sellerData.size()) {
            return null;
        }

        Map.Entry<UUID, List<SettlementItem>> entry = sellerData.get(index++);
        UUID sellerId = entry.getKey();
        List<SettlementItem> items = entry.getValue();

        Settlement pendingSettlement = settlementRepository
            .findBySellerIdAndStatusAndCarriedToSettlementIdIsNull(sellerId, SettlementStatus.PENDING_MIN_AMOUNT)
            .stream()
            .max(Comparator.comparing(Settlement::getCreatedAt))
            .orElse(null);

        return new SellerSettlementData(sellerId, items, pendingSettlement, periodStart, periodEnd);
    }

    private void init() {
        // yearMonth(예: 2026-03) 기준으로 정산 기간 계산
        // periodFrom = 전월 26일, periodTo = 해당월 25일
        YearMonth ym = YearMonth.parse(yearMonth);
        LocalDate periodFrom = ym.minusMonths(1).atDay(26);
        LocalDate periodTo = ym.atDay(25);
        this.periodStart = periodFrom.atStartOfDay();
        this.periodEnd = periodTo.atTime(23, 59, 59);

        Map<UUID, List<SettlementItem>> itemsBySeller = collectItemsBySeller(periodFrom, periodTo);
        includeCarryOverSellers(itemsBySeller);

        log.info("[MonthlySettlementReader] yearMonth={}, 대상 기간: {} ~ {}, 판매자: {}명",
            yearMonth, periodFrom, periodTo, itemsBySeller.size());
        this.sellerData = new ArrayList<>(itemsBySeller.entrySet());
    }

    private Map<UUID, List<SettlementItem>> collectItemsBySeller(LocalDate from, LocalDate to) {
        List<SettlementItem> items = settlementItemRepository
            .findByStatusAndEventDateTimeBetween(SettlementItemStatus.READY, from, to);
        log.info("[MonthlySettlementReader] READY 항목: {}건", items.size());
        return new HashMap<>(items.stream()
            .collect(Collectors.groupingBy(SettlementItem::getSellerId)));
    }

    private void includeCarryOverSellers(Map<UUID, List<SettlementItem>> itemsBySeller) {
        settlementRepository.findByStatus(SettlementStatus.PENDING_MIN_AMOUNT).stream()
            .filter(s -> s.getCarriedToSettlementId() == null)
            .map(Settlement::getSellerId)
            .distinct()
            .filter(sellerId -> !itemsBySeller.containsKey(sellerId))
            .forEach(sellerId -> itemsBySeller.put(sellerId, List.of()));
    }
}