package com.devticket.commerce.cart.domain.model;

import com.devticket.commerce.cart.domain.exception.CartErrorCode;
import com.devticket.commerce.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@Table(name = "cart_item", schema = "commerce")
// JPA 엔티티를 위한 기본생성자 필수
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 전체 생성자는 내부에서만 사용
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
    @Schema(description = "장바구니에 담은 시각, 사용자행동 Log용 필드")
    private LocalDateTime addedAt;

    //---- 정적 팩토리 메서드 ------------------
    //객체 생성
    public static CartItem create(UUID cartId, UUID eventId, int quantity, boolean purchasable,
        int maxQuantityPerUser) {

        // 상품 구매가능 상태 검증
        if (!purchasable) {
            throw new BusinessException(CartErrorCode.EVENT_ENDED);
        }

        // 수량 정책 검증 (최소 1개)
        if (quantity < 1) {
            throw new BusinessException(CartErrorCode.INVALID_QUANTITY);
        }

        // 인당 최대 구매 수량 제한 검증
        if (quantity > maxQuantityPerUser) {
            throw new BusinessException(CartErrorCode.EXCEED_MAX_PURCHASE);
        }

        LocalDateTime now = LocalDateTime.now();
        return CartItem.builder()
            .cartId(cartId)
            .eventId(eventId)
            .quantity(quantity)
            .addedAt(now)
            .build();
    }

    //---도메인 비즈니스 로직----------------------
    // 장바구니 아이템의 수량변경
    public void updateQuantity(int newQuantity, int maxQuantityPerUser) {
        //최소 수량검증 (수량은 1개 이상이어야 함)
        if (newQuantity < 1) {
            throw new BusinessException(CartErrorCode.INVALID_QUANTITY);
        }

        //인당 최대 구매수량 검증
        if (newQuantity > maxQuantityPerUser) {
            throw new BusinessException(CartErrorCode.EXCEED_MAX_PURCHASE);
        }
        this.quantity = newQuantity;
    }

    // 장바구니에 이미 있는 상품을 또 담을 때 사용.
    public void addQuantity(int additionalQuantity, int maxQuantityPerUser) {
        updateQuantity(this.quantity + additionalQuantity, maxQuantityPerUser);
    }

}