package com.devticket.commerce.cart.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "cart_item", schema = "public")
// JPA 엔티티를 위한 기본생성자 필수
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @Column(name = "cart_id")
    @Schema(description = "장바구니 항목")
    private UUID cartId;

    @Column(name = "event_id", nullable = false)
    @Schema(description = "상품 ID : Event PK")
    private UUID eventId;

    @Column(nullable = false)
    @Schema(description = "수량")
    private int quantity;

    @Column(name = "added_at", nullable = false)
    @Schema(description = "장바구니에 담은 시각")
    private LocalDateTime addedAt;

    //---- 정적 팩토리 메서드 ------------------
    //객체 생성
    public static CartItem create(UUID cartId, UUID eventId, int quantity) {
        CartItem newCartItem = new CartItem();
        newCartItem.cartId = cartId;
        newCartItem.eventId = eventId;
        newCartItem.quantity = quantity;
        newCartItem.addedAt = LocalDateTime.now();
        return newCartItem;
    }

}
