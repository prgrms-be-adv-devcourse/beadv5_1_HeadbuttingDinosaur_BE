package com.devticket.commerce.cart.application.service;

import com.devticket.commerce.cart.application.usecase.CartUseCase;
import com.devticket.commerce.cart.domain.exception.CartErrorCode;
import com.devticket.commerce.cart.domain.exception.EventErrorCode;
import com.devticket.commerce.cart.domain.model.Cart;
import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
import com.devticket.commerce.cart.domain.repository.CartRepository;
import com.devticket.commerce.cart.infrastructure.external.client.EventClient;
import com.devticket.commerce.cart.infrastructure.external.client.dto.InternalPurchaseValidationResponse;
import com.devticket.commerce.cart.presentation.dto.req.CartItemRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartItemResponse;
import com.devticket.commerce.common.exception.BusinessException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService implements CartUseCase {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final EventClient eventClient;

    // =========================================================================
    // Public Methods (Main Flow)
    // =========================================================================

    @Override
    public boolean finByUserId(Long userId) {
        return cartRepository.findByUserId(userId).isPresent();
    }

    @Override
    @Transactional
    public CartItemResponse save(Long userId, CartItemRequest request) {
        //장바구니 확보
        Cart cart = findOrCreateCart(userId);

        //외부 API 호출 및 정책 검증 : Event서비스호출, 상품의 구매가능상태,구매가능 수량 등 검증
        InternalPurchaseValidationResponse event = eventClient.getValidateEventStatus(request.eventId(), userId,
            request.quantity());
        handlePurchaseValidationError(event);

        //도메인 로직 : 장바구니 아이템 추가 또는 업데이트(이미 장바구니에 존재하는 상품을 또 담는 경우 수량변경)
        CartItem savedItem = addOrUpdateCartItem(cart.getId(), request);

        //응답데이터 구성
        return CartItemResponse.of(cart, savedItem, event);
    }

    // =========================================================================
    // Private Helpers (Logic & Validation)
    // =========================================================================

    private Cart findOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
            .orElseGet(() -> cartRepository.save(Cart.create(userId)));
    }

    private CartItem addOrUpdateCartItem(Long cartId, CartItemRequest request) {
        return cartItemRepository.findByCartIdAndEventId(cartId, request.eventId())
            .map(existingItem -> {
                existingItem.addQuantity(request.quantity());
                return existingItem;
            })
            .orElseGet(() -> {
                CartItem newItem = CartItem.create(cartId, request.eventId(), request.quantity());
                return cartItemRepository.save(newItem);
            });
    }

    // Event에서 반환된 reason값 기준 에러 처리
    private void handlePurchaseValidationError(InternalPurchaseValidationResponse response) {
        if (Boolean.TRUE.equals(response.purchasable())) {
            return;
        }

        // Event 구매가능 상태별 예외처리
        switch (response.reason()) {
            case "SALE_ENDED" -> throw new BusinessException(CartErrorCode.EVENT_ENDED);
            case "SOLD_OUT", "INSUFFICIENT_STOCK" -> throw new BusinessException(CartErrorCode.OUT_OF_STOCK);
            case "EVENT_CANCELLED" -> throw new BusinessException(EventErrorCode.EVENT_ALREADY_CANCELLED);
            case "MAX_PER_USER_EXCEEDED" -> throw new BusinessException(CartErrorCode.EXCEED_MAX_PURCHASE);
            default -> {
                log.warn("[CartService] Unknown validation reason from Event Service: {}", response.reason());
                throw new BusinessException(EventErrorCode.INVALID_PURCHASE_REQUEST);
            }
        }
    }

}
