package com.devticket.settlement.infrastructure.client;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import com.devticket.settlement.infrastructure.client.dto.res.EndedEventResponse;
import com.devticket.settlement.infrastructure.client.dto.res.EventServiceResponse;
import com.devticket.settlement.infrastructure.client.dto.res.InternalEndedEventsData;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class SettlementToEventClient {

    private final RestClient restClient;

    public SettlementToEventClient(@Qualifier("settlementToEventRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 특정 날짜에 종료된 이벤트 목록 조회.
     * GET /internal/events/ended?date={date}
     * 응답: SuccessResponse<InternalEndedEventsResponse>
     *   → { status, message, data: { events: [ {id, eventId, sellerId}, ... ] } }
     */
    public List<EndedEventResponse> getEndedEvents(LocalDate date) {
        try {
            log.info("[SettlementToEventClient] getEndedEvents - date: {}", date);

            EventServiceResponse<InternalEndedEventsData> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/internal/events/ended")
                    .queryParam("date", date.toString())
                    .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[SettlementToEventClient] External API Error: Status {}", res.getStatusCode());
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(new ParameterizedTypeReference<EventServiceResponse<InternalEndedEventsData>>() {});

            if (response == null || response.data() == null || response.data().events() == null) {
                log.warn("[SettlementToEventClient] 응답 데이터 없음 - date: {}", date);
                return List.of();
            }

            log.info("[SettlementToEventClient] 종료된 이벤트 {}건 조회됨", response.data().events().size());
            return response.data().events();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SettlementToEventClient] Critical Error: ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}