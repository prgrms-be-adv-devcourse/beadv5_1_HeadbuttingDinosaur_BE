package com.devticket.admin.presentation.controller;

import com.devticket.admin.application.service.AdminEventService;
import com.devticket.admin.presentation.dto.req.AdminEventSearchRequest;
import com.devticket.admin.presentation.dto.res.AdminEventListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Tag(name = "관리자 Event 관리 API")
public class AdminEventController {

    private final AdminEventService adminEventService;

    @Operation(summary = "관리자 Event 리스트 조회", description = "관리자가 전체 리스트를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "이벤트 목록 조회 성공")
    @ApiResponse(responseCode = "403", description = "접근 권한 없음 (COMMON_005)")
    @GetMapping("/events")
    public AdminEventListResponse getEventList(@ModelAttribute @Valid AdminEventSearchRequest condition) {
        return adminEventService.getEventList(condition);   // Mock 제거
    }


    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "관리자 Event 삭제 API", description = "관리자가 이벤트를 삭제합니다.")
    @PatchMapping("/events/{eventId}/force-cancel")
    public void cancelEvent(
        @RequestHeader("X-User-Id") UUID adminId,          // ← adminId 추가
        @PathVariable UUID eventId) {
        adminEventService.forceCancel(adminId, eventId);   // 빈 body 구현
    }

}
