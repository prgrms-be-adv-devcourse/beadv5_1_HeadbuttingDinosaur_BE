package com.devticket.admin.presentation.controller;

import com.devticket.admin.presentation.dto.req.AdminEventSearchRequest;
import com.devticket.admin.presentation.dto.res.AdminEventListResponse;
import com.devticket.admin.presentation.dto.res.AdminEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "관리자 Event 관리 API")
public class AdminEventController {

    @Operation(summary = "관리자 Event 리스트 조회", description = "관리자가 전체 리스트를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "이벤트 목록 조회 성공")
    @ApiResponse(responseCode = "403", description = "접근 권한 없음 (COMMON_005)")
    @GetMapping("/admin/events")
    public AdminEventListResponse getEventList(@ModelAttribute AdminEventSearchRequest condition) {

        // TODO: 서비스 구현 전 임시 Mock
        List<AdminEventResponse> mockContent = List.of(
            new AdminEventResponse(
                "1d7f4d4a-1c6b-4aa2-b49e-8ed2fdb10001",
                "Spring Boot 심화 밋업",
                "DevKim",
                "ON_SALE",
                "2026-04-10T19:00:00",
                50,
                23,
                "2026-03-23T15:00:00"
            )
        );

        return new AdminEventListResponse(mockContent, 0, 20, 1L, 1);
    }


    @Operation(summary = "관리자 Event 삭제 API", description = "관리자가 이벤트를 삭제합니다.")
    @PatchMapping("/admin/events/{eventId}/force-cancel")
    public void cancelEvent(@PathVariable UUID eventId) {
        
    }

}
