package com.devticket.payment.refund.presentation.controller;

import com.devticket.payment.refund.application.service.RefundService;
import com.devticket.payment.refund.presentation.dto.OrderRefundRequest;
import com.devticket.payment.refund.presentation.dto.OrderRefundResponse;
import com.devticket.payment.refund.presentation.dto.RefundDetailResponse;
import com.devticket.payment.refund.presentation.dto.RefundInfoResponse;
import com.devticket.payment.refund.presentation.dto.RefundListItemResponse;
import com.devticket.payment.refund.presentation.dto.PgRefundRequest;
import com.devticket.payment.refund.presentation.dto.PgRefundResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/refunds")
@Tag(name = "Refund", description = "환불 API")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @Operation(summary = "환불 정보 조회", description = "ticketId 기반으로 환불 예상 금액 및 정책 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "결제 정보 없음")
    })
    @GetMapping("/info")
    public ResponseEntity<RefundInfoResponse> getRefundInfo(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestParam String ticketId) {

        return ResponseEntity.ok(refundService.getRefundInfo(userId, ticketId));
    }

    @Operation(summary = "환불 내역 목록 조회", description = "사용자의 환불 내역을 페이징으로 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping()
    public ResponseEntity<Page<RefundListItemResponse>> getRefundList(
        @RequestHeader("X-User-Id") UUID userId,
        @PageableDefault(size = 10, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(refundService.getRefundList(userId, pageable));
    }

    @Operation(summary = "환불 상세 조회", description = "refundId로 환불 상세 정보를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "환불 내역 없음")
    })
    @GetMapping("/{refundId}")
    public ResponseEntity<RefundDetailResponse> getRefundDetail(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID refundId
    ) {
        return ResponseEntity.ok(refundService.getRefundDetail(userId, refundId));
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

    @Operation(summary = "오더 전체 환불 요청",
        description = "오더 내 ISSUED 상태 티켓 전부를 한 번에 비동기 환불 처리합니다. Saga 시작 직후 REQUESTED 상태로 즉시 응답합니다.")
    @PostMapping("/orders/{orderId}")
    public ResponseEntity<OrderRefundResponse> refundOrder(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID orderId,
        @Valid @RequestBody OrderRefundRequest request
    ) {
        OrderRefundResponse response = refundService.refundOrder(userId, orderId, request.reason());
        return ResponseEntity.ok(response);
    }
}
