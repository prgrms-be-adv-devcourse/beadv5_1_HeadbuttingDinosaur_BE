package com.devticket.commerce.cart.infrastructure.external.client;

import com.devticket.commerce.cart.infrastructure.external.client.dto.InternalPurchaseValidationResponse;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.exception.CommonErrorCode;
import java.util.UUID;
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
<<<<<<< Updated upstream
    public InternalPurchaseValidationResponse getValidateEventStatus(Long eventId, Long userId, Integer quantity) {
=======
    public InternalPurchaseValidationResponse getValidateEventStatus(Long eventId, UUID userId, Integer quantity) {

>>>>>>> Stashed changes
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

            return response;

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
