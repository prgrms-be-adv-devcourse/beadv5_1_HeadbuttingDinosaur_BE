package com.devticket.event.presentation.controller;

import com.devticket.event.application.EventInternalService;
import com.devticket.event.common.response.SuccessResponse;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.presentation.dto.internal.InternalBulkEventInfoRequest;
import com.devticket.event.presentation.dto.internal.InternalBulkEventInfoResponse;
import com.devticket.event.presentation.dto.internal.InternalBulkStockAdjustmentRequest;
import com.devticket.event.presentation.dto.internal.InternalStockAdjustmentResponse;
import com.devticket.event.presentation.dto.internal.InternalEventInfoResponse;
import com.devticket.event.presentation.dto.internal.InternalPurchaseValidationResponse;
import com.devticket.event.presentation.dto.internal.InternalSellerEventsResponse;
import com.devticket.event.presentation.dto.internal.InternalStockDeductRequest;
import com.devticket.event.presentation.dto.internal.InternalStockOperationResponse;
import com.devticket.event.presentation.dto.internal.InternalStockRestoreRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
@Tag(name = "Event Internal API", description = "Commerce, Payment, Settlement 서비스 연동용 내부 API")
public class EventInternalController {

    private final EventInternalService eventInternalService;

    /**
     * API 1: 단건 이벤트 정보 조회
     * Commerce/Payment 서비스가 id(Long)로 이벤트 기본 정보를 조회할 때 사용
     */
    @GetMapping("/{eventId}")
    public ResponseEntity<SuccessResponse<InternalEventInfoResponse>> getEventInfo(
        @PathVariable UUID eventId) {
        return ResponseEntity.ok(SuccessResponse.success(
            eventInternalService.getEventInfo(eventId)
        ));
    }

    /**
     * API 2: 벌크 이벤트 정보 조회
     * 없는 ID는 결과에서 누락 (예외 없음) — 부분 결과 반환
     */
    @PostMapping("/bulk")
    public ResponseEntity<SuccessResponse<InternalBulkEventInfoResponse>> getBulkEventInfo(
        @RequestBody @Valid InternalBulkEventInfoRequest request) {
        return ResponseEntity.ok(SuccessResponse.success(
            eventInternalService.getBulkEventInfo(request.ids())
        ));
    }

    /**
     * API 3: 구매 가능 여부 검증
     * 성공: purchasable=true / 실패: purchasable=false + 불가 사유 포함
     */
    @GetMapping("/{eventId}/validate-purchase")
    public ResponseEntity<SuccessResponse<InternalPurchaseValidationResponse>> validatePurchase(
        @PathVariable UUID eventId,
        @RequestParam int requestedQuantity) {
        return ResponseEntity.ok(SuccessResponse.success(
            eventInternalService.validatePurchase(eventId, requestedQuantity)
        ));
    }

    /**
     * API 4: 벌크 재고 조정 (원자적 처리 — All or Nothing)
     * delta > 0: 차감, delta < 0: 복원, delta == 0: no-op
     * 하나라도 실패 시 전체 롤백
     */
    @PatchMapping("/stock-adjustments")
    public ResponseEntity<SuccessResponse<InternalStockAdjustmentResponse>> adjustStockBulk(
        @RequestBody @Valid InternalBulkStockAdjustmentRequest request) {
        return ResponseEntity.ok(SuccessResponse.success(
            eventInternalService.adjustStockBulk(request)
        ));
    }

    /**
     * API 5: 단건 재고 차감
     * Pessimistic Lock 적용 — 동시 요청 직렬화
     */
    @PostMapping("/{eventId}/deduct-stock")
    public ResponseEntity<SuccessResponse<InternalStockOperationResponse>> deductStock(
        @PathVariable UUID eventId,
        @RequestBody @Valid InternalStockDeductRequest request) {
        return ResponseEntity.ok(SuccessResponse.success(
            eventInternalService.deductStock(eventId, request.quantity())
        ));
    }

    /**
     * API 6: 단건 재고 복원
     * Pessimistic Lock 적용 — 동시 요청 직렬화
     */
    @PostMapping("/{eventId}/restore-stock")
    public ResponseEntity<SuccessResponse<InternalStockOperationResponse>> restoreStock(
        @PathVariable UUID eventId,
        @RequestBody @Valid InternalStockRestoreRequest request) {
        return ResponseEntity.ok(SuccessResponse.success(
            eventInternalService.restoreStock(eventId, request.quantity())
        ));
    }

    /**
     * API 7: 판매자별 이벤트 목록 조회
     * status=null이면 전체 상태 반환 (Settlement 정산 집계 지원)
     */
    @GetMapping("/by-seller/{sellerId}")
    public ResponseEntity<SuccessResponse<InternalSellerEventsResponse>> getEventsBySeller(
        @PathVariable UUID sellerId,
        @RequestParam(required = false) EventStatus status) {
        return ResponseEntity.ok(SuccessResponse.success(
            eventInternalService.getEventsBySeller(sellerId, status)
        ));
    }
}
