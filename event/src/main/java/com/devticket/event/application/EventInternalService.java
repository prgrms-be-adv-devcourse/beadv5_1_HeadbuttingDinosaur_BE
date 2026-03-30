package com.devticket.event.application;

import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.model.Event;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.presentation.dto.internal.InternalBulkEventInfoResponse;
import com.devticket.event.presentation.dto.internal.InternalBulkStockAdjustmentRequest;
import com.devticket.event.presentation.dto.internal.InternalEventInfoResponse;
import com.devticket.event.presentation.dto.internal.InternalPurchaseValidationResponse;
import com.devticket.event.presentation.dto.internal.InternalSellerEventsResponse;
import com.devticket.event.presentation.dto.internal.InternalStockAdjustmentResponse;
import com.devticket.event.presentation.dto.internal.InternalStockOperationResponse;
import com.devticket.event.presentation.dto.internal.PurchaseUnavailableReason;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventInternalService {

    private final EventRepository eventRepository;

    /**
     * API 1: 단건 이벤트 정보 조회
     * Commerce/Payment 서비스가 eventId(Long)로 이벤트 기본 정보를 조회할 때 사용
     */
    public InternalEventInfoResponse getEventInfo(Long id) {
        Event event = eventRepository.findById(id)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));
        return InternalEventInfoResponse.from(event);
    }

    /**
     * API 2: 벌크 이벤트 정보 조회
     * 없는 ID는 조용히 누락 (예외 없음) — 부분 성공 시나리오 허용
     */
    public InternalBulkEventInfoResponse getBulkEventInfo(List<Long> ids) {
        List<InternalEventInfoResponse> responses = eventRepository.findAllByIdIn(ids)
            .stream()
            .map(InternalEventInfoResponse::from)
            .toList();
        return new InternalBulkEventInfoResponse(responses);
    }

    /**
     * API 3: 구매 가능 여부 검증
     * 성공 시 reason=null, 실패 시 불가 사유 + 이벤트 정보(maxQuantity, title, price) 포함
     */
    public InternalPurchaseValidationResponse validatePurchase(Long id, int requestedQuantity) {
        // 잘못된 수량 요청 — 명시적 검증 (재고 부족으로 오분류하지 않기 위함)
        if (requestedQuantity < 1) {
            throw new BusinessException(EventErrorCode.INVALID_STOCK_QUANTITY);
        }

        Event event = eventRepository.findById(id)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));

        if (event.isPurchasable(requestedQuantity)) {
            return InternalPurchaseValidationResponse.success(id);
        }

        PurchaseUnavailableReason reason = resolveReason(event, requestedQuantity);
        return InternalPurchaseValidationResponse.failure(
            id, reason, event.getMaxQuantity(), event.getTitle(), event.getPrice()
        );
    }

    /**
     * API 7: 판매자별 이벤트 목록 조회
     * status=null이면 해당 판매자의 전체 상태 이벤트 반환
     */
    public InternalSellerEventsResponse getEventsBySeller(UUID sellerId, EventStatus status) {
        List<InternalSellerEventsResponse.SellerEventSummary> summaries =
            eventRepository.findEventsBySeller(sellerId, status)
                .stream()
                .map(e -> new InternalSellerEventsResponse.SellerEventSummary(
                    e.getId(),
                    e.getTitle(),
                    e.getPrice(),
                    e.getTotalQuantity(),
                    e.getRemainingQuantity(),
                    e.getStatus(),
                    e.getEventDateTime()
                ))
                .toList();
        return new InternalSellerEventsResponse(sellerId, summaries);
    }

    /**
     * API 5: 단건 재고 차감
     * Pessimistic Lock으로 동시성 제어
     */
    @Transactional
    public InternalStockOperationResponse deductStock(Long id, int quantity) {
        Event event = eventRepository.findByIdWithLock(id)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));
        event.deductStock(quantity);
        return new InternalStockOperationResponse(id, true, event.getRemainingQuantity(), event.getTitle());
    }

    /**
     * API 6: 단건 재고 복원
     * Pessimistic Lock으로 동시성 제어
     */
    @Transactional
    public InternalStockOperationResponse restoreStock(Long id, int quantity) {
        Event event = eventRepository.findByIdWithLock(id)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));
        event.restoreStock(quantity);
        return new InternalStockOperationResponse(id, true, event.getRemainingQuantity(), event.getTitle());
    }

    /**
     * API 4: 벌크 재고 조정 — 단일 트랜잭션, 항목별 성공/실패 수집
     * delta > 0: 재고 차감 (판매), delta < 0: 재고 복원 (환불), delta == 0: no-op
     */
    @Transactional
    public InternalStockAdjustmentResponse adjustStockBulk(InternalBulkStockAdjustmentRequest request) {
        List<InternalStockAdjustmentResponse.StockAdjustmentResult> results = new ArrayList<>();
        for (InternalBulkStockAdjustmentRequest.StockAdjustmentItem item : request.items()) {
            try {
                Event event = eventRepository.findByIdWithLock(item.id())
                    .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));
                if (item.delta() > 0) {
                    event.deductStock(item.delta());
                } else if (item.delta() < 0) {
                    event.restoreStock(-item.delta());
                }
                results.add(new InternalStockAdjustmentResponse.StockAdjustmentResult(
                    item.id(), true, event.getRemainingQuantity(), event.getTitle(), event.getPrice()
                ));
            } catch (BusinessException e) {
                results.add(new InternalStockAdjustmentResponse.StockAdjustmentResult(
                    item.id(), false, null, null, null
                ));
            }
        }
        return new InternalStockAdjustmentResponse(results);
    }

    /**
     * isPurchasable() 실패 원인을 PurchaseUnavailableReason으로 역추론
     * 우선순위: 취소됨 > 매진 > 판매 기간 외 > 인당 한도 초과 > 재고 부족
     */
    private PurchaseUnavailableReason resolveReason(Event event, int requestedQuantity) {
        EventStatus status = event.getStatus();

        if (status == EventStatus.CANCELLED || status == EventStatus.FORCE_CANCELLED) {
            return PurchaseUnavailableReason.EVENT_CANCELLED;
        }
        if (status == EventStatus.SOLD_OUT) {
            return PurchaseUnavailableReason.SOLD_OUT;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(event.getSaleStartAt()) || now.isAfter(event.getSaleEndAt())) {
            return PurchaseUnavailableReason.SALE_ENDED;
        }
        if (requestedQuantity > event.getMaxQuantity()) {
            return PurchaseUnavailableReason.MAX_PER_USER_EXCEEDED;
        }
        return PurchaseUnavailableReason.INSUFFICIENT_STOCK;
    }
}
