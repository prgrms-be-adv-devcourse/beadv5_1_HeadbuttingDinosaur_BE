package com.devticket.admin.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminSettelmentListResponse(

    @Schema(description = "정산 목록 (SettlementResponse 재사용)", example = "json List 형태")
    List<SettlementResponse> content,

    @Schema(description = "현재 페이지 번호", example = "0")
    Integer page,

    @Schema(description = "페이지 크기", example = "20")
    Integer size,

    @Schema(description = "전체 데이터 수", example = "1")
    Long totalElements,

    @Schema(description = "전체 페이지 수", example = "1")
    Integer totalPages
) {

}
