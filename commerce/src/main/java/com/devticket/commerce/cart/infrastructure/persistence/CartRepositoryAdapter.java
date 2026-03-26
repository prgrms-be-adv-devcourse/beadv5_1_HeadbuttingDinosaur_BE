package com.devticket.commerce.cart.infrastructure.persistence;

import com.devticket.commerce.cart.domain.model.Cart;
import com.devticket.commerce.cart.domain.repository.CartRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CartRepositoryAdapter implements CartRepository {

    public final CartJpaRepository cartJpaRepository;

    @Override
    public Optional<Cart> findByUserId(Long userId) {
        return cartJpaRepository.findByUserId(userId);
    }

    //장바구니 생성
    @Override
    public Cart save(Cart cart) {
        return cartJpaRepository.save(cart);
    }


}
