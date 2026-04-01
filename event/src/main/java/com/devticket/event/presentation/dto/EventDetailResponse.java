package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventImage;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record EventDetailResponse(
    UUID eventId,
    UUID sellerId,
    String sellerNickname,
    String title,
    String description,
    String location,
    LocalDateTime eventDateTime,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt,
    Integer price,
    Integer totalQuantity,
    Integer remainingQuantity,
    Integer maxQuantity,
    EventStatus status,
    EventCategory category,
    List<TechStackInfo> techStacks,
    List<String> imageUrls
) {

    public record TechStackInfo(Long techStackId, String name) {}

    public static EventDetailResponse from(Event event, String sellerNickname) {
        List<TechStackInfo> techStacks = event.getEventTechStacks().stream()
            .map(ts -> new TechStackInfo(ts.getTechStackId(), ts.getTechStackName()))
            .toList();

        List<String> imageUrls = event.getEventImages().stream()
            .sorted(Comparator.comparingInt(EventImage::getSortOrder))
            .map(EventImage::getImageUrl)
            .toList();

        return new EventDetailResponse(
            event.getEventId(),
            event.getSellerId(),
            sellerNickname,
            event.getTitle(),
            event.getDescription(),
            event.getLocation(),
            event.getEventDateTime(),
            event.getSaleStartAt(),
            event.getSaleEndAt(),
            event.getPrice(),
            event.getTotalQuantity(),
            event.getRemainingQuantity(),
            event.getMaxQuantity(),
            event.getStatus(),
            event.getCategory(),
            techStacks,
            imageUrls
        );
    }
}
