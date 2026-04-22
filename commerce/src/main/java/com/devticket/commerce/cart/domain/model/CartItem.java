package com.devticket.commerce.cart.domain.model;

import com.devticket.commerce.cart.domain.exception.CartErrorCode;
import com.devticket.commerce.common.entity.BaseEntity;
import com.devticket.commerce.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@SuperBuilder
@Table(
    name = "cart_item",
    schema = "commerce",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_cart_item_cart_event",
        columnNames = {"cart_id", "event_id"}
    )
)
// JPA 엔티티를 위한 기본생성자 필수
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 전체 생성자는 내부에서만 사용
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(name = "cart_item_id", nullable = false, updatable = false, unique = true)
    private UUID cartItemId = UUID.randomUUID();

    @Column(name = "cart_id")
    @Schema(description = "장바구니 항목")
    private Long cartId;

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
    public static CartItem create(Long cartId, UUID eventId, int quantity) {

        // 수량 정책 검증 (최소 1개)
        if (quantity < 1) {
            throw new BusinessException(CartErrorCode.INVALID_QUANTITY);
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
    // 장바구니 수량관련 정책 검증
    private static void validateQuantityRange(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(CartErrorCode.INVALID_QUANTITY);
        }
    }

    // 장바구니 아이템의 수량변경
    public void updateQuantity(int newQuantity) {
        validateQuantityRange(newQuantity);
        this.quantity = newQuantity;
    }

//    //내 수량과 외부 가격을 곱해 금액 계산
//    public int calculateLineAmount(int price) {
//        return price * this.quantity;
//    }

    // 장바구니에 이미 있는 상품을 또 담을 때 사용.
    public void addQuantity(int additionalQuantity) {
        updateQuantity(this.quantity + additionalQuantity);
    }

}