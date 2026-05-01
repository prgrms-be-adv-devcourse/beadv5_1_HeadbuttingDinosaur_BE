package com.devticket.settlement.infrastructure.client;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class SettlementToMemberClient {

    private final RestClient restClient;

    public SettlementToMemberClient(@Qualifier("settlementToMemberRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public List<UUID> getSellerIds() {
        try {
            return restClient.get()
                .uri("/internal/members/sellers")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[SettlementToMemberClient] External API Error: Status {}", res.getStatusCode());
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(new ParameterizedTypeReference<List<UUID>>() {});
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SettlementToMemberClient] Critical Error: ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}