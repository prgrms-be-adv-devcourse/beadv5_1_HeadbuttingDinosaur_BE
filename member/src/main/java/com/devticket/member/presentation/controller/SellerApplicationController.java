package com.devticket.member.presentation.controller;

import com.devticket.member.application.SellerApplicationService;
import com.devticket.member.presentation.dto.request.SellerApplicationRequest;
import com.devticket.member.presentation.dto.response.SellerApplicationResponse;
import com.devticket.member.presentation.dto.response.SellerApplicationStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller Application", description = "판매자 전환 신청 API")
@RestController
@RequestMapping("/api/seller-applications")
@RequiredArgsConstructor
public class SellerApplicationController {

    private final SellerApplicationService sellerApplicationService;

    @Operation(summary = "판매자 전환 신청", description = "정산 계좌 정보와 함께 판매자 전환 신청")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "신청 성공"),
        @ApiResponse(responseCode = "409", description = "이미 신청 진행 중")
    })
    @PostMapping
    public ResponseEntity<SellerApplicationResponse> apply(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody SellerApplicationRequest request) {
        SellerApplicationResponse response = sellerApplicationService.apply(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "신청 상태 조회", description = "본인의 판매자 전환 신청 상태 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "신청 내역 없음")
    })
    @GetMapping("/me")
    public ResponseEntity<SellerApplicationStatusResponse> getMyApplication(
        @RequestHeader("X-User-Id") UUID userId) {
        SellerApplicationStatusResponse response = sellerApplicationService.getMyApplication(userId);
        return ResponseEntity.ok(response);
    }
}