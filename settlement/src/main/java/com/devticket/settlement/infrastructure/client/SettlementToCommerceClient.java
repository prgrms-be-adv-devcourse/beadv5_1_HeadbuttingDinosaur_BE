package com.devticket.settlement.infrastructure.client;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import com.devticket.settlement.infrastructure.client.dto.req.InternalSettlementDataRequest;
import com.devticket.settlement.infrastructure.client.dto.res.CommerceTicketSettlementResponse;
import com.devticket.settlement.infrastructure.client.dto.res.EventTicketSettlementResponse;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
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
public class SettlementToCommerceClient {

    private final RestClient restClient;

    public SettlementToCommerceClient(@Qualifier("settlementToCommerceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public InternalSettlementDataResponse getSettlementData(InternalSettlementDataRequest request) {
        try {
            log.info("[SettlementToCommerceClient] getSettlementData - sellerId: {}, period: {} ~ {}",
                request.sellerId(), request.periodStart(), request.periodEnd());

            return restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/internal/orders/settlement-data")
                    .queryParam("sellerId", request.sellerId())
                    .queryParam("periodStart", request.periodStart())
                    .queryParam("periodEnd", request.periodEnd())
                    .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[SettlementToCommerceClient] External API Error: Status {}", res.getStatusCode());
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(InternalSettlementDataResponse.class);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SettlementToCommerceClient] Critical Error: ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 여러 이벤트의 티켓 정산 데이터를 한 번에 조회.
     * POST /internal/tickets/settlement-data
     * Request body : [uuid1, uuid2, ...]
     * Response     : { "items": [ { eventId, orderItemId, salesAmount, refundAmount }, ... ] }
     */
    public List<EventTicketSettlementResponse> getTicketSettlementData(List<UUID> eventIds) {
        try {
            log.info("[SettlementToCommerceClient] getTicketSettlementData - eventIds 수: {}", eventIds.size());

            CommerceTicketSettlementResponse response = restClient.post()
                .uri("/internal/tickets/settlement-data")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(eventIds)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[SettlementToCommerceClient] Ticket API Error: Status {}", res.getStatusCode());
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(CommerceTicketSettlementResponse.class);

            if (response == null || response.items() == null) {
                log.warn("[SettlementToCommerceClient] 응답 데이터 없음");
                return List.of();
            }

            log.info("[SettlementToCommerceClient] 조회된 orderItem 건수: {}", response.items().size());
            return response.items();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SettlementToCommerceClient] Critical Error: ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}