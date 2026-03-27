package com.devticket.event.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devticket.event.application.EventService;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.fixture.EventTestFixture;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import com.devticket.event.presentation.dto.SellerEventCreateResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private EventService eventService;

    // 이벤트 생성 테스트

    @Test
    void 정상적인_요청시_201_응답과_UUID를_반환한다() throws Exception {
        // given
        Long sellerId = 1L;
        UUID expectedEventId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        SellerEventCreateRequest request = EventTestFixture.createEventRequest(
            now.plusDays(4), now.plusDays(10), now.plusDays(15), 100, 4
        );

        SellerEventCreateResponse expectedResponse = new SellerEventCreateResponse(
            expectedEventId,
            EventStatus.DRAFT,
            now
        );

        when(eventService.createEvent(eq(sellerId), any(SellerEventCreateRequest.class)))
            .thenReturn(expectedResponse);

        // when & then
        mockMvc.perform(post("/api/v1/events")
                .header("X-User-Id", String.valueOf(sellerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.eventId").value(expectedEventId.toString()))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void X_User_Id_헤더가_누락되면_400_응답을_반환한다() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.now();
        SellerEventCreateRequest request = EventTestFixture.createEventRequest(
            now.plusDays(4), now.plusDays(10), now.plusDays(15), 100, 4
        );

        // when & then (헤더를 세팅하지 않음)
        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isBadRequest()); // 헤더 누락 에러 확인
    }

    @Test
    void DTO_필수값이_누락되거나_형식에_맞지않으면_400_응답을_반환한다() throws Exception {
        // given
        // 수량이 음수이거나 필수 문자열이 비어있는 비정상적인 DTO 생성
        SellerEventCreateRequest badRequest = new SellerEventCreateRequest(
            "", // 제목 누락 (@NotBlank 실패 유도)
            "설명", "강남역",
            LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
            -5000, // 가격 음수 (@Positive 실패 유도)
            100, 4, null, null, null
        );

        // when & then
        mockMvc.perform(post("/api/v1/events")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badRequest)))
            .andDo(print())
            .andExpect(status().isBadRequest()); // @Valid 에 의한 400 반환 확인
    }

    // 이벤트 상세 조회 테스트

    @Test
    void 이벤트_상세조회_성공시_200_응답을_반환한다() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        EventDetailResponse mockResponse = EventTestFixture.createEventDetailResponse(eventId);

        when(eventService.getEvent(eventId)).thenReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/v1/events/{eventId}", eventId)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk()) // 200 OK 검증
            .andExpect(jsonPath("$.data.eventId").value(eventId.toString()))
            .andExpect(jsonPath("$.data.title").value("상세 조회 테스트 밋업"));
    }
}