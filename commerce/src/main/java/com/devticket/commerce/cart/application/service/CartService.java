package com.devticket.commerce.cart.application.service;

import com.devticket.commerce.cart.application.usecase.CartUseCase;
import com.devticket.commerce.cart.domain.model.Cart;
import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
import com.devticket.commerce.cart.domain.repository.CartRepository;
import com.devticket.commerce.cart.presentation.dto.req.CartItemRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class CartService implements CartUseCase {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Override
    public boolean finByUserId(Long userId) {
        return cartRepository.findByUserId(userId).isPresent();
    }

    @Override
    public CartItemResponse save(Long userId, CartItemRequest request) {
        //장바구니 유무 확인. 없으면 장바구니 생성
        Cart cart = cartRepository.findByUserId(userId)
            .orElseGet(() -> {
                Cart newCart = Cart.create(userId);
                return cartRepository.save(newCart);
            });

        //내부api호출-> Event의 인당최대구매수량,구매가능상태값 가져오기.
        //TODO : 내부api호출로 수정 필요;
        int maxQuantityPerUser = 50;
        boolean purchasable = true;

        //CartItem 생성 또는 수량 합산
        CartItem cartItem = cartItemRepository.findByCartIdAndEventId(cart.getId(), request.eventId())
            .map(existingCartItem -> {
                // 이미 있다면 기존 아이템의 수량을 업데이트
                existingCartItem.addQuantity(request.quantity(), maxQuantityPerUser);
                return existingCartItem;
            })
            .orElseGet(() -> {
                // 없다면 새로운 CartItem 생성
                return CartItem.create(
                    cart.getId(),
                    request.eventId(),
                    request.quantity(),
                    purchasable,
                    maxQuantityPerUser
                );
            });

        CartItem savedItem = cartItemRepository.save(cartItem);

        return new CartItemResponse(
            savedItem.getEventId().toString(),
            "Event API결과값", // 실제로는 Event API 결과값 사용
            15000,                    // 실제로는 Event API 결과값 사용
            savedItem.getQuantity()
        );
    }
}
