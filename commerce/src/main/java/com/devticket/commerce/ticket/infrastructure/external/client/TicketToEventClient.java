package com.devticket.commerce.ticket.infrastructure.external.client;

import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.exception.CommonErrorCode;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalBulkEventInfoRequest;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalEventInfoResponse;
import java.util.List;
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

    public TicketToEventClient(@Qualifier("orderToEventRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // 이벤트 정보 조회(단건)
    public InternalEventInfoResponse getEventInfo(Long eventId) {
        try {
            log.info("[TicketToEventClient] getEventInfo - ID: {}", eventId);

            return restClient.get() // 단건 조희는 GET 권장 (서버 설정에 따라 PATCH/POST 가능)
                .uri("/internal/events/{eventId}", eventId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[TicketToEventClient] External API Error: Status {}", res.getStatusCode());
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(InternalEventInfoResponse.class);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TicketToEventClient] Critical Error (Single): ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 이벤트 정보 여러건 조회
    public List<InternalEventInfoResponse> getBulkEventInfo(InternalBulkEventInfoRequest request) {
        try {
            log.info("[TicketToEventClient] getBulkEventInfo - Size: {}", request.eventIds().size());

            return restClient.post() // 대량 조회 시 Body를 사용하므로 POST 혹은 PATCH 사용
                .uri("/internal/events/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[TicketToEventClient] External API Error: Status {}", res.getStatusCode());
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(new ParameterizedTypeReference<List<InternalEventInfoResponse>>() {
                });

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TicketToEventClient] Critical Error (Bulk): ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}

