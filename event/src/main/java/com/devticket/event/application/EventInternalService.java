package com.devticket.event.application;

import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.model.Event;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.presentation.dto.internal.InternalBulkEventInfoResponse;
import com.devticket.event.presentation.dto.internal.InternalEventInfoResponse;
import com.devticket.event.presentation.dto.internal.InternalPurchaseValidationResponse;
import com.devticket.event.presentation.dto.internal.InternalSellerEventsResponse;
import com.devticket.event.presentation.dto.internal.PurchaseUnavailableReason;
import java.time.LocalDateTime;
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
