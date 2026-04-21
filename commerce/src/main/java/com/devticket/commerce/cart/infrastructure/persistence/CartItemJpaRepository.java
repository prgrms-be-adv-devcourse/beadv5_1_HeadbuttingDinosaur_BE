package com.devticket.commerce.cart.infrastructure.persistence;

import com.devticket.commerce.cart.domain.model.CartItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemJpaRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findAllByCartItemIdIn(List<UUID> cartItemIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CartItem ci WHERE ci.cartItemId IN :cartItemIds ORDER BY ci.cartItemId")
    List<CartItem> findAllByCartItemIdInWithLock(@Param("cartItemIds") List<UUID> cartItemIds);

    Optional<CartItem> findByCartIdAndEventId(Long cartId, UUID eventId);

    List<CartItem> findAllByCartId(Long cartId);

}
