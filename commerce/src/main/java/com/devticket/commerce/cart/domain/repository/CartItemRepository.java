package com.devticket.commerce.cart.domain.repository;

import com.devticket.commerce.cart.domain.model.CartItem;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository {

    CartItem save(CartItem cartItem);

    List<CartItem> findAllById(List<Long> cartItemIds);

    Optional<CartItem> findByCartIdAndEventId(Long cartId, Long eventId);

    //장바구니 아이템 삭제
    void deleteAllInBatch(List<CartItem> cartItems);
}
