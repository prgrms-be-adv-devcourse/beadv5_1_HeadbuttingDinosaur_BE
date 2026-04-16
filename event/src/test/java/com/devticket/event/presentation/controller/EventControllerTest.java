package com.devticket.event.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.devticket.event.presentation.dto.SellerEventDetailResponse;
import com.devticket.event.presentation.dto.SellerEventSummaryResponse;
import com.devticket.event.presentation.dto.SellerEventUpdateRequest;
import com.devticket.event.presentation.dto.SellerEventUpdateResponse;
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

@WebMvcTest({EventController.class, SellerEventController.class})
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
            sellerId,
            EventStatus.ON_SALE,
            now
        );

        when(eventService.createEvent(eq(sellerId), any(SellerEventCreateRequest.class)))
            .thenReturn(expectedResponse);

        // when & then
        mockMvc.perform(post("/api/seller/events")
                .header("X-User-Id", String.valueOf(sellerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.eventId").value(expectedEventId.toString()))
            .andExpect(jsonPath("$.data.status").value("ON_SALE"))
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
        mockMvc.perform(post("/api/seller/events")
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
        mockMvc.perform(post("/api/seller/events")
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
        mockMvc.perform(get("/api/events/{eventId}", eventId)
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
        mockMvc.perform(get("/api/events")
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

        // when (API 호출: sellerId, status 파라미터와 X-User-Id 헤더까지 테스트)
        mockMvc.perform(get("/api/events")
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
        mockMvc.perform(get("/api/events")
                .param("category", "INVALID_CATEGORY") // 존재하지 않는 Enum
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    // 판매자 등록 이벤트 목록 조회 테스트

    @Test
    void 헤더없이_일반목록_조회_파라미터_전달() throws Exception {
        // given
        EventListResponse mockResponse = EventTestFixture.createEventListResponse();

        // currentUserId 자리에 isNull() 매칭
        when(eventService.getEventList(any(EventListRequest.class), isNull(), any(Pageable.class)))
            .thenReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/events")
                .param("keyword", "스프링")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));

        // verify
        ArgumentCaptor<EventListRequest> requestCaptor = ArgumentCaptor.forClass(EventListRequest.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(eventService).getEventList(requestCaptor.capture(), isNull(), pageableCaptor.capture());

        assertThat(requestCaptor.getValue().keyword()).isEqualTo("스프링");
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20); // @PageableDefault 기본값
    }

    @Test
    void 판매자_본인이벤트_조회_파라미터_바인딩() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        EventListResponse mockResponse = EventTestFixture.createEventListResponse();

        // 헤더 값(sellerId)이 서비스의 두 번째 파라미터로 전달되는지 eq()로 매칭
        when(eventService.getEventList(any(EventListRequest.class), eq(sellerId), any(Pageable.class)))
            .thenReturn(mockResponse);

        // when & then (sellerId, status 포함한 통합 테스트)
        mockMvc.perform(get("/api/events")
                .header("X-User-Id", sellerId.toString())
                .param("sellerId", sellerId.toString())
                .param("status", "DRAFT")
                .param("category", "MEETUP")
                .param("techStacks", "1", "2")
                .param("page", "1")
                .param("size", "10")
                .param("sort", "price,desc")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk());

        // verify
        ArgumentCaptor<EventListRequest> requestCaptor = ArgumentCaptor.forClass(EventListRequest.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(eventService).getEventList(requestCaptor.capture(), eq(sellerId), pageableCaptor.capture());

        // 1. DTO 필드 검증
        EventListRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.sellerId()).isEqualTo(sellerId);
        assertThat(capturedRequest.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(capturedRequest.category()).isEqualTo(EventCategory.MEETUP);
        assertThat(capturedRequest.techStacks()).containsExactly(1L, 2L);

        // 2. 페이징 및 정렬 검증
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageNumber()).isEqualTo(1);
        assertThat(capturedPageable.getPageSize()).isEqualTo(10);
        assertThat(capturedPageable.getSort().getOrderFor("price").getDirection())
            .isEqualTo(Sort.Direction.DESC);
    }

    // 판매자 이벤트 상세 조회 테스트

    @Test
    void 판매자_이벤트_상세조회_성공시_200_응답과_상세정보를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        SellerEventDetailResponse mockResponse = EventTestFixture.createSellerEventDetailResponse(eventId);

        when(eventService.getSellerEventDetail(eq(sellerId), eq(eventId))).thenReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/seller/events/{eventId}", eventId)
                .header("X-User-Id", sellerId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.eventId").value(eventId.toString()))
            .andExpect(jsonPath("$.data.title").value("상세 조회 테스트 밋업"))
            .andExpect(jsonPath("$.data.techStacks").isArray())
            .andExpect(jsonPath("$.data.imageUrls").isArray());
    }

    @Test
    void 판매자_이벤트_상세조회시_X_User_Id_헤더가_없으면_400_응답을_반환한다() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();

        // when & then (헤더 미포함)
        mockMvc.perform(get("/api/seller/events/{eventId}", eventId)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    // 판매자 이벤트 현황 조회 테스트

    @Test
    void 판매자_이벤트_현황조회_성공시_200_응답과_현황정보를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        SellerEventSummaryResponse mockResponse = EventTestFixture.createSellerEventSummaryResponse(eventId);

        when(eventService.getEventSummary(eq(sellerId), eq(eventId))).thenReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/seller/events/{eventId}/statistics", eventId)
                .header("X-User-Id", sellerId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.eventId").value(eventId.toString()))
            .andExpect(jsonPath("$.data.totalQuantity").value(100))
            .andExpect(jsonPath("$.data.soldQuantity").value(20))
            .andExpect(jsonPath("$.data.remainingQuantity").value(80));
    }

    @Test
    void 판매자_이벤트_현황조회시_X_User_Id_헤더가_없으면_400_응답을_반환한다() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();

        // when & then (헤더 미포함)
        mockMvc.perform(get("/api/seller/events/{eventId}/statistics", eventId)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    // 이벤트 수정 및 판매 중지 테스트

    @Test
    void 정상_판매_중지_요청_성공() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        SellerEventUpdateRequest cancelRequest = EventTestFixture.createUpdateEventRequest_Cancel();
        SellerEventUpdateResponse expectedResponse = EventTestFixture.createSellerEventUpdateResponse(eventId, EventStatus.CANCELLED);

        // ArgumentCaptor로 요청 필드 검증
        ArgumentCaptor<SellerEventUpdateRequest> requestCaptor = ArgumentCaptor.forClass(SellerEventUpdateRequest.class);
        when(eventService.updateEvent(eq(sellerId), eq(eventId), requestCaptor.capture()))
            .thenReturn(expectedResponse);

        // when & then
        mockMvc.perform(patch("/api/seller/events/{eventId}", eventId)
                .header("X-User-Id", sellerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancelRequest)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.eventId").value(eventId.toString()))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // 요청 바디 검증
        SellerEventUpdateRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void 판매_중지_X_User_Id_헤더_누락_실패() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        SellerEventUpdateRequest cancelRequest = EventTestFixture.createUpdateEventRequest_Cancel();

        // when & then (헤더 미포함)
        mockMvc.perform(patch("/api/seller/events/{eventId}", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancelRequest)))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    void 정상_이벤트_수정_요청_성공() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        SellerEventUpdateRequest updateRequest = EventTestFixture.createUpdateEventRequest();
        SellerEventUpdateResponse expectedResponse = EventTestFixture.createSellerEventUpdateResponse(eventId, EventStatus.DRAFT);

        // ArgumentCaptor로 요청 필드 검증
        ArgumentCaptor<SellerEventUpdateRequest> requestCaptor = ArgumentCaptor.forClass(SellerEventUpdateRequest.class);
        when(eventService.updateEvent(eq(sellerId), eq(eventId), requestCaptor.capture()))
            .thenReturn(expectedResponse);

        // when & then
        mockMvc.perform(patch("/api/seller/events/{eventId}", eventId)
                .header("X-User-Id", sellerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.eventId").value(eventId.toString()))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));

        // 요청 바디 JSON 바인딩 검증
        SellerEventUpdateRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.title()).isEqualTo(updateRequest.title());
        assertThat(capturedRequest.price()).isEqualTo(updateRequest.price());
        assertThat(capturedRequest.totalQuantity()).isEqualTo(updateRequest.totalQuantity());
        assertThat(capturedRequest.maxQuantity()).isEqualTo(updateRequest.maxQuantity());
        assertThat(capturedRequest.saleStartAt()).isEqualTo(updateRequest.saleStartAt());
    }

    @Test
    void 이벤트_수정_X_User_Id_헤더_누락_실패() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        SellerEventUpdateRequest updateRequest = EventTestFixture.createUpdateEventRequest();

        // when & then (헤더 미포함)
        mockMvc.perform(patch("/api/seller/events/{eventId}", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

}
