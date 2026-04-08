package com.devticket.commerce.order.infrastructure.external.client;

import com.devticket.commerce.cart.infrastructure.external.client.dto.EventSuccessResponse;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.exception.CommonErrorCode;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalBulkStockAdjustmentRequest;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalSellerEventsByPeriodRequest;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalStockAdjustmentResponse;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalStockAdjustmentWrapper;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalBulkEventInfoRequest;
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
public class OrderToEventClient {

    private final RestClient restClient;

    public OrderToEventClient(@Qualifier("orderToEventRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // 주문 생성,취소시 -> 여러건의 Event의 재고 차감,증감
    public List<InternalStockAdjustmentResponse> adjustStocks(InternalBulkStockAdjustmentRequest request) {
        try {
            var response = restClient.patch()
                .uri("/internal/events/stock-adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(new ParameterizedTypeReference<EventSuccessResponse<InternalStockAdjustmentWrapper>>() {
                });

            return response.data().results();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OrderToEventClient] Critical Error (adjustStocks): ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public List<InternalEventInfoResponse> getBulkEventInfo(List<UUID> eventIds) {
        try {
            var response = restClient.post()
                .uri("/internal/events/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InternalBulkEventInfoRequest(eventIds))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(new ParameterizedTypeReference<EventSuccessResponse<List<InternalEventInfoResponse>>>() {
                });
            return response.data();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OrderToEventClient] Critical Error (getBulkEventInfo): ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public List<InternalEventInfoResponse> getSellerEventsByPeriod(UUID sellerId, String periodStart,
        String periodEnd) {
        try {
            var response = restClient.post()
                .uri("/internal/events/seller-period")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InternalSellerEventsByPeriodRequest(sellerId, periodStart, periodEnd))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(new ParameterizedTypeReference<EventSuccessResponse<List<InternalEventInfoResponse>>>() {
                });
            return response.data();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OrderToEventClient] Critical Error (getSellerEventsByPeriod): ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

}

