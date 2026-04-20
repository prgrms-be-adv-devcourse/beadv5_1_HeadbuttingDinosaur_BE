package com.devticket.admin.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminEventListResponse(

    @Schema(description = "이벤트 목록")
    List<AdminEventResponse> content,

    @Schema(description = "현재 페이지 번호", example = "0")
    Integer page,

    @Schema(description = "페이지 크기", example = "20")
    Integer size,

    @Schema(description = "전체 데이터 수", example = "100")
    Long totalElements,

    @Schema(description = "전체 페이지 수", example = "5")
    Integer totalPages
) {

}
