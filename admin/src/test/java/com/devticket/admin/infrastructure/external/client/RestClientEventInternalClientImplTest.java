package com.devticket.admin.infrastructure.external.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

@DisplayName("RestClientEventInternalClientImpl")
class RestClientEventInternalClientImplTest {

    private static final String EVENT_SERVER_URL = "http://event:8082";

    private MockRestServiceServer server;
    private RestClientEventInternalClientImpl sut;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        sut = new RestClientEventInternalClientImpl(restClient);
        ReflectionTestUtils.setField(sut, "eventServerUrl", EVENT_SERVER_URL);
    }

    @Nested
    @DisplayName("forceCancel")
    class ForceCancel {

        @Test
        @DisplayName("PATCH 메서드와 reason 본문을 포함해 event-svc 의 internal 강제취소 엔드포인트를 호출한다")
        void shouldCallEventInternalForceCancelWithPatchAndReasonBody() {
            UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");

            server.expect(requestTo(EVENT_SERVER_URL + "/internal/events/" + eventId + "/force-cancel"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reason").value("관리자 강제 취소"))
                .andRespond(withNoContent());

            sut.forceCancel(eventId);

            server.verify();
        }

        @Test
        @DisplayName("event-svc 가 5xx 응답하면 HttpServerErrorException 을 그대로 전파한다")
        void shouldPropagateHttpServerErrorExceptionWhenEventReturns5xx() {
            UUID eventId = UUID.randomUUID();

            server.expect(requestTo(EVENT_SERVER_URL + "/internal/events/" + eventId + "/force-cancel"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withServerError());

            assertThatThrownBy(() -> sut.forceCancel(eventId))
                .isInstanceOf(HttpServerErrorException.class);

            server.verify();
        }

        @Test
        @DisplayName("event-svc 가 4xx 응답하면 HttpClientErrorException 을 그대로 전파한다")
        void shouldPropagateHttpClientErrorExceptionWhenEventReturns4xx() {
            UUID eventId = UUID.randomUUID();

            server.expect(requestTo(EVENT_SERVER_URL + "/internal/events/" + eventId + "/force-cancel"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

            assertThatThrownBy(() -> sut.forceCancel(eventId))
                .isInstanceOf(HttpClientErrorException.class);

            server.verify();
        }
    }
}
