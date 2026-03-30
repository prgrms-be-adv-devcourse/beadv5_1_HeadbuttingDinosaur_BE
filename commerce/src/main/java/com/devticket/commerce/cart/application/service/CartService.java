package com.devticket.commerce.cart.application.service;

import com.devticket.commerce.cart.application.usecase.CartItemUseCase;
import com.devticket.commerce.cart.application.usecase.CartUseCase;
import com.devticket.commerce.cart.domain.exception.CartErrorCode;
import com.devticket.commerce.cart.domain.exception.EventErrorCode;
import com.devticket.commerce.cart.domain.model.Cart;
import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
import com.devticket.commerce.cart.domain.repository.CartRepository;
import com.devticket.commerce.cart.infrastructure.external.client.EventClient;
import com.devticket.commerce.cart.infrastructure.external.client.dto.InternalPurchaseValidationResponse;
import com.devticket.commerce.cart.presentation.dto.req.CartItemQuantityRequest;
import com.devticket.commerce.cart.presentation.dto.req.CartItemRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartClearResponse;
import com.devticket.commerce.cart.presentation.dto.res.CartItemDeleteResponse;
import com.devticket.commerce.cart.presentation.dto.res.CartItemDetail;
import com.devticket.commerce.cart.presentation.dto.res.CartItemQuantityResponse;
import com.devticket.commerce.cart.presentation.dto.res.CartItemResponse;
import com.devticket.commerce.cart.presentation.dto.res.CartResponse;
import com.devticket.commerce.common.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService implements CartUseCase, CartItemUseCase {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final EventClient eventClient;
    private final TransactionTemplate transactionTemplate;

    // =========================================================================
    // Public Methods (Main Flow)
    // =========================================================================

    @Override
    public boolean findByUserId(UUID userId) {
        return cartRepository.findByUserId(userId).isPresent();
    }

    @Override
    public CartItemResponse save(UUID userId, CartItemRequest request) {
        //외부 API 호출 및 정책 검증 : Event서비스호출, 상품의 구매가능상태,구매가능 수량 등 검증
        InternalPurchaseValidationResponse event = eventClient.getValidateEventStatus(request.eventId(), userId,
            request.quantity());
        handlePurchaseValidationError(event);

        //DB 작업 : 장바구니 확보와 장바구니에 아이템 담기 로직을 한개 트랜잭션 단위로 묶음.
        //Cart와 CartItem -> 객체참조x, 연관관계 매핑 없이 식별자참조.
        Cart cart = findOrCreateCart(userId);

        CartItem cartItem = transactionTemplate.execute(status ->
            addOrUpdateCartItem(cart.getId(), request)
        );

        //응답데이터 구성
        return CartItemResponse.of(cart, cartItem, event.title(), event.price());
    }

    // 장바구니 전체 조회
    @Override
    public CartResponse getCart(UUID userId) {
        // 장바구니 비어 있음 예외
        Cart cart = getCartByUserId(userId);
        // 장바구니 내 아이템 조회
        List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart.getId());

        // 아이템 -> 아이템 상세
        List<CartItemDetail> itemDetails = cartItems.stream()
            .map(cartItem -> {
                InternalPurchaseValidationResponse event =
                    eventClient.getValidateEventStatus(cartItem.getEventId(), userId, cartItem.getQuantity());
                return CartItemDetail.of(cartItem, event.title(), event.price());
            }).toList();

        return CartResponse.of(cart, itemDetails);
    }

    // 장바구니 비우기
    @Override
    public CartClearResponse clearCart(UUID userId) {
        Cart cart = getCartByUserId(userId);
        List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart.getId());
        cartItemRepository.deleteAllInBatch(cartItems);
        return CartClearResponse.of();
    }

    // 장바구니 아이템 갯수 증감
    @Override
    public CartItemQuantityResponse updateTicket(UUID userId, Long cartItemId, CartItemQuantityRequest request) {
        // 장바구니 가져오기
        Cart cart = getCartByUserId(userId);
        // 장바구니 아이템 가져오기
        CartItem cartItem = getCartItemById(cartItemId);

        // 장바구니 아이템이 유저의 장바구니 아이템인가 확인 예외
        if (!cartItem.getCartId().equals(cart.getId())) {
            throw new BusinessException(CartErrorCode.ITEM_NOT_FOUND);
        }

        cartItem.addQuantity(request.quantity());

        CartItem savedCartItem = cartItemRepository.save(cartItem);

        return CartItemQuantityResponse.of(savedCartItem);
    }

    // 장바구니 아이템 삭제
    @Override
    public CartItemDeleteResponse deleteTicket(UUID userId, Long cartItemId) {
        // 장바구니 가져오기
        Cart cart = getCartByUserId(userId);
        // 장바구니 아이템 가져오기
        CartItem cartItem = getCartItemById(cartItemId);

        // 장바구니 아이템이 유저의 장바구니 아이템인가 확인 예외
        if (!cartItem.getCartId().equals(cart.getId())) {
            throw new BusinessException(CartErrorCode.ITEM_NOT_FOUND);
        }

        cartItemRepository.deleteAllInBatch(List.of(cartItem));
        return CartItemDeleteResponse.of();
    }

    // =========================================================================
    // Private Helpers (Logic & Validation)
    // =========================================================================

    private Cart findOrCreateCart(UUID userId) {
        // 동시성문제 고려하기
        // 동일 사용자가 장바구니 담기 버튼 광클 -> 아직 존재하지 않는 데이터에 대해서는 Lock을 걸 대상이 없음
        try {
            return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.create(userId)));
        } catch (DataIntegrityViolationException e) { // 동일사용자로 cart추가 생성요청시_데이터무결성 제약조건 위반
            //이미 생성되어 있는 Cart를 조회해서 반환
            return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("장바구니 확보 실패"));

        }

    }

    private CartItem addOrUpdateCartItem(Long cartId, CartItemRequest request) {
        return cartItemRepository.findByCartIdAndEventId(cartId, request.eventId())
            .map(existingItem -> {
                log.info("[CartService] 기존 아이템 수량 추가: cartId={}, eventId={}", cartId, request.eventId());
                existingItem.addQuantity(request.quantity());
                return cartItemRepository.save(existingItem);
            })
            .orElseGet(() -> {
                log.info("[CartService] 신규 아이템 생성: cartId={}, eventId={}", cartId, request.eventId());
                CartItem newItem = CartItem.create(cartId, request.eventId(), request.quantity());
                return cartItemRepository.save(newItem);
            });
    }

    // Event에서 반환된 reason값 기준 에러 처리
    private void handlePurchaseValidationError(InternalPurchaseValidationResponse response) {
        if (Boolean.TRUE.equals(response.purchasable())) {
            return;
        }

        //InternalPurchaseValidationResponse응답데이터 reason = null허용필드
        if (response.reason() == null) {
            throw new BusinessException(EventErrorCode.INVALID_PURCHASE_REQUEST);
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


    // 장바구니 존재 유무 확인
    private Cart getCartByUserId(UUID userId) {
        return cartRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.CART_EMPTY));
    }

    // 장바구니 아이템 존재 유무 확인
    private CartItem getCartItemById(Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.ITEM_NOT_FOUND));
    }

}
