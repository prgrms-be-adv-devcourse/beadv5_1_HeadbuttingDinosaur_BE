package com.devticket.event.fixture;

import com.devticket.event.domain.model.EventCategory;
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
}