package com.devticket.settlement.infrastructure.client;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import com.devticket.settlement.infrastructure.client.dto.req.InternalBulkEventInfoRequest;
import com.devticket.settlement.infrastructure.client.dto.res.EndedEventResponse;
import com.devticket.settlement.infrastructure.client.dto.res.EventInfoResponse;
import com.devticket.settlement.infrastructure.client.dto.res.EventServiceResponse;
import com.devticket.settlement.infrastructure.client.dto.res.InternalBulkEventInfoData;
import com.devticket.settlement.infrastructure.client.dto.res.InternalEndedEventsData;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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

    /**
     * 이벤트 ID 목록으로 이벤트 정보를 한 번에 조회.
     * POST /internal/events/bulk
     * 응답: SuccessResponse<InternalBulkEventInfoResponse>
     *   → { status, message, data: { events: [ {eventId, title, ...}, ... ] } }
     *
     * 정산 응답에 eventTitle 을 채우기 위해 사용한다. 없는 ID 는 응답에서 누락될 수 있어
     * 호출 측에서 누락 ID 를 fallback 처리해야 한다.
     * 외부 호출 실패 시 빈 Map 을 반환하여 정산 조회 자체가 실패하지 않도록 한다.
     */
    public Map<UUID, String> getEventTitles(List<UUID> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UUID> distinctIds = eventIds.stream().distinct().toList();

        try {
            log.debug("[SettlementToEventClient] getEventTitles - count: {}", distinctIds.size());

            EventServiceResponse<InternalBulkEventInfoData> response = restClient.post()
                .uri("/internal/events/bulk")
                .body(new InternalBulkEventInfoRequest(distinctIds))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[SettlementToEventClient] Bulk API Error: Status {}", res.getStatusCode());
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(new ParameterizedTypeReference<EventServiceResponse<InternalBulkEventInfoData>>() {});

            if (response == null || response.data() == null || response.data().events() == null) {
                log.warn("[SettlementToEventClient] bulk 응답 데이터 없음 - count: {}", distinctIds.size());
                return Collections.emptyMap();
            }

            return response.data().events().stream()
                .filter(e -> e.eventId() != null && e.title() != null)
                .collect(Collectors.toMap(
                    EventInfoResponse::eventId,
                    EventInfoResponse::title,
                    (a, b) -> a
                ));
        } catch (Exception e) {
            // 정산 조회는 본질적으로 SettlementItem 데이터로 동작 가능하므로
            // 이벤트 제목 보강 실패가 전체 조회 실패로 이어지지 않도록 한다.
            log.warn("[SettlementToEventClient] getEventTitles 실패 - 빈 Map 으로 fallback: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
