package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public record SellerEventUpdateRequest(
    @Size(min = 2, max = 50, message = "이벤트 제목은 2자 이상 50자 이하여야 합니다.")
    String title,

    String description,

    String location,

    LocalDateTime eventDateTime,

    LocalDateTime saleStartAt,

    LocalDateTime saleEndAt,

    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    @Max(value = 9999999, message = "가격은 9,999,999원 이하여야 합니다.")
    Integer price,

    @Min(value = 5, message = "총 수량은 5명 이상이어야 합니다.")
    @Max(value = 9999, message = "총 수량은 9,999명 이하여야 합니다.")
    Integer totalQuantity,

    @Min(value = 1, message = "인당 최대 구매 수량은 1 이상이어야 합니다.")
    Integer maxQuantity,

    EventCategory category,

    @Size(min = 1, max = 5, message = "기술 스택은 1개에서 5개까지 선택 가능합니다.")
    List<Long> techStackIds,

    @Size(max = 1, message = "이미지는 최대 1장까지 업로드 가능합니다.")
    List<String> imageUrls,

    EventStatus status
) {}
