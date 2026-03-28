package com.devticket.commerce.order.infrastructure.external.client;

import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.exception.CommonErrorCode;
import com.devticket.commerce.mock.controller.dto.InternalStockAdjustmentRequest;
import com.devticket.commerce.mock.controller.dto.InternalStockAdjustmentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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

    //주문 생성,취소시 -> Event의 재고 차감,증감
    public InternalStockAdjustmentResponse adjustStock(Long eventId, InternalStockAdjustmentRequest request) {
        try {
            log.info("[EventClient] Adjusting stock : eventId={}, delta={}", eventId, request.quantityDelta());

            return restClient.patch()
                .uri("/internal/events/{eventId}/stock", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[EventClient] API Error: {} {}", res.getStatusCode(), res.getStatusText());
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(InternalStockAdjustmentResponse.class);


        } catch (BusinessException e) {
            // 이미 정의된 비즈니스 예외는 그대로 던짐
            throw e;
        } catch (Exception e) {
            // 연결 실패, 타임아웃 등 시스템적 장애 처리
            log.error("[EventClient] Critical Error: ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }


}

