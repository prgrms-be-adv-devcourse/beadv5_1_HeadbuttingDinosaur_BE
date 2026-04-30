package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventCategory;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SellerEventCreateRequest(
    @NotBlank(message = "이벤트 제목은 필수입니다.")
    @Size(min = 2, max = 50, message = "이벤트 제목은 2자 이상 50자 이하여야 합니다.")
    String title,

    @NotBlank(message = "상세 설명은 필수입니다.")
    String description,

    @NotBlank(message = "장소는 필수입니다.")
    String location,

    @NotNull(message = "행사 일시는 필수입니다.")
    LocalDateTime eventDateTime,

    @NotNull(message = "판매 시작 시각은 필수입니다.")
    LocalDateTime saleStartAt,

    @NotNull(message = "판매 종료 시각은 필수입니다.")
    LocalDateTime saleEndAt,

    @NotNull(message = "가격은 필수입니다.")
    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    @Max(value = 9999999, message = "가격은 9,999,999원 이하여야 합니다.")
    Integer price,

    @NotNull(message = "총 수량은 필수입니다.")
    @Min(value = 5, message = "총 수량은 5명 이상이어야 합니다.")
    @Max(value = 9999, message = "총 수량은 9,999명 이하여야 합니다.")
    Integer totalQuantity,

    @NotNull(message = "인당 최대 구매 수량은 필수입니다.")
    @Min(value = 1, message = "인당 최대 구매 수량은 1 이상이어야 합니다.")
    Integer maxQuantity,

    @NotNull(message = "카테고리는 필수입니다.")
    EventCategory category,

    @NotNull(message = "기술 스택은 1개 이상 선택해야 합니다.")
    @Size(min = 1, max = 5, message = "기술 스택은 1개에서 5개까지 선택 가능합니다.")
    List<Long> techStackIds,

    @Size(max = 1, message = "이미지는 최대 1장까지 업로드 가능합니다.")
    List<String> imageUrls
) {}
