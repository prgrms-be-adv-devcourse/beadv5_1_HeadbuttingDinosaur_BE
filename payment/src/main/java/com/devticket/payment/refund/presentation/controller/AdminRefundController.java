package com.devticket.payment.refund.presentation.controller;

import com.devticket.payment.refund.application.service.RefundService;
import com.devticket.payment.refund.presentation.dto.AdminEventCancelRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin Refund", description = "관리자 대상 환불 API")
@RequiredArgsConstructor
public class AdminRefundController {

    private final RefundService refundService;

    @Operation(summary = "관리자 이벤트 강제 취소",
        description = "관리자 권한으로 이벤트를 강제 취소. Event 상태가 FORCE_CANCELLED 로 전이되고 "
            + "해당 이벤트의 PAID 주문에 대해 비동기로 환불 Saga 가 fan-out 된다.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "취소 요청 수락 — 환불 fan-out 은 비동기 진행")
    })
    @PostMapping("/events/{eventId}/cancel")
    public ResponseEntity<Void> cancelAdminEvent(
        @RequestHeader("X-User-Id") UUID adminId,
        @PathVariable UUID eventId,
        @Valid @RequestBody AdminEventCancelRequest request
    ) {
        refundService.cancelAdminEvent(adminId, eventId, request.reason());
        return ResponseEntity.accepted().build();
    }
}