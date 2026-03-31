package com.devticket.commerce.order.infrastructure.external.client;

import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.exception.CommonErrorCode;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalBulkStockAdjustmentRequest;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalStockAdjustmentResponse;
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
public class OrderToEventClient {

    private final RestClient restClient;

    public OrderToEventClient(@Qualifier("orderToEventRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // 주문 생성,취소시 -> 여러건의 Event의 재고 차감,증감
    public List<InternalStockAdjustmentResponse> adjustStocks(InternalBulkStockAdjustmentRequest request) {
        try {
            // [추가] 보낼 데이터의 내용을 구체적으로 로깅 (JSON 구조 확인용)
            log.info("[OrderToEventClient] Bulk Request Payload: {}", request);

            return restClient.patch()
                .uri("/internal/events/stock-adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    String errorBody = res.toString();
                    log.error("[OrderToEventClient] API Error Status: {} {}", res.getStatusCode(), res.getStatusText());
                    log.error("[OrderToEventClient] API Error Body: {}", errorBody); // 이게 500 에러의 핵심 단서입니다.

                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(new ParameterizedTypeReference<List<InternalStockAdjustmentResponse>>() {
                });

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OrderToEventClient] Critical Error (Network/Mapping): ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 이벤트 정보 여러건 조회 (이벤트 타이틀 등)
    public List<InternalEventInfoResponse> getBulkEventInfo(List<Long> eventIds) {
        try {
            log.info("[OrderToEventClient] getBulkEventInfo - eventIds: {}", eventIds);

            return restClient.post()
                .uri("/internal/events/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InternalBulkEventInfoRequest(eventIds))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[OrderToEventClient] API Error Status: {} {}", res.getStatusCode(), res.getStatusText());
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(new ParameterizedTypeReference<List<InternalEventInfoResponse>>() {});

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OrderToEventClient] Critical Error (getBulkEventInfo): ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

}

