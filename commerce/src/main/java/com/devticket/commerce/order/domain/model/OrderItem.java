package com.devticket.commerce.order.domain.model;

import com.devticket.commerce.common.entity.BaseEntity;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.order.domain.exception.OrderErrorCode;
import com.devticket.commerce.order.domain.exception.OrderItemErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "order_item", schema = "commerce")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "order_item_id", unique = true)
    UUID orderItemId;

    @Column(name = "order_id")
    Long orderId;

    @Column(name = "user_id")
    UUID userId;

    @Column(name = "event_id")
    Long eventId;

    @Column
    int price;

    @Column
    int quantity;

    @Column(name = "subtotal_amount")
    int subtotalAmount;

    @Column(name = "delete_at")
    LocalDateTime deletedAt;

    //--------------정적 팩토리 메서드 -----------------
    public static OrderItem create(
        Long orderId,
        UUID userId,
        Long eventId,
        int price,
        int quantity
    ) {
        int subtotalAmount = price * quantity;

        return OrderItem.builder()
            .orderItemId(UUID.randomUUID())
            .orderId(orderId)
            .userId(userId)
            .eventId(eventId)
            .price(price)
            .quantity(quantity)
            .subtotalAmount(subtotalAmount)
            .deletedAt(null)
            .build();
    }

    //---- 도메인 비즈니스 메서드 ------------------------------

    //아이템의 수량 직접변경 + 수량이 변경되면 subtotalAmount재계산
    public void updateQuantity(int newQuantity, int maxQuantity) {
        validateQuantity(newQuantity, maxQuantity);
        this.quantity = newQuantity;
        this.subtotalAmount = calcSubtotalAmount(this.price, newQuantity);
    }

    //이이템의 수량 증감 + 수량이 변경되면 subtotalAmount 재계산
    public void addQuantity(int delta, int maxQuantity) {
        int finalQuantity = this.quantity + delta;
        validateQuantity(finalQuantity, maxQuantity);
        this.quantity = finalQuantity;
        this.subtotalAmount = calcSubtotalAmount(this.price, quantity);
    }

    //--------------------
    //아이템 수량 검증
    private void validateQuantity(int quantity, int maxQuantity) {
        //수량은 1이상
        if (quantity <= 0) {
            throw new BusinessException(OrderErrorCode.INVALID_QUANTITY);
        }
        //1인이 구매가능한 최대 수량검증
        if (quantity > maxQuantity) {
            throw new BusinessException(OrderItemErrorCode.EXCEED_MAX_QUANTITY);
        }
    }

    //소계 합산
    private int calcSubtotalAmount(int price, int quantity) {
        return price * quantity;
    }
}



