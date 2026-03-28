package com.devticket.event.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devticket.event.application.EventService;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.fixture.EventTestFixture;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.EventListRequest;
import com.devticket.event.presentation.dto.EventListResponse;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import com.devticket.event.presentation.dto.SellerEventCreateResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
        UUID sellerId = UUID.randomUUID();
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

        UUID sellerId = UUID.randomUUID();

        // when & then
        mockMvc.perform(post("/api/v1/events")
                .header("X-User-Id", sellerId.toString())
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

    // 이벤트 목록 조회 및 검색 API 테스트

    @Test
    void 파라미터_없이_목록_조회시_기본_페이징이_적용되어_성공_응답을_반환한다() throws Exception {
        // given
        EventListResponse mockResponse = EventTestFixture.createEventListResponse();

        given(eventService.getEventList(any(EventListRequest.class), isNull(), any(Pageable.class)))
            .willReturn(mockResponse);

        // when (API 호출)
        mockMvc.perform(get("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));

        // then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(eventService).getEventList(any(EventListRequest.class), isNull(), pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageNumber()).isEqualTo(0);
        assertThat(capturedPageable.getPageSize()).isEqualTo(20);
    }

    @Test
    void 모든_필터_및_정렬_조건이_주어지면_정상적으로_매핑하여_호출한다() throws Exception {
        // given
        UUID testUserId = UUID.randomUUID();
        EventListResponse mockResponse = EventTestFixture.createEventListResponse();

        given(eventService.getEventList(any(EventListRequest.class), eq(testUserId), any(Pageable.class)))
            .willReturn(mockResponse);

        // when (API 호출: 새로 추가된 sellerId, status 파라미터와 X-User-Id 헤더까지 테스트)
        mockMvc.perform(get("/api/v1/events")
                .header("X-User-Id", testUserId.toString())
                .param("keyword", "스프링")
                .param("category", "MEETUP")
                .param("techStacks", "1", "2")
                .param("sellerId", testUserId.toString())
                .param("status", "DRAFT")
                .param("page", "1")
                .param("size", "10")
                .param("sort", "price,desc")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk());

        // then (DTO와 Pageable 캡처)
        ArgumentCaptor<EventListRequest> requestCaptor = ArgumentCaptor.forClass(EventListRequest.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(eventService).getEventList(requestCaptor.capture(), eq(testUserId), pageableCaptor.capture());

        // 1. DTO 바인딩 검증
        EventListRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.keyword()).isEqualTo("스프링");
        assertThat(capturedRequest.category()).isEqualTo(EventCategory.MEETUP);
        assertThat(capturedRequest.techStacks()).containsExactly(1L, 2L);
        assertThat(capturedRequest.sellerId()).isEqualTo(testUserId);
        assertThat(capturedRequest.status()).isEqualTo(EventStatus.DRAFT);

        // 2. 페이징 및 정렬 바인딩 검증
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageNumber()).isEqualTo(1);
        assertThat(capturedPageable.getPageSize()).isEqualTo(10);

        Sort.Order priceOrder = capturedPageable.getSort().getOrderFor("price");
        assertThat(priceOrder).isNotNull();
        assertThat(priceOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void 엣지_케이스_잘못된_카테고리_Enum값을_전달하면_400에러를_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/events")
                .param("category", "INVALID_CATEGORY") // 존재하지 않는 Enum
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }
}