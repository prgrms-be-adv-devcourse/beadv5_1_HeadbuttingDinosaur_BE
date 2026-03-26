package com.devticket.member.presentation.controller;

import com.devticket.member.application.AuthService;
import com.devticket.member.presentation.dto.request.LoginRequest;
import com.devticket.member.presentation.dto.request.SignUpRequest;
import com.devticket.member.presentation.dto.request.SocialSignUpOrLoginRequest;
import com.devticket.member.presentation.dto.request.TokenRefreshRequest;
import com.devticket.member.presentation.dto.response.LoginResponse;
import com.devticket.member.presentation.dto.response.LogoutResponse;
import com.devticket.member.presentation.dto.response.SignUpResponse;
import com.devticket.member.presentation.dto.response.SocialSignUpOrLoginResponse;
import com.devticket.member.presentation.dto.response.TokenRefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입 Step 1", description = "이메일, 비밀번호로 계정 생성")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "계정 생성 성공"),
        @ApiResponse(responseCode = "409", description = "이메일 중복")
    })
    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signup(@Valid @RequestBody SignUpRequest request) {
        SignUpResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "일반 로그인", description = "이메일, 비밀번호로 로그인")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그인 성공"),
        @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "구글 소셜 로그인", description = "Google ID Token으로 로그인/가입")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그인 성공"),
        @ApiResponse(responseCode = "201", description = "신규 가입 성공"),
        @ApiResponse(responseCode = "409", description = "동일 이메일 계정 존재")
    })
    @PostMapping("/social/google")
    public ResponseEntity<SocialSignUpOrLoginResponse> socialLogin(
        @Valid @RequestBody SocialSignUpOrLoginRequest request) {
        SocialSignUpOrLoginResponse response = authService.socialLogin(request);
        HttpStatus status = response.isNewUser() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @Operation(summary = "로그아웃", description = "Refresh Token 삭제")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    })
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
        @RequestHeader("Authorization") String refreshToken) {
        LogoutResponse response = authService.logout(refreshToken);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 Access Token 재발급")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "재발급 성공"),
        @ApiResponse(responseCode = "401", description = "Refresh Token 유효하지 않음")
    })
    @PostMapping("/reissue")
    public ResponseEntity<TokenRefreshResponse> reissue(
        @Valid @RequestBody TokenRefreshRequest request) {
        TokenRefreshResponse response = authService.reissue(request);
        return ResponseEntity.ok(response);
    }
}
