package com.devticket.event.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Event External API", description = "사용자 대상 Event 서비스 API")
public class EventExternalController {

    @GetMapping("/{eventId}")
    @Operation(summary = "이벤트 상세 조회", description = "이벤트 ID를 기반으로 상세 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "이벤트를 찾을 수 없음")
    public ResponseEntity<Object> getEvent(@PathVariable Long eventId) {
        // 실제 비즈니스 로직 연동 위치
        return ResponseEntity.ok().build();
    }
}
