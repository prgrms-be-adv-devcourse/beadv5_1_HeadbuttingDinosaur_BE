package com.devticket.member.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member", description = "회원/인증 외부 API")
@RestController
public class MemberController {

    @Operation(
        summary = "Member 서비스 헬스 체크",
        description = "Swagger UI 및 서비스 기동 여부 확인용 API"
    )
    @ApiResponse(responseCode = "200", description = "정상 응답")
    @GetMapping("/api/members/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("member ok");
    }
}
