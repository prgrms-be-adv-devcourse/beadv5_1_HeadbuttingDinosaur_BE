package com.devticket.event.domain.exception;

import java.util.UUID;
import lombok.Getter;

/**
 * order.created Consumer에서 재고 차감 실패 시 throw — Consumer가 stock.failed Outbox를 새 트랜잭션으로 저장하기 위한 컨텍스트 운반체
 * @Transactional 롤백을 트리거하여 이전 항목의 부분 차감도 함께 롤백된다.
 */
@Getter
public class StockDeductionException extends RuntimeException {

    private final UUID orderId;
    private final UUID eventId;

    public StockDeductionException(UUID orderId, UUID eventId, String reason) {
        super(reason);
        this.orderId = orderId;
        this.eventId = eventId;
    }
}
