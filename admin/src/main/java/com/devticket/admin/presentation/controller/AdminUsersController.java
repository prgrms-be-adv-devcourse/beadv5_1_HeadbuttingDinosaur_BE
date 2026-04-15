package com.devticket.admin.presentation.controller;

import com.devticket.admin.application.service.AdminUserService;
import com.devticket.admin.presentation.dto.req.UserRoleRequest;
import com.devticket.admin.presentation.dto.req.UserSearchCondition;
import com.devticket.admin.presentation.dto.req.UserStatusRequest;
import com.devticket.admin.presentation.dto.res.AdminUserListResponse;
import com.devticket.admin.presentation.dto.res.UserDetailResponse;
import com.devticket.admin.presentation.dto.res.UserListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Tag(name = "Admin User", description = "관리자 회원 관리 API")
public class AdminUsersController {

    private final AdminUserService adminUserService;

    @Operation(summary = "회원 목록 조회")
    @ApiResponse(responseCode = "200", description = "정산 내역 조회 성공")
    @GetMapping("/users")
    public AdminUserListResponse getUsers(@ModelAttribute @Valid UserSearchCondition condition) {
        // GET에 @RequestBody는 문제의 소지가 큼 → @ModelAttribute
        return adminUserService.getMembers(condition);
    }

    @GetMapping("/users/{userId}")             // ← 신규
    public UserDetailResponse getUserDetail(@PathVariable UUID userId) {
        return adminUserService.getUserDetail(userId);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "회원 제재 api")
    @ApiResponse(responseCode = "200", description = "회원 제재 성공")
    @PatchMapping("/users/{userId}/status")
    public void penalizeUser(
        @RequestHeader("X-User-Id") UUID adminId,
        @PathVariable UUID userId,
        @RequestBody @Valid UserStatusRequest request) {
        adminUserService.penalizeUser(adminId, userId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "회원 권한 변경 api")
    @ApiResponse(responseCode = "200", description = "회원 권한 변경 성공")
    @PatchMapping("/users/{userId}/role")
    public void updateUserRole(
        @RequestHeader("X-User-Id") UUID adminId,
        @PathVariable UUID userId,
        @RequestBody @Valid UserRoleRequest request) {
        adminUserService.updateUserRole(adminId, userId, request);
    }


}
