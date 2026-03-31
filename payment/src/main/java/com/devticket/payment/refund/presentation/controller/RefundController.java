package com.devticket.payment.refund.presentation.controller;

import com.devticket.payment.refund.application.service.RefundService;
import com.devticket.payment.refund.presentation.dto.RefundInfoResponse;
import com.devticket.payment.refund.presentation.dto.PgRefundRequest;
import com.devticket.payment.refund.presentation.dto.PgRefundResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/refunds")
@Tag(name = "Refund", description = "환불 API")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @Operation(summary = "환불 정보 조회", description = "ticketId 기반으로 환불 예상 금액 및 정책 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "결제 정보 없음")
    })
    @GetMapping()
    public ResponseEntity<RefundInfoResponse> getRefundInfo(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestParam String ticketId) {

        return ResponseEntity.ok(refundService.getRefundInfo(userId, ticketId));
    }

    @PostMapping("/pg/{ticketId}")
    public ResponseEntity<PgRefundResponse> refundPgTicket(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable String ticketId,
        @Valid @RequestBody PgRefundRequest request
    ) {
        PgRefundResponse response = refundService.refundPgTicket(userId, ticketId, request);
        return ResponseEntity.ok(response);
    }
}
