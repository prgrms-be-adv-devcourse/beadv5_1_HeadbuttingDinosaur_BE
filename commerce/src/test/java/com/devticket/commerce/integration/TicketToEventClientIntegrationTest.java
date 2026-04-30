package com.devticket.commerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.devticket.commerce.common.enums.EventStatus;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.ticket.infrastructure.external.client.TicketToEventClient;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalBulkEventInfoRequest;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalEventInfoResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * IT-#672: event 서비스 응답의 EventStatus 역직렬화 통합 검증.
 *
 * <p>운영 장애(2026-04-30) 회귀 방지: event가 새 enum 값 "ENDED"를 추가했으나
 * commerce가 동기화되지 않아 RestClient 응답 역직렬화 시 InvalidFormatException 발생,
 * /internal/events/{id} 및 /internal/events/bulk 호출이 500으로 실패한 사례 재현·방지.
 *
 * <p>검증 레이어: RestClient → Jackson → DTO record → EventStatus enum (실제 버그가 발생한 경로 그대로).
 */
@DisplayName("IT-#672: TicketToEventClient EventStatus 역직렬화")
class TicketToEventClientIntegrationTest {

    private static final String BASE_URL = "http://localhost:8082";

    private MockRestServiceServer mockServer;
    private TicketToEventClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl(BASE_URL)
            .defaultHeader("Content-Type", "application/json");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new TicketToEventClient(builder.build());
    }

    @Test
    @DisplayName("IT-#672-A: 단건 응답 status=ENDED 정상 역직렬화 (회귀 방지)")
    void getSingleEventInfo_decodesEndedStatus() {
        UUID eventId = UUID.randomUUID();
        String responseBody = singleEventResponseBody(eventId, "ENDED");

        mockServer.expect(requestTo(BASE_URL + "/internal/events/" + eventId))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        InternalEventInfoResponse response = client.getSingleEventInfo(eventId);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.status()).isEqualTo(EventStatus.ENDED);
        mockServer.verify();
    }

    @Test
    @DisplayName("IT-#672-B: 벌크 응답에 모든 EventStatus 값 포함 시 정상 역직렬화 (신규 enum 누락 회귀 방지)")
    void getBulkEventInfo_decodesAllEventStatusValues() {
        List<EventStatus> all = Arrays.asList(EventStatus.values());
        List<UUID> eventIds = all.stream().map(s -> UUID.randomUUID()).toList();

        String dataArray = java.util.stream.IntStream.range(0, all.size())
            .mapToObj(i -> singleEventDataNode(eventIds.get(i), all.get(i).name()))
            .collect(Collectors.joining(","));
        String responseBody = """
            {
              "status": 200,
              "message": "ok",
              "data": { "events": [%s] }
            }
            """.formatted(dataArray);

        mockServer.expect(requestTo(BASE_URL + "/internal/events/bulk"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        List<InternalEventInfoResponse> events =
            client.getBulkEventInfo(new InternalBulkEventInfoRequest(eventIds));

        assertThat(events).hasSize(all.size());
        assertThat(events).extracting(InternalEventInfoResponse::status)
            .containsExactlyElementsOf(all);
        mockServer.verify();
    }

    @Test
    @DisplayName("IT-#672-C: event가 commerce에 없는 신규 enum 값을 내려보내면 BusinessException으로 매핑")
    void getSingleEventInfo_unknownStatusValue_isMappedToBusinessException() {
        UUID eventId = UUID.randomUUID();
        String responseBody = singleEventResponseBody(eventId, "FUTURE_UNKNOWN_STATUS");

        mockServer.expect(requestTo(BASE_URL + "/internal/events/" + eventId))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getSingleEventInfo(eventId))
            .isInstanceOf(BusinessException.class);
        mockServer.verify();
    }

    private String singleEventResponseBody(UUID eventId, String status) {
        return """
            {
              "status": 200,
              "message": "ok",
              "data": %s
            }
            """.formatted(singleEventDataNode(eventId, status));
    }

    private String singleEventDataNode(UUID eventId, String status) {
        return """
            {
              "eventId": "%s",
              "sellerId": "%s",
              "title": "테스트 이벤트",
              "price": 10000,
              "status": "%s",
              "category": "CONFERENCE",
              "totalQuantity": 100,
              "maxQuantity": 5,
              "remainingQuantity": 0,
              "eventDateTime": "2026-04-30T10:00:00",
              "saleStartAt": "2026-04-01T00:00:00",
              "saleEndAt": "2026-04-29T23:59:59"
            }
            """.formatted(eventId, UUID.randomUUID(), status);
    }
}
