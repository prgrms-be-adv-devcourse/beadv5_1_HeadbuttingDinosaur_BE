package com.devticket.payment.refund.presentation.controller;

import com.devticket.payment.refund.application.service.RefundService;
import com.devticket.payment.refund.presentation.dto.SellerEventCancelRequest;
import com.devticket.payment.refund.presentation.dto.SellerRefundListItemResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller")
@Tag(name = "Seller Refund", description = "판매자 대상 환불 API")
@RequiredArgsConstructor
public class SellerRefundController {

    private final RefundService refundService;

    @Operation(summary = "판매자 이벤트별 환불 내역 조회",
        description = "특정 이벤트의 전체 환불 내역을 페이징으로 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/refunds/events/{eventId}")
    public ResponseEntity<Page<SellerRefundListItemResponse>> getSellerRefundListByEventId(
        @RequestHeader("X-User-Id") UUID sellerId,
        @PathVariable String eventId,
        @PageableDefault(size = 10, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(refundService.getSellerRefundListByEventId(sellerId, eventId, pageable));
    }

    @Operation(summary = "판매자 이벤트 강제 취소",
        description = "판매자가 본인 이벤트를 취소. Event 상태가 FORCE_CANCELLED 로 전이되고 "
            + "해당 이벤트의 PAID 주문들에 대해 비동기로 환불 Saga 가 fan-out 된다.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "취소 요청 수락 — 환불 fan-out 은 비동기 진행")
    })
    @PostMapping("/events/{eventId}/cancel")
    public ResponseEntity<Void> cancelSellerEvent(
        @RequestHeader("X-User-Id") UUID sellerId,
        @PathVariable UUID eventId,
        @Valid @RequestBody SellerEventCancelRequest request
    ) {
        refundService.cancelSellerEvent(sellerId, eventId, request.reason());
        return ResponseEntity.accepted().build();
    }
}
