package com.devticket.member.presentation.controller;

import com.devticket.member.application.InternalMemberService;
import com.devticket.member.presentation.dto.internal.response.InternalMemberInfoResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberRoleResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberStatusResponse;
import com.devticket.member.presentation.dto.internal.response.InternalSellerInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal - Member", description = "내부 서비스 연동 API (JWT 검증 없음)")
@RestController
@RequestMapping("/internal/members")
@RequiredArgsConstructor
public class InternalMemberController {

    private final InternalMemberService internalMemberService;

    @Operation(summary = "유저 기본 정보 조회", description = "내부 서비스용 — 유저 ID로 기본 정보 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "회원 없음")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<InternalMemberInfoResponse> getMemberInfo(
        @PathVariable UUID userId) {
        InternalMemberInfoResponse response = internalMemberService.getMemberInfo(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "회원 상태 확인", description = "내부 서비스용 — 주문 전 회원 상태 확인")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "회원 없음")
    })
    @GetMapping("/{userId}/status")
    public ResponseEntity<InternalMemberStatusResponse> getMemberStatus(
        @PathVariable UUID userId) {
        InternalMemberStatusResponse response = internalMemberService.getMemberStatus(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "권한 확인", description = "내부 서비스용 — 판매자 권한 확인")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "회원 없음")
    })
    @GetMapping("/{userId}/role")
    public ResponseEntity<InternalMemberRoleResponse> getMemberRole(
        @PathVariable UUID userId) {
        InternalMemberRoleResponse response = internalMemberService.getMemberRole(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "정산 계좌 조회", description = "내부 서비스용 — 판매자 정산 계좌 정보 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "회원 또는 판매자 정보 없음")
    })
    @GetMapping("/{userId}/seller-info")
    public ResponseEntity<InternalSellerInfoResponse> getSellerInfo(
        @PathVariable UUID userId) {
        InternalSellerInfoResponse response = internalMemberService.getSellerInfo(userId);
        return ResponseEntity.ok(response);
    }
}
