package com.devticket.commerce.cart.domain.repository;

import com.devticket.commerce.cart.domain.model.CartItem;
import java.util.Optional;

public interface CartItemRepository {

    CartItem save(CartItem cartItem);

    Optional<CartItem> findByCartIdAndEventId(Long cartId, Long eventId);
}
