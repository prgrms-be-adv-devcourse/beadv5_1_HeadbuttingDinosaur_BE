package com.devticket.commerce.cart.infrastructure.external.client;

import com.devticket.commerce.cart.domain.exception.CartErrorCode;
import com.devticket.commerce.cart.domain.exception.EventErrorCode;
import com.devticket.commerce.cart.infrastructure.external.client.dto.InternalPurchaseValidationResponse;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class EventClient {

    private final RestClient restClient;

    public EventClient(@Qualifier("eventRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    //장바구니 담기진행시 -> Event의 현재구매가능상태, 1인당 구매제한 수량 정보를 가져옵니다.
    public InternalPurchaseValidationResponse getValidateEventStatus(Long eventId, Long userId, Integer quantity) {
        try {
            log.info("[EventClient] Calling Internal API: eventId={}, userId={}", eventId, userId);

            InternalPurchaseValidationResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/internal/events/{eventId}/validate-purchase")
                    .queryParam("userId", userId)
                    .queryParam("quantity", quantity)
                    .build(eventId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[EventClient] Response Error: {} {}", res.getStatusCode(), res.getStatusText());
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(InternalPurchaseValidationResponse.class);

            // 💡 응답 객체가 null이 아닐 경우 상세 검증 수행
            if (response != null) {
                handlePurchaseValidationError(response);
            }

            return response;

        } catch (BusinessException e) {
            // 이미 정의된 비즈니스 예외는 그대로 던짐
            throw e;
        } catch (Exception e) {
            log.error("[EventClient] Critical Error: ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void handlePurchaseValidationError(InternalPurchaseValidationResponse response) {
        if (Boolean.TRUE.equals(response.purchasable())) {
            return;
        }

        // Event에서 반환된 reason값 기준 에러메세지
        switch (response.reason()) {
            case "SALE_ENDED" -> throw new BusinessException(CartErrorCode.EVENT_ENDED);
            case "SOLD_OUT", "INSUFFICIENT_STOCK" -> throw new BusinessException(CartErrorCode.OUT_OF_STOCK);
            case "EVENT_CANCELLED" -> throw new BusinessException(EventErrorCode.EVENT_ALREADY_CANCELLED);
            case "MAX_PER_USER_EXCEEDED" -> throw new BusinessException(CartErrorCode.EXCEED_MAX_PURCHASE);
            default -> {
                log.warn("[EventClient] Unknown validation reason: {}", response.reason());
                throw new BusinessException(EventErrorCode.INVALID_PURCHASE_REQUEST);
            }
        }
    }
}
