package com.devticket.payment.payment.infrastructure.client;

import com.devticket.payment.common.exception.BusinessException;
import com.devticket.payment.common.exception.CommonErrorCode;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderTicketsResponse;
import com.devticket.payment.wallet.infrastructure.client.dto.InternalEventOrdersResponse;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderItemInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class CommerceInternalClient {

    private final RestClient restClient;

    public CommerceInternalClient(
        @Value("${internal.commerce.base-url}") String baseUrl) {

        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)                          // ex) http://commerce-service:8080
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    public InternalOrderInfoResponse getOrderInfo(UUID orderId) {
        try {
            return restClient.get()
                .uri("/internal/orders/{orderId}", orderId)
                .retrieve()
                .body(InternalOrderInfoResponse.class);
        } catch (HttpServerErrorException e) {
            // 5xx
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public void completePayment(UUID orderId) {
        try {
            restClient.post()
                .uri("/internal/orders/{orderId}/payment-completed", orderId)
                .retrieve()
                .toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw e;
        }
    }

    public void failOrder(UUID orderId) {
        restClient.post()
            .uri("/internal/orders/{orderId}/payment-failed", orderId)
            .retrieve()
            .toBodilessEntity();
    }


    public InternalEventOrdersResponse getOrdersByEvent(UUID eventId) {
        log.info("[CommerceClient] 이벤트 주문 조회 — eventId={}", eventId);
        try {
            return restClient.get()
                .uri("/internal/orders/by-event/{eventId}?status=PAID", eventId)
                .retrieve()
                .body(InternalEventOrdersResponse.class);
        } catch (RestClientException e) {
            log.error("[CommerceClient] 이벤트 주문 조회 실패 — eventId={}, error={}",
                eventId, e.getMessage());
            throw new IllegalStateException("Commerce 서비스 호출 실패 — eventId=" + eventId, e);
        }
    }
      
    public InternalOrderItemInfoResponse getOrderItemInfoByTicketId(String ticketId) {
        return restClient.get()
            .uri("/internal/order-items/by-ticket/{ticketId}", ticketId)
            .retrieve()
            .body(InternalOrderItemInfoResponse.class);

    }

    public void completeRefund(String ticketId) {
        try {
            restClient.post()
                .uri("/internal/tickets/{ticketId}/refund-completed", ticketId)
                .retrieve()
                .toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw e;
        }
    }

    /**
     * 오더 전체 환불 시 사용할 orderId 내 ISSUED 상태 티켓 목록 조회.
     * Commerce 가 소유한 ticketId 의 "source of truth" 로 사용한다.
     */
    public InternalOrderTicketsResponse getIssuedTicketsByOrder(UUID orderId) {
        log.info("[CommerceClient] 발급 티켓 목록 조회 — orderId={}", orderId);
        try {
            return restClient.get()
                .uri("/internal/orders/{orderId}/tickets?status=ISSUED", orderId)
                .retrieve()
                .body(InternalOrderTicketsResponse.class);
        } catch (RestClientException e) {
            log.error("[CommerceClient] 발급 티켓 조회 실패 — orderId={}, error={}",
                orderId, e.getMessage());
            throw new IllegalStateException("Commerce 서비스 호출 실패 — orderId=" + orderId, e);
        }
    }
}