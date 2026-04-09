package com.devticket.settlement.infrastructure.batch;

import com.devticket.settlement.infrastructure.client.SettlementToCommerceClient;
import com.devticket.settlement.infrastructure.client.SettlementToMemberClient;
import com.devticket.settlement.infrastructure.client.dto.req.InternalSettlementDataRequest;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementItemReader implements ItemReader<InternalSettlementDataResponse> {

    private final SettlementToCommerceClient settlementToCommerceClient;
    private final SettlementToMemberClient settlementToMemberClient;

    private List<UUID> sellerIds;
    private int index = 0;


    public void init() {
        this.sellerIds = settlementToMemberClient.getSellerIds();
        this.index = 0;
    }

    @Override
    public InternalSettlementDataResponse read() throws Exception {
        if (sellerIds == null || index >= sellerIds.size()){
            return null;
        }
        // 저번 달 1일
        LocalDate firstDayOfLastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        // 저번 달 말일
        LocalDate lastDayOfLastMonth = LocalDate.now().withDayOfMonth(1).minusDays(1);
        // 날자 포매팅
        String periodStart = firstDayOfLastMonth.format(DateTimeFormatter.ISO_DATE);
        String periodEnd = lastDayOfLastMonth.format(DateTimeFormatter.ISO_DATE);

        // 인덱스 추가
        UUID sellerId = sellerIds.get(index++);

        // 전체 데이터 조회
        InternalSettlementDataRequest request = new InternalSettlementDataRequest(sellerId, periodStart, periodEnd);
        return settlementToCommerceClient.getSettlementData(request);
    }
}
