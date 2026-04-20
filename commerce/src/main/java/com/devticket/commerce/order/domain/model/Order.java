package com.devticket.commerce.order.domain.model;

import com.devticket.commerce.common.entity.BaseEntity;
import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.enums.PaymentMethod;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.order.domain.exception.OrderErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Getter
@Table(
        name = "order",
        schema = "commerce",
        indexes = @Index(name = "idx_order_user_cart_hash", columnList = "user_id, cart_hash")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "order_id", unique = true)
    UUID orderId;

    @Column(name = "user_id")
    UUID userId;

    @Column(name = "order_number", nullable = false, unique = true, length = 100)
    String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    PaymentMethod paymentMethod;

    // Payment 서비스의 paymentId — payment.completed 수신 시 기록. 환불 Saga 페이로드에 필요.
    @Column(name = "payment_id")
    UUID paymentId;

    @Column(name = "total_amount")
    int totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    OrderStatus status;

    @Column(name = "ordered_at", nullable = false)
    LocalDateTime orderedAt;

    @Column(name = "deleted_at")
    LocalDateTime deletedAt;

    // 장바구니 내용 해시 — (itemId, quantity) itemId 정렬 후 SHA-256, 중복 주문 판단 기준
    // UNIQUE 제약 없음 — 이력 주문(CANCELLED/FAILED)은 동일 해시 존재 가능
    @Column(name = "cart_hash", length = 64)
    String cartHash;

    // 낙관적 락 — Consumer/스케줄러 동시 상태 전이 충돌 방어
    @Version
    @Column(name = "version", nullable = false)
    Long version;

    //---- 정적 팩토리 메서드 ------------------------------

    public static Order create(
        UUID userId,
        int totalAmount,
        String cartHash
    ) {
        LocalDateTime now = LocalDateTime.now();

        //주문번호 생성 20250327-*******
        String datePrefix = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String generatedOrderNumber = String.format("%s-%s", datePrefix, uniqueSuffix);

//        // 최소 주문 상품 존재 여부 확인
//        if (items == null || items.isEmpty()) {
//            throw new BusinessException(OrderErrorCode.EMPTY_ORDER_ITEMS);
//        }
//
//        // OrderItem들의 subtotalAmount 합계
//        int calculatedTotal = items.stream()
//            .mapToInt(OrderItem::getSubtotalAmount)
//            .sum();

        validateTotalAmount(totalAmount);

        return Order.builder()
            .orderId(UUID.randomUUID())
            .userId(userId)
            .orderNumber(generatedOrderNumber)
            .paymentMethod(null)
            .totalAmount(totalAmount)
            .status(OrderStatus.CREATED)
            .orderedAt(now)
            .cartHash(cartHash)
            .deletedAt(null)
            .build();

    }

    //---- 도메인 비즈니스 메서드 ------------------------------

    // ⚠️ 사용 금지 — OrderExpirationScheduler 만료 타이머가 BaseEntity.updated_at 에 의존함.
    // PAYMENT_PENDING 상태에서 이 메서드를 호출하면 updated_at 이 갱신되어 만료 타이머가 리셋됨.
    // 금액 변경이 필요하면 주문 재생성 경로 사용. 관련: docs/kafka-impl-plan.md §주문 만료 스케줄러
    @Deprecated(forRemoval = true)
    public void updateTotalAmount(int newTotalAmount) {
        if (newTotalAmount < 0) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
        if (this.status == OrderStatus.PAID) {
            throw new BusinessException(OrderErrorCode.CANNOT_CHANGE_AMOUNT_AFTER_PAID);
        }
        this.totalAmount = newTotalAmount;
    }

    // stock.deducted 수신: CREATED → PAYMENT_PENDING
    public void pendingPayment() {
        if (!canTransitionTo(OrderStatus.PAYMENT_PENDING)) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        this.status = OrderStatus.PAYMENT_PENDING;
    }

    // payment.completed 수신: PAYMENT_PENDING → PAID
    public void completePayment() {
        if (this.status == OrderStatus.PAID) {
            throw new BusinessException(OrderErrorCode.ALREADY_PAID_ORDER);
        }
        if (!canTransitionTo(OrderStatus.PAID)) {
            throw new BusinessException(OrderErrorCode.CANNOT_COMPLETE_PAYMENT);
        }
        this.status = OrderStatus.PAID;
    }

    // payment.completed 수신 시 paymentId/paymentMethod 기록 — 환불 Saga 페이로드용.
    public void completePayment(UUID paymentId, PaymentMethod paymentMethod) {
        completePayment();
        this.paymentId = paymentId;
        this.paymentMethod = paymentMethod;
    }

    // stock.failed 수신: CREATED → FAILED
    public void failByStock() {
        if (!canTransitionTo(OrderStatus.FAILED)) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        this.status = OrderStatus.FAILED;
    }

    // 스케줄러 만료: CREATED 상태로 만료 시간(팀 합의: 30분) 초과 시 → FAILED
    public void expire() {
        if (!canTransitionTo(OrderStatus.FAILED)) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        this.status = OrderStatus.FAILED;
    }

    // payment.failed 수신: PAYMENT_PENDING → FAILED
    public void failPayment() {
        if (!canTransitionTo(OrderStatus.FAILED)) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        this.status = OrderStatus.FAILED;
    }

    // 주문 취소: PAYMENT_PENDING → CANCELLED / PAID → CANCELLED
    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new BusinessException(OrderErrorCode.ALREADY_CANCELLED_ORDER);
        }
        if (!canTransitionTo(OrderStatus.CANCELLED)) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        this.status = OrderStatus.CANCELLED;
    }

    //환불에 의한 총 주문금액 차감 (PAID 상태에서도 허용)
    public void adjustAmountForRefund(int refundAmount) {
        if (refundAmount <= 0) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
        this.totalAmount -= refundAmount;
    }

    // refund.order.cancel 수신: PAID → REFUND_PENDING (Refund Saga 1단계)
    public void requestRefund() {
        if (!canTransitionTo(OrderStatus.REFUND_PENDING)) {
            throw new BusinessException(OrderErrorCode.REFUND_NOT_REFUNDABLE);
        }
        this.status = OrderStatus.REFUND_PENDING;
    }

    // refund.completed 수신: REFUND_PENDING → REFUNDED (Saga 최종 확정)
    public void completeRefund() {
        if (!canTransitionTo(OrderStatus.REFUNDED)) {
            throw new BusinessException(OrderErrorCode.REFUND_COMPLETE_INVALID);
        }
        this.status = OrderStatus.REFUNDED;
    }

    // refund.order.compensate 수신: REFUND_PENDING → PAID (보상 롤백)
    public void rollbackRefund() {
        if (!canTransitionTo(OrderStatus.PAID)) {
            throw new BusinessException(OrderErrorCode.REFUND_ROLLBACK_INVALID);
        }
        this.status = OrderStatus.PAID;
    }

    //---- 상태 전이 검증 ------------------------------

    // Consumer 멱등 처리 및 상태 전이 방어용 — canTransitionTo() 기반으로 모든 도메인 메서드 가드 구현
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this.status) {
            // CREATED: 재고 확인 대기 중 — stock.deducted(→PAYMENT_PENDING) 또는 stock.failed(→FAILED)만 허용
            case CREATED         -> target == OrderStatus.PAYMENT_PENDING
                                 || target == OrderStatus.FAILED;
            // PAYMENT_PENDING: 결제 대기 중 — 결제 완료/실패/취소 허용
            case PAYMENT_PENDING -> target == OrderStatus.PAID
                                 || target == OrderStatus.FAILED
                                 || target == OrderStatus.CANCELLED;
            // PAID: 결제 완료 — 취소 또는 환불 Saga 진입 허용
            case PAID            -> target == OrderStatus.CANCELLED
                                 || target == OrderStatus.REFUND_PENDING;
            // REFUND_PENDING: 환불 Saga 진행 중 — 최종 확정(REFUNDED) 또는 보상 롤백(PAID)
            case REFUND_PENDING  -> target == OrderStatus.REFUNDED
                                 || target == OrderStatus.PAID;
            // FAILED, CANCELLED, REFUNDED: 종단 상태 — 전이 불가
            default              -> false;
        };
    }

    //-------------------
    private static void validateTotalAmount(int totalAmount) {
        if (totalAmount <= 0) {
            throw new BusinessException(OrderErrorCode.INVALID_TOTAL_AMOUNT);
        }
    }

}
