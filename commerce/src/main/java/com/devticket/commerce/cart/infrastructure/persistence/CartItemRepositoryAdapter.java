package com.devticket.commerce.cart.infrastructure.persistence;

import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CartItemRepositoryAdapter implements CartItemRepository {

    private final CartItemJpaRepository cartItemJpaRepository;

    @Override
    public CartItem save(CartItem cartItem) {
        return cartItemJpaRepository.save(cartItem);
    }

    @Override
    public List<CartItem> findAllById(List<Long> cartItemIds) {
        return cartItemJpaRepository.findAllById(cartItemIds);
    }

    @Override
    public List<CartItem> findAllByCartItemId(List<UUID> cartItemIds) {
        return cartItemJpaRepository.findAllByCartItemIdIn(cartItemIds);
    }

    @Override
    public List<CartItem> findAllByCartItemIdWithLock(List<UUID> cartItemIds) {
        return cartItemJpaRepository.findAllByCartItemIdInWithLock(cartItemIds);
    }

    @Override
    public Optional<CartItem> findByCartIdAndEventId(Long cartId, UUID eventId) {
        return cartItemJpaRepository.findByCartIdAndEventId(cartId, eventId);
    }

    @Override
    public void deleteAllInBatch(List<CartItem> cartItems) {
        cartItemJpaRepository.deleteAllInBatch(cartItems);
    }

    @Override
    public List<CartItem> findAllByCartId(Long cartId) {
        return cartItemJpaRepository.findAllByCartId(cartId);
    }

    @Override
    public Optional<CartItem> findById(Long cartItemId) {
        return cartItemJpaRepository.findById(cartItemId);
    }
}
