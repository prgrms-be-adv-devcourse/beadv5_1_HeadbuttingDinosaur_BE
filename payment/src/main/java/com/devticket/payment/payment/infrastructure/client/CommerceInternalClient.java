package com.devticket.payment.payment.infrastructure.client;

import com.devticket.payment.common.exception.BusinessException;
import com.devticket.payment.common.exception.CommonErrorCode;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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

    public InternalOrderInfoResponse getOrderInfo(String orderId) {
        try {
            return restClient.get()
                .uri("/internal/orders/{orderId}", orderId)
                .retrieve()
                .body(InternalOrderInfoResponse.class);
        }  catch (HttpServerErrorException e) {
            // 5xx
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public void completePayment(Long orderId) {
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
}
