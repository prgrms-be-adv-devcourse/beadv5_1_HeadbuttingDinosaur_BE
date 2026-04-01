package com.devticket.commerce.cart.infrastructure.persistence;

import com.devticket.commerce.cart.domain.model.CartItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemJpaRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findAllByCartItemIdIn(List<UUID> cartItemIds);

    Optional<CartItem> findByCartIdAndEventId(Long cartId, UUID eventId);

    List<CartItem> findAllByCartId(Long cartId);
}
