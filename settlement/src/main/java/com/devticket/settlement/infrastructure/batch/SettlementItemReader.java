package com.devticket.settlement.infrastructure.batch;

import com.devticket.settlement.infrastructure.client.SettlementToCommerceClient;
import com.devticket.settlement.infrastructure.client.dto.req.InternalSettlementDataRequest;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementItemReader implements ItemReader<InternalSettlementDataResponse> {

    private final SettlementToCommerceClient settlementToCommerceClient;
    // read 플래그로 한 번만 읽기
    private boolean read = false;

    public void init() {
        this.read = false;
    }

    @Override
    public InternalSettlementDataResponse read() throws Exception {
        if (read) {
            return null;
        }
        // 저번 달 1일
        LocalDate firstDayOfLastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        // 저번 달 말일
        LocalDate lastDayOfLastMonth = LocalDate.now().withDayOfMonth(1).minusDays(1);
        // 날자 포매팅
        String periodStart = firstDayOfLastMonth.format(DateTimeFormatter.ISO_DATE);
        String periodEnd = lastDayOfLastMonth.format(DateTimeFormatter.ISO_DATE);

        // 전체 데이터 조회
        InternalSettlementDataRequest request = new InternalSettlementDataRequest(null, periodStart, periodEnd);
        InternalSettlementDataResponse response = settlementToCommerceClient.getSettlementData(request);

        read = true;
        return response;
    }
}
