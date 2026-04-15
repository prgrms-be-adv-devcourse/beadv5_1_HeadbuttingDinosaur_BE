package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventImage;
import com.devticket.event.domain.model.EventTechStack;
import com.devticket.event.infrastructure.search.EventDocument;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record EventListContentResponse(
    UUID eventId,
    String title,
    String thumbnailUrl,
    String location,
    LocalDateTime eventDateTime,
    Integer price,
    EventStatus status,
    List<String> techStacks,
    LocalDateTime saleEndAt,
    Integer totalQuantity,
    Integer remainingQuantity
) {
    public static EventListContentResponse from(Event event) {
        String thumbnailUrl = event.getEventImages().stream()
            .min(Comparator.comparingInt(EventImage::getSortOrder))
            .map(EventImage::getImageUrl)
            .orElse(null);
        List<String> techStacks = event.getEventTechStacks().stream()
            .map(EventTechStack::getTechStackName)
            .toList();
        return new EventListContentResponse(
            event.getEventId(),
            event.getTitle(),
            thumbnailUrl,
            event.getLocation(),
            event.getEventDateTime(),
            event.getPrice(),
            event.getStatus(),
            techStacks,
            event.getSaleEndAt(),
            event.getTotalQuantity(),
            event.getRemainingQuantity()
        );
    }

    public static EventListContentResponse fromELS(EventDocument doc) {
        return new EventListContentResponse(
            UUID.fromString(doc.getId()),
            doc.getTitle(),
            null,
            null,
            null,
            0,
            doc.getStatus() != null ? EventStatus.valueOf(doc.getStatus()) : null,
            doc.getTechStacks() != null ? doc.getTechStacks() : List.of(),
            null
        );
    }

}
