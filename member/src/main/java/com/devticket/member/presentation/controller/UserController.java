package com.devticket.member.presentation.controller;

import com.devticket.member.application.UserService;
import com.devticket.member.presentation.dto.request.ChangePasswordRequest;
import com.devticket.member.presentation.dto.request.SignUpProfileRequest;
import com.devticket.member.presentation.dto.request.UpdateProfileRequest;
import com.devticket.member.presentation.dto.response.ChangePasswordResponse;
import com.devticket.member.presentation.dto.response.GetProfileResponse;
import com.devticket.member.presentation.dto.response.SignUpProfileResponse;
import com.devticket.member.presentation.dto.response.UpdateProfileResponse;
import com.devticket.member.presentation.dto.response.WithdrawResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "사용자 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "프로필 생성", description = "회원가입 Step 2 — 닉네임, 포지션, 기술 스택 입력")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "프로필 생성 성공"),
        @ApiResponse(responseCode = "409", description = "닉네임 중복")
    })
    @PostMapping("/profile")
    public ResponseEntity<SignUpProfileResponse> createProfile(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody SignUpProfileRequest request) {
        SignUpProfileResponse response = userService.createProfile(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "프로필 조회", description = "로그인한 사용자의 프로필 정보 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "회원 없음")
    })
    @GetMapping("/me")
    public ResponseEntity<GetProfileResponse> getProfile(
        @RequestHeader("X-User-Id") UUID userId) {
        GetProfileResponse response = userService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "프로필 수정", description = "닉네임, 포지션, 기술 스택, 자기소개 수정")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "409", description = "닉네임 중복")
    })
    @PatchMapping("/me")
    public ResponseEntity<UpdateProfileResponse> updateProfile(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody UpdateProfileRequest request) {
        UpdateProfileResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호 확인 후 새 비밀번호로 변경")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "변경 성공"),
        @ApiResponse(responseCode = "400", description = "현재 비밀번호 불일치")
    })
    @PatchMapping("/me/password")
    public ResponseEntity<ChangePasswordResponse> changePassword(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody ChangePasswordRequest request) {
        ChangePasswordResponse response = userService.changePassword(userId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "회원 탈퇴", description = "회원 탈퇴 요청 (Soft Delete)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "탈퇴 성공")
    })
    @DeleteMapping("/me")
    public ResponseEntity<WithdrawResponse> withdraw(
        @RequestHeader("X-User-Id") UUID userId) {
        WithdrawResponse response = userService.withdraw(userId);
        return ResponseEntity.ok(response);
    }
}

