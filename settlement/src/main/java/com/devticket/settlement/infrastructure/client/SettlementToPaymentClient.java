package com.devticket.settlement.infrastructure.client;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import com.devticket.settlement.infrastructure.client.dto.req.InternalSettlementDataRequest;
import com.devticket.settlement.infrastructure.client.dto.req.SettlementDepositRequest;
import com.devticket.settlement.infrastructure.client.dto.res.CommerceTicketSettlementResponse;
import com.devticket.settlement.infrastructure.client.dto.res.EventTicketSettlementResponse;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class SettlementToPaymentClient {

    private final RestClient restClient;

    public SettlementToPaymentClient(@Qualifier("settlementToPaymentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void transferToDeposit(UUID settlementId, UUID sellerId, int amount) {
        SettlementDepositRequest request = new SettlementDepositRequest(settlementId, sellerId, amount);
        log.info("[SettlementToCommerceClient] transferToDeposit - settlementId: {}, sellerId: {}, amount: {}",
            settlementId, sellerId, amount);
        try {
            String jsonRequest = new ObjectMapper().writeValueAsString(request);
            log.info("[DEBUG] Payment로 전송할 실제 JSON: {}", jsonRequest);
            restClient.post()
                .uri("/internal/wallet/settlement-deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[SettlementToCommerceClient] Deposit transfer failed: status={}", res.getStatusCode());
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .toBodilessEntity();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SettlementToCommerceClient] Deposit transfer critical error: ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }




}