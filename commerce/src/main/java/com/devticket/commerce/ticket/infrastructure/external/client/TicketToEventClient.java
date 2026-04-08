package com.devticket.commerce.ticket.infrastructure.external.client;

import com.devticket.commerce.cart.infrastructure.external.client.dto.EventSuccessResponse;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.exception.CommonErrorCode;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalBulkEventInfoRequest;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalBulkEventInfoResponse;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalEventInfoResponse;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class TicketToEventClient {

    private final RestClient restClient;

    public TicketToEventClient(@Qualifier("ticketToEventRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // 이벤트 정보 조회(단건)
    public InternalEventInfoResponse getSingleEventInfo(UUID eventId) {
        try {
            var response = restClient.get()
                .uri("/internal/events/{eventId}", eventId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(new ParameterizedTypeReference<EventSuccessResponse<InternalEventInfoResponse>>() {
                });
            return response.data();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TicketToEventClient] Critical Error (Single): ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public List<InternalEventInfoResponse> getBulkEventInfo(InternalBulkEventInfoRequest request) {
        try {
            var response = restClient.post()
                .uri("/internal/events/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(new ParameterizedTypeReference<EventSuccessResponse<InternalBulkEventInfoResponse>>() {
                });
            return response.data().events();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TicketToEventClient] Critical Error (Bulk): ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

}

