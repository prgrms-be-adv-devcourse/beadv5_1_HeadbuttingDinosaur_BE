package com.devticket.commerce.cart.infrastructure.persistence;

import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CartItemRepositoryAdapter implements CartItemRepository {

    public final CartItemJpaRepository cartItemJpaRepository;

    @Override
    public CartItem save(CartItem cartItem) {
        return cartItemJpaRepository.save(cartItem);
    }

    @Override
    public Optional<CartItem> findByCartIdAndEventId(Long cartId, Long eventId) {
        return cartItemJpaRepository.findByCartIdAndEventId(cartId, eventId);
    }
}
