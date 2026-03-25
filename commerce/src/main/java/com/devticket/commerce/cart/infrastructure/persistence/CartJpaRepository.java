package com.devticket.commerce.cart.infrastructure.persistence;

import com.devticket.commerce.cart.domain.model.Cart;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartJpaRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);
}
