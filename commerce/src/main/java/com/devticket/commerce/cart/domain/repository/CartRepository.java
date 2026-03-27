package com.devticket.commerce.cart.domain.repository;

import com.devticket.commerce.cart.domain.model.Cart;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository {

    Optional<Cart> findByUserId(UUID userId);

    Cart save(Cart cart);
}
