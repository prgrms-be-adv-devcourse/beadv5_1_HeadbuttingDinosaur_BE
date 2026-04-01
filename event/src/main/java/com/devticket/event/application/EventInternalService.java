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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    public InternalEventInfoResponse getEventInfo(UUID id) {
        Event event = eventRepository.findByEventId(id)
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
    public InternalPurchaseValidationResponse validatePurchase(UUID id, int requestedQuantity) {
        // 잘못된 수량 요청 — 명시적 검증 (재고 부족으로 오분류하지 않기 위함)
        if (requestedQuantity < 1) {
            throw new BusinessException(EventErrorCode.INVALID_STOCK_QUANTITY);
        }

        Event event = eventRepository.findByEventId(id)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));

        if (event.isPurchasable(requestedQuantity)) {
            return InternalPurchaseValidationResponse.success(
                event.getEventId(), event.getMaxQuantity(), event.getTitle(), event.getPrice()
            );
        }

        PurchaseUnavailableReason reason = resolveReason(event, requestedQuantity);
        return InternalPurchaseValidationResponse.failure(
            event.getEventId(), reason, event.getMaxQuantity(), event.getTitle(), event.getPrice()
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
                    e.getEventId(),
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
    public InternalStockOperationResponse deductStock(UUID id, int quantity) {
        Event event = eventRepository.findByEventIdWithLock(id)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));
        event.deductStock(quantity);
        return new InternalStockOperationResponse(event.getEventId(), true, event.getRemainingQuantity(), event.getTitle());
    }

    /**
     * API 6: 단건 재고 복원
     * Pessimistic Lock으로 동시성 제어
     */
    @Transactional
    public InternalStockOperationResponse restoreStock(UUID id, int quantity) {
        Event event = eventRepository.findByEventIdWithLock(id)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));
        event.restoreStock(quantity);
        return new InternalStockOperationResponse(event.getEventId(), true, event.getRemainingQuantity(), event.getTitle());
    }

    /**
     * API 4: 벌크 재고 조정 — 원자적 처리 (All or Nothing)
     *
     * 설계 원칙:
     * - 하나의 트랜잭션 내에서 모든 항목을 처리
     * - 하나라도 실패하면 전체 롤백 (원자성 보장)
     * - 부분 성공 상태는 발생하지 않음
     *
     * delta > 0: 재고 차감 (판매)
     * delta < 0: 재고 복원 (환불)
     * delta == 0: no-op
     *
     * HTTP 호출측:
     * - 성공: 모든 항목이 처리됨
     * - 실패: 예외 발생 → 하나라도 실패하면 전체 롤백 → 주문 취소
     *
     * Kafka 전환 시:
     * - Consumer가 성공/실패 이벤트만 수신
     * - 부분 보상 로직 불필요 → Saga 구조 단순화
     */
    @Transactional
    public InternalStockAdjustmentResponse adjustStockBulk(InternalBulkStockAdjustmentRequest request) {
        // Step 1: 인덱스와 함께 정렬 (락 순서 고정)
        record ItemWithIndex(
            int originalIndex,
            InternalBulkStockAdjustmentRequest.StockAdjustmentItem item
        ) {}

        List<ItemWithIndex> sortedItems = IntStream.range(0, request.items().size())
            .mapToObj(i -> new ItemWithIndex(i, request.items().get(i)))
            .sorted(Comparator.comparing(x -> x.item().id()))
            .toList();

        // Step 2: 모든 고유 ID를 정렬하여 한 번에 락 획득 (deadlock 방지)
        List<UUID> uniqueSortedIds = sortedItems.stream()
            .map(x -> x.item().id())
            .distinct()
            .sorted()
            .toList();

        Map<UUID, Event> eventMap = eventRepository.findAllByEventIdInWithLock(uniqueSortedIds)
            .stream()
            .collect(Collectors.toMap(Event::getEventId, e -> e));

        // Step 3: 정렬된 순서로 처리 — 예외 발생 시 전체 롤백
        List<InternalStockAdjustmentResponse.StockAdjustmentResult> sortedResults = new ArrayList<>();
        for (var itemWithIndex : sortedItems) {
            Event event = eventMap.get(itemWithIndex.item().id());
            if (event == null) {
                throw new BusinessException(EventErrorCode.EVENT_NOT_FOUND);
            }

            // 각 처리가 Event 상태를 변경 (같은 id의 다음 item은 변경된 상태를 봄)
            if (itemWithIndex.item().delta() > 0) {
                event.deductStock(itemWithIndex.item().delta());
            } else if (itemWithIndex.item().delta() < 0) {
                event.restoreStock(-itemWithIndex.item().delta());
            }

            sortedResults.add(new InternalStockAdjustmentResponse.StockAdjustmentResult(
                event.getEventId(),
                true,
                event.getRemainingQuantity(),
                event.getTitle(),
                event.getPrice(),
                event.getMaxQuantity()
            ));
        }

        // Step 4: 원래 요청 순서로 재정렬하여 응답
        InternalStockAdjustmentResponse.StockAdjustmentResult[] results =
            new InternalStockAdjustmentResponse.StockAdjustmentResult[request.items().size()];

        for (int i = 0; i < sortedItems.size(); i++) {
            results[sortedItems.get(i).originalIndex()] = sortedResults.get(i);
        }

        return new InternalStockAdjustmentResponse(Arrays.asList(results));
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
