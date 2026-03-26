package com.devticket.event.fixture;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.model.Event;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class EventTestFixture {

    // static 메서드로 만들어서 어디서든 클래스명으로 쉽게 호출할 수 있게 합니다.
    public static SellerEventCreateRequest createEventRequest(
        LocalDateTime saleStart, LocalDateTime saleEnd, LocalDateTime eventDate,
        int totalQty, int maxQty) {
        return new SellerEventCreateRequest(
            "Spring Boot 3.x 심화 밋업", "설명", "강남역",
            eventDate, saleStart, saleEnd,
            50000, totalQty, maxQty, EventCategory.MEETUP,
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            List.of("url1")
        );
    }

    public static Event createEvent(Long sellerId) {
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
}