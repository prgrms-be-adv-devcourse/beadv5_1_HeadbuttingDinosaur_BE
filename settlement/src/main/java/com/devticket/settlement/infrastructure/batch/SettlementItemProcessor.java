package com.devticket.settlement.infrastructure.batch;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementStatus;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SettlementItemProcessor implements ItemProcessor<InternalSettlementDataResponse, List<Settlement>> {

    // 수수료율 : 차후, feePolicy 테이블에서 가져 오도록 변경 예정
    private static final double FEE_RATE = 0.05;

    private UUID sellerId;

    public void setSellerId(UUID sellerId) {
        this.sellerId = sellerId;
    }

    @Override
    public List<Settlement> process(InternalSettlementDataResponse response) throws Exception {
        LocalDate firstDayOfLastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        LocalDate lastDayOfLastMonth = LocalDate.now().withDayOfMonth(1).minusDays(1);

        return response.eventSettlements().stream()
            .map(event -> {
                int feeAmount = (int) (event.totalSalesAmount() * FEE_RATE);
                int finalAmount = event.totalSalesAmount() - feeAmount - event.totalRefundAmount();

                if (finalAmount < 10000) {
                    log.warn("[SettlementItemProcessor] 최소 정산금액 미달 - eventId: {}", event.eventId());
                    return null;
                }

                return Settlement.builder()
                    .sellerId(response.sellerId()) // 응답에서 직접 가져오기
                    .periodStartAt(firstDayOfLastMonth.atStartOfDay())
                    .periodEndAt(lastDayOfLastMonth.atTime(23, 59, 59))
                    .totalSalesAmount(event.totalSalesAmount())
                    .totalRefundAmount(event.totalRefundAmount())
                    .totalFeeAmount(feeAmount)
                    .finalSettlementAmount(finalAmount)
                    .status(SettlementStatus.COMPLETED)
                    .settledAt(LocalDateTime.now())
                    .build();
            })
            .filter(s -> s != null)
            .toList();
    }
}