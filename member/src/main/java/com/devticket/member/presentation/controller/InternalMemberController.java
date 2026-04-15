package com.devticket.member.presentation.controller;

import com.devticket.member.application.InternalMemberService;
import com.devticket.member.presentation.domain.UserRole;
import com.devticket.member.presentation.domain.UserStatus;
import com.devticket.member.presentation.dto.internal.request.InternalDecideSellerApplicationRequest;
import com.devticket.member.presentation.dto.internal.request.InternalUpdateUserRoleRequest;
import com.devticket.member.presentation.dto.internal.request.InternalUpdateUserStatusRequest;
import com.devticket.member.presentation.dto.internal.response.InternalDecideSellerApplicationResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberInfoResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberRoleResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberStatusResponse;
import com.devticket.member.presentation.dto.internal.response.InternalPagedMemberResponse;
import com.devticket.member.presentation.dto.internal.response.InternalSellerApplicationResponse;
import com.devticket.member.presentation.dto.internal.response.InternalSellerInfoResponse;
import com.devticket.member.presentation.dto.internal.response.InternalTechStackListResponse;
import com.devticket.member.presentation.dto.internal.response.InternalUpdateRoleResponse;
import com.devticket.member.presentation.dto.internal.response.InternalUpdateStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal - Member", description = "내부 서비스 연동 API (JWT 검증 없음)")
@RestController
@RequestMapping("/internal/members")
@RequiredArgsConstructor
public class InternalMemberController {

    private final InternalMemberService internalMemberService;

    // 판매자 등록 신청자 리스트
    @Operation(summary = "판매자 신청 목록 조회", description = "내부 서비스용 — 판매자 신청 목록 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/seller-applications")
    public ResponseEntity<List<InternalSellerApplicationResponse>> getSellerApplications(){
        List<InternalSellerApplicationResponse> response = internalMemberService.getSellerApplications();
        return ResponseEntity.ok(response);
    }

    // 판매자 승인 결정
    @Operation(summary = "판매자 신청 승인/반려", description = "내부 서비스용 — 판매자 신청 승인/반려")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "처리 성공"),
        @ApiResponse(responseCode = "404", description = "신청 없음")
    })
    @PatchMapping("/seller-applications/{applicationId}")
    public ResponseEntity<InternalDecideSellerApplicationResponse> decideSellerApplication(
        @PathVariable UUID applicationId,
        @RequestBody InternalDecideSellerApplicationRequest request
    ){
        InternalDecideSellerApplicationResponse response=internalMemberService.decideSellerApplication(applicationId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "판매자 ID 목록 조회", description = "내부 서비스용 — 전체 판매자 ID 목록 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/sellers")
    public ResponseEntity<List<UUID>> getSellerId(){
        List<UUID> response = internalMemberService.getSellerIds();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "기술 스택 목록 조회", description = "내부 서비스용 — 전체 기술 스택 목록 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/tech-stacks")
    public ResponseEntity<InternalTechStackListResponse> getTechStacks() {
        InternalTechStackListResponse response = internalMemberService.getAllTechStacks();
        return ResponseEntity.ok(response);
    }

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

    @Operation(summary = "관리자 회원 목록 조회",
        description = "내부 서비스용 — 관리자 백오피스 회원 목록 조회 (role/status/keyword 필터 + 페이징)")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<InternalPagedMemberResponse> searchMembers(
        @RequestParam(required = false) UserRole role,
        @RequestParam(required = false) UserStatus status,
        @RequestParam(required = false) String keyword,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(internalMemberService.searchMembers(role, status, keyword, pageable));
    }

    @Operation(summary = "관리자 회원 상태 변경", description = "내부 서비스용 — 관리자의 회원 제재/해제")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "변경 성공"),
        @ApiResponse(responseCode = "403", description = "탈퇴된 계정 재활성화 시도 (MEMBER_008)"),
        @ApiResponse(responseCode = "404", description = "회원 없음 (MEMBER_009)")
    })
    @PatchMapping("/{userId}/status")
    public ResponseEntity<InternalUpdateStatusResponse> updateMemberStatus(
        @PathVariable UUID userId,
        @Valid @RequestBody InternalUpdateUserStatusRequest request
    ) {
        return ResponseEntity.ok(internalMemberService.updateMemberStatus(userId, request));
    }

    @Operation(summary = "관리자 회원 권한 변경", description = "내부 서비스용 — 관리자의 회원 권한 변경")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "변경 성공"),
        @ApiResponse(responseCode = "404", description = "회원 없음 (MEMBER_009)")
    })
    @PatchMapping("/{userId}/role")
    public ResponseEntity<InternalUpdateRoleResponse> updateMemberRole(
        @PathVariable UUID userId,
        @Valid @RequestBody InternalUpdateUserRoleRequest request
    ) {
        return ResponseEntity.ok(internalMemberService.updateMemberRole(userId, request));
    }

    @Operation(summary = "배치 회원 정보 조회", description = "내부 서비스용 — 여러 회원의 정보를 한 번에 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 요청 파라미터")
    })
    @GetMapping("/batch")
    public ResponseEntity<List<InternalMemberInfoResponse>> getMemberInfoBatch(
        @Parameter(description = "조회할 회원 UUID 목록", example = "uuid1,uuid2,uuid3")
        @RequestParam List<UUID> userIds
    ) {
        return ResponseEntity.ok(internalMemberService.getMemberInfoBatch(userIds));
    }
}
