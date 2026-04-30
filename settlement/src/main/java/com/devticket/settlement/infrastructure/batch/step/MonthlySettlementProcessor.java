package com.devticket.settlement.infrastructure.batch.step;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.model.SettlementStatus;
import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.infrastructure.batch.dto.SellerSettlementData;
import com.devticket.settlement.infrastructure.batch.dto.SettlementResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.ToLongFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlySettlementProcessor implements ItemProcessor<SellerSettlementData, SettlementResult> {

    private static final int MIN_SETTLEMENT_AMOUNT = 10_000;

    private final SettlementRepository settlementRepository;

    @Override
    public SettlementResult process(SellerSettlementData data) {
        UUID sellerId = data.sellerId();

        if (alreadySettled(sellerId, data.periodStart())) {
            log.info("[MonthlySettlementProcessor] 이미 처리된 기간 스킵 - sellerId={}", sellerId);
            return null;
        }

        long totalSales = sum(data.items(), SettlementItem::getSalesAmount);
        long totalRefund = sum(data.items(), SettlementItem::getRefundAmount);
        long totalFee = sum(data.items(), SettlementItem::getFeeAmount);
        long settlementAmount = sum(data.items(), SettlementItem::getSettlementAmount);

        int carriedInAmount = data.pendingSettlement() != null
            ? data.pendingSettlement().getFinalSettlementAmount()
            : 0;
        long finalAmount = settlementAmount + carriedInAmount;

        SettlementStatus status = finalAmount >= MIN_SETTLEMENT_AMOUNT
            ? SettlementStatus.CONFIRMED
            : SettlementStatus.PENDING_MIN_AMOUNT;

        Settlement settlement = Settlement.builder()
            .sellerId(sellerId)
            .periodStartAt(data.periodStart())
            .periodEndAt(data.periodEnd())
            .totalSalesAmount((int) totalSales)
            .totalRefundAmount((int) totalRefund)
            .totalFeeAmount((int) totalFee)
            .finalSettlementAmount((int) finalAmount)
            .carriedInAmount(carriedInAmount)
            .status(status)
            .settledAt(LocalDateTime.now())
            .build();

        log.info("[MonthlySettlementProcessor] sellerId={}, items={}건, carriedIn={}, finalAmount={}, status={}",
            sellerId, data.items().size(), carriedInAmount, finalAmount, status);

        return new SettlementResult(settlement, data.pendingSettlement(), data.items());
    }

    private boolean alreadySettled(UUID sellerId, LocalDateTime periodStart) {
        return settlementRepository.existsBySellerIdAndPeriodStartAtBetweenAndStatusNot(
            sellerId,
            periodStart,
            periodStart.toLocalDate().atTime(23, 59, 59),
            SettlementStatus.CANCELLED
        );
    }

    private long sum(List<SettlementItem> items, ToLongFunction<SettlementItem> mapper) {
        return items.stream().mapToLong(mapper).sum();
    }
}