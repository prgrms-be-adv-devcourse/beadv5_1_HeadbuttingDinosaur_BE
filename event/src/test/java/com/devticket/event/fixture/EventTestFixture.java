package com.devticket.event.fixture;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.EventListResponse;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import com.devticket.event.presentation.dto.SellerEventDetailResponse;
import com.devticket.event.presentation.dto.SellerEventSummaryResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

public class EventTestFixture {

    // static 메서드로 만들어서 어디서든 클래스명으로 쉽게 호출할 수 있게 합니다.
    public static SellerEventCreateRequest createEventRequest(
        LocalDateTime saleStart, LocalDateTime saleEnd, LocalDateTime eventDate,
        int totalQty, int maxQty) {
        return new SellerEventCreateRequest(
            "Spring Boot 3.x 심화 밋업", "설명", "강남역",
            eventDate, saleStart, saleEnd,
            50000, totalQty, maxQty, EventCategory.MEETUP,
            List.of(1L, 2L),
            List.of("url1")
        );
    }

    public static Event createEvent(UUID sellerId) {
        return Event.create(
            sellerId, "상세 조회 테스트 밋업", "설명", "강남역",
            LocalDateTime.now().plusDays(15), LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(10),
            50000, 100, 4, EventCategory.MEETUP
        );
    }

    public static EventDetailResponse createEventDetailResponse(UUID eventId) {
        return new EventDetailResponse(
            eventId, "상세 조회 테스트 밋업", "설명", "강남역",
            LocalDateTime.now().plusDays(15), LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(10),
            50000, 100, 4, EventCategory.MEETUP
        );
    }

    // Page<Event>를 반환하는 모의(Mock) 객체 생성 메서드
    public static Page<Event> createEventPage() {
        Event event = createEvent(UUID.randomUUID());
        ReflectionTestUtils.setField(event, "eventId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "createdAt", LocalDateTime.now());

        // 1개의 데이터가 들어있는 첫 번째 페이지(0) 반환
        return new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1);
    }

    // Controller 테스트용 EventListResponse 생성 메서드
    public static EventListResponse createEventListResponse() {
        return EventListResponse.of(createEventPage());
    }

    // 판매자 이벤트 상세 조회 응답 생성
    public static SellerEventDetailResponse createSellerEventDetailResponse(UUID eventId) {
        return new SellerEventDetailResponse(
            eventId,
            "상세 조회 테스트 밋업",
            "설명",
            "강남역",
            LocalDateTime.now().plusDays(15),
            LocalDateTime.now().plusDays(4),
            LocalDateTime.now().plusDays(10),
            50000,
            100,
            80,
            4,
            EventStatus.DRAFT,
            EventCategory.MEETUP,
            List.of(new SellerEventDetailResponse.TechStackInfo(1L, "Spring")),
            List.of("url1"),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    // 판매자 이벤트 현황 조회 응답 생성
    public static SellerEventSummaryResponse createSellerEventSummaryResponse(UUID eventId) {
        return new SellerEventSummaryResponse(
            eventId,
            "상세 조회 테스트 밋업",
            EventStatus.ON_SALE,
            100,
            20,
            80,
            LocalDateTime.now().plusDays(10),
            LocalDateTime.now().plusDays(15)
        );
    }
}