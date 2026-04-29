package com.devticket.event.presentation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devticket.event.application.event.ActionLogDomainEvent;
import com.devticket.event.common.config.JacksonConfig;
import com.devticket.event.common.exception.GlobalExceptionHandler;
import com.devticket.event.common.messaging.event.ActionType;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DwellController Bean Validation 검증.
 *
 * audit-report 2026-04-28 P0 #3 — CLAUDE.md "Producer Bean Validation = 최종 방어선"
 * 명시. action.log 토픽은 acks=0 + Consumer dedup 미적용 정책이라
 * 잘못된 페이로드가 들어오면 사후 정정 불가 → 컨트롤러에서 선차단해야 한다.
 *
 * 검증 대상: dwellTimeSeconds 의 @NotNull / @Positive + 비로그인 publishEvent skip.
 *
 * Spring Boot 4 / Spring 7: ApplicationContext 자체가 ApplicationEventPublisher
 * resolvableDependency 로 등록되어 있어 @MockitoBean 으로는 컨트롤러 주입을 가로챌 수 없다.
 * @RecordApplicationEvents 로 실제 publisher 를 사용하면서 발행 이벤트를 수집한다.
 */
@WebMvcTest({DwellController.class, GlobalExceptionHandler.class, JacksonConfig.class})
@RecordApplicationEvents
@DisplayName("DwellController — Bean Validation + 비로그인 발행 skip")
class DwellControllerTest {

    private static final String DWELL_PATH = "/api/events/{eventId}/dwell";

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationEvents events;

    @Nested
    @DisplayName("정상 케이스")
    class HappyPath {

        @Test
        @DisplayName("로그인 사용자 정상 요청 시 204 응답 + ActionLogDomainEvent 발행")
        void loggedInUser_validRequest_publishesEvent() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();

            mockMvc.perform(post(DWELL_PATH, eventId)
                    .header("X-User-Id", userId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"dwellTimeSeconds\":60}"))
                .andExpect(status().isNoContent());

            Assertions.assertThat(events.stream(ActionLogDomainEvent.class))
                .singleElement()
                .satisfies(published -> {
                    Assertions.assertThat(published.userId()).isEqualTo(userId);
                    Assertions.assertThat(published.eventId()).isEqualTo(eventId);
                    Assertions.assertThat(published.actionType()).isEqualTo(ActionType.DWELL_TIME);
                    Assertions.assertThat(published.dwellTimeSeconds()).isEqualTo(60);
                    Assertions.assertThat(published.searchKeyword()).isNull();
                    Assertions.assertThat(published.stackFilter()).isNull();
                    Assertions.assertThat(published.quantity()).isNull();
                    Assertions.assertThat(published.totalAmount()).isNull();
                    Assertions.assertThat(published.timestamp()).isNotNull();
                });
        }

        @Test
        @DisplayName("비로그인(X-User-Id 헤더 없음) 요청 시 204 응답 + 발행 skip")
        void anonymousUser_returnsNoContentWithoutPublishing() throws Exception {
            UUID eventId = UUID.randomUUID();

            mockMvc.perform(post(DWELL_PATH, eventId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"dwellTimeSeconds\":30}"))
                .andExpect(status().isNoContent());

            Assertions.assertThat(events.stream(ActionLogDomainEvent.class)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Bean Validation 위반")
    class ValidationFailure {

        @Test
        @DisplayName("dwellTimeSeconds 가 null 이면 400 + 발행 skip (@NotNull 위반)")
        void nullDwellTimeSeconds_returnsBadRequest() throws Exception {
            UUID eventId = UUID.randomUUID();

            mockMvc.perform(post(DWELL_PATH, eventId)
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"dwellTimeSeconds\":null}"))
                .andExpect(status().isBadRequest());

            Assertions.assertThat(events.stream(ActionLogDomainEvent.class)).isEmpty();
        }

        @Test
        @DisplayName("dwellTimeSeconds == 0 이면 400 + 발행 skip (@Positive 위반)")
        void zeroDwellTimeSeconds_returnsBadRequest() throws Exception {
            UUID eventId = UUID.randomUUID();

            mockMvc.perform(post(DWELL_PATH, eventId)
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"dwellTimeSeconds\":0}"))
                .andExpect(status().isBadRequest());

            Assertions.assertThat(events.stream(ActionLogDomainEvent.class)).isEmpty();
        }

        @Test
        @DisplayName("dwellTimeSeconds < 0 이면 400 + 발행 skip (@Positive 위반)")
        void negativeDwellTimeSeconds_returnsBadRequest() throws Exception {
            UUID eventId = UUID.randomUUID();

            mockMvc.perform(post(DWELL_PATH, eventId)
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"dwellTimeSeconds\":-5}"))
                .andExpect(status().isBadRequest());

            Assertions.assertThat(events.stream(ActionLogDomainEvent.class)).isEmpty();
        }

        @Test
        @DisplayName("body 누락 시 400 + 발행 skip")
        void emptyBody_returnsBadRequest() throws Exception {
            UUID eventId = UUID.randomUUID();

            mockMvc.perform(post(DWELL_PATH, eventId)
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest());

            Assertions.assertThat(events.stream(ActionLogDomainEvent.class)).isEmpty();
        }
    }
}
