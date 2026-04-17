package com.devticket.commerce.cart.domain.repository;

import com.devticket.commerce.cart.domain.model.CartItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository {

    CartItem save(CartItem cartItem);

    List<CartItem> findAllById(List<Long> cartItemIds);

    List<CartItem> findAllByCartItemId(List<UUID> cartItemIds);

    // 주문 생성 시 CartItem 동시 변경 차단용 — @Transactional 안에서만 호출, ORDER BY cartItemId로 락 순서 고정
    List<CartItem> findAllByCartItemIdWithLock(List<UUID> cartItemIds);

    Optional<CartItem> findByCartIdAndEventId(Long cartId, UUID eventId);

    //장바구니 아이템 삭제
    void deleteAllInBatch(List<CartItem> cartItems);

    // 장바구니 전체 조회
    List<CartItem> findAllByCartId(Long cartId);

    // 장바구니 아이템 조회
    Optional<CartItem> findById(Long cartItemId);
}
