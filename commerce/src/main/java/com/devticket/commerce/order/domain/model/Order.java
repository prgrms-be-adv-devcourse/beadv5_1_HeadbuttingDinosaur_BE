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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "order", schema = "commerce")
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

    @Column(name = "total_amount")
    int totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    OrderStatus status;

    @Column(name = "ordered_at", nullable = false)
    LocalDateTime orderedAt;

    @Column(name = "deleted_at")
    LocalDateTime deletedAt;

    //---- 정적 팩토리 메서드 ------------------------------

    public static Order create(
        UUID userId,
        List<OrderItem> items
    ) {
        LocalDateTime now = LocalDateTime.now();

        //주문번호 생성 20250327-*******
        String datePrefix = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String generatedOrderNumber = String.format("%s-%s", datePrefix, uniqueSuffix);

        // 최소 주문 상품 존재 여부 확인
        if (items == null || items.isEmpty()) {
            throw new BusinessException(OrderErrorCode.EMPTY_ORDER_ITEMS);
        }

        // OrderItem들의 subtotalAmount 합계
        int calculatedTotal = items.stream()
            .mapToInt(OrderItem::getSubtotalAmount)
            .sum();

        validateTotalAmount(calculatedTotal);

        return Order.builder()
            .orderId(UUID.randomUUID())
            .userId(userId)
            .orderNumber(generatedOrderNumber)
            .paymentMethod(null)
            .totalAmount(calculatedTotal)
            .status(OrderStatus.CREATED)
            .orderedAt(now)
            .deletedAt(null)
            .build();

    }

    //---- 도메인 비즈니스 메서드 ------------------------------

    //총 주문 금액 업데이트
    //OrderItem의 구매수량이 변경되면 Order의 총 주문금액도 변경됩니다.
    public void updateTotalAmount(int newTotalAmount) {
        if (newTotalAmount < 0) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
        if (this.status == OrderStatus.PAID) {
            throw new BusinessException(OrderErrorCode.CANNOT_CHANGE_AMOUNT_AFTER_PAID);
        }
        this.totalAmount = newTotalAmount;
    }

    //주문 상태 변경 : 결제 대기중 PAYMENT_PENDING
    public void pendingPayment(PaymentMethod method) {
        if (this.status != OrderStatus.CREATED) {
            throw new BusinessException(OrderErrorCode.CANNOT_CHANGE_TO_PENDING);
        }
        this.status = OrderStatus.PAYMENT_PENDING;
    }

    //주문 상태 변경 : 결제완료 PAID
    public void completePayment() {
        if (this.status == OrderStatus.PAID) {
            throw new BusinessException(OrderErrorCode.ALREADY_PAID_ORDER);
        }
        if (this.status != OrderStatus.PAYMENT_PENDING) {
            throw new BusinessException(OrderErrorCode.CANNOT_COMPLETE_PAYMENT);
        }
        this.status = OrderStatus.PAID;
    }

    //주문 상태 변경 : 주문취소 CANCELLED
    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new BusinessException(OrderErrorCode.ALREADY_CANCELLED_ORDER);
        }
        this.status = OrderStatus.CANCELLED;
    }

    //-------------------
    private static void validateTotalAmount(int totalAmount) {
        if (totalAmount <= 0) {
            throw new BusinessException(OrderErrorCode.INVALID_TOTAL_AMOUNT);
        }
    }

}
