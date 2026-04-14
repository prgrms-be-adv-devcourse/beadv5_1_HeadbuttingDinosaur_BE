package com.devticket.admin.infrastructure.external.client;

import com.devticket.admin.infrastructure.external.dto.res.InternalSettlementPageResponse;
import com.devticket.admin.presentation.dto.req.AdminSettlementSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Primary
@Component
@RequiredArgsConstructor
public class RestClientSettlementInternalClientImpl implements SettlementInternalClient {

    private final RestClient restClient;

    @Value("${internal.settlement-service.url}")
    private String settlementServerUrl;

    @Override
    public InternalSettlementPageResponse getSettlements(AdminSettlementSearchRequest condition) {
        return restClient.get()
            .uri(settlementServerUrl + "/internal/settlements?status={st}&sellerId={sid}&startDate={sd}&endDate={ed}&page={p}&size={z}",
                nvl(condition.status()), nvl(condition.sellerId()),
                nvl(condition.startDate()), nvl(condition.endDate()),
                condition.page() == null ? 0 : condition.page(),
                condition.size() == null ? 20 : condition.size())
            .retrieve()
            .body(InternalSettlementPageResponse.class);
    }

    @Override
    public void runSettlement() {
        restClient.post()
            .uri(settlementServerUrl + "/internal/settlements/run")
            .retrieve()
            .toBodilessEntity();
    }

    private static String nvl(String v) { return v == null ? "" : v; }
}