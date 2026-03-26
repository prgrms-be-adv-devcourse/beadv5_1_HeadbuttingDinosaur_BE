package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.model.Event;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventDetailResponse(
    UUID eventId,
    String title,
    String description,
    String location,
    LocalDateTime eventDateTime,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt,
    Integer price,
    Integer totalQuantity,
    Integer maxQuantity,
    EventCategory category
    // TODO: ERD에 따라 TechStack과 ImageUrl 리스트가 엔티티에 매핑되어 있다면
    // List<UUID> techStackIds, List<String> imageUrls 필드를 추가해 주세요!
) {
    // Entity를 DTO로 변환해주는 정적 팩토리 메서드 (포장지 역할)
    public static EventDetailResponse from(Event event) {
        return new EventDetailResponse(
            event.getEventId(),
            event.getTitle(),
            event.getDescription(),
            event.getLocation(),
            event.getEventDateTime(),
            event.getSaleStartAt(),
            event.getSaleEndAt(),
            event.getPrice(),
            event.getTotalQuantity(),
            event.getMaxQuantity(),
            event.getCategory()
        );
    }
}
