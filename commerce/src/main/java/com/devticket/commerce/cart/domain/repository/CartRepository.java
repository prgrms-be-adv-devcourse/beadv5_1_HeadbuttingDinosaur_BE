package com.devticket.commerce.cart.domain.repository;

import com.devticket.commerce.cart.domain.model.Cart;
import java.util.Optional;

public interface CartRepository {

    Optional<Cart> findByUserId(Long id);

    Cart save(Cart cart);
}
