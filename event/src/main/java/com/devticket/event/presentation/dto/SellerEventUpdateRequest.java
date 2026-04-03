package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import java.time.LocalDateTime;
import java.util.List;

public record SellerEventUpdateRequest(
    String title,
    String description,
    String location,
    LocalDateTime eventDateTime,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt,
    Integer price,
    Integer totalQuantity,
    Integer maxQuantity,
    EventCategory category,
    List<Long> techStackIds,
    List<String> imageUrls,
    EventStatus status
) {}
