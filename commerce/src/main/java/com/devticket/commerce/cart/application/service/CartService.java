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
import com.devticket.commerce.common.messaging.event.ActionLogDomainEvent;
import com.devticket.commerce.common.messaging.event.ActionType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService implements CartUseCase, CartItemUseCase {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final EventClient eventClient;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

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

        CartItem cartItem = transactionTemplate.execute(status -> {
            CartItem item = addOrUpdateCartItem(cart.getId(), request);
            // action.log 발행 (트랜잭션 활성 상태에서 호출 — AFTER_COMMIT 트리거)
            publishCartActionLog(userId, request.eventId(), ActionType.CART_ADD,
                request.quantity(), event.price());
            return item;
        });

        //응답데이터 구성
        return CartItemResponse.of(cart, cartItem, event.title(), event.price());
    }

    // 장바구니 전체 조회 — Cart row가 없으면 빈 응답 반환 (#416: 신규 사용자 400 → 200)
    @Override
    public CartResponse getCart(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return CartResponse.empty();
        }

        List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart.getId());

        List<CartItemDetail> itemDetails = cartItems.stream()
            .map(cartItem -> {
                InternalPurchaseValidationResponse event =
                    eventClient.getValidateEventStatus(cartItem.getEventId(), userId, cartItem.getQuantity());
                return CartItemDetail.of(cartItem, event.title(), event.price());
            }).toList();

        return CartResponse.of(cart, itemDetails);
    }

    // 장바구니 비우기 — Cart row가 없으면 멱등 성공 반환 (#416: 이미 비어있음도 성공으로 취급)
    @Override
    @Transactional
    public CartClearResponse clearCart(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return CartClearResponse.of();
        }
        List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart.getId());
        if (!cartItems.isEmpty()) {
            // N회 CART_REMOVE 발행 (아이템별 eventId 보존 — AI 이탈 분석 용)
            for (CartItem item : cartItems) {
                Integer price = fetchEventPriceSafely(item.getEventId(), userId, item.getQuantity());
                publishCartActionLog(userId, item.getEventId(), ActionType.CART_REMOVE,
                    item.getQuantity(), price);
            }
            cartItemRepository.deleteAllInBatch(cartItems);
        }
        return CartClearResponse.of();
    }

    // 장바구니 아이템 갯수 증감
    @Override
    @Transactional
    public CartItemQuantityResponse updateTicket(UUID userId, Long cartItemId, CartItemQuantityRequest request) {
        // 장바구니 가져오기 — Cart 없으면 ITEM_NOT_FOUND (#416)
        Cart cart = getCartOrThrowItemNotFound(userId);
        // 장바구니 아이템 가져오기
        CartItem cartItem = getCartItemById(cartItemId);

        // 변경 후 수량
        int newQuantity = cartItem.getQuantity() + request.quantity();

        // 장바구니 아이템이 유저의 장바구니 아이템인가 확인 예외
        if (!cartItem.getCartId().equals(cart.getId())) {
            throw new BusinessException(CartErrorCode.ITEM_NOT_FOUND);
        }

        // Event 구매 가능 검증 - 1) Event 현재 상태 가져 오기
        InternalPurchaseValidationResponse event = eventClient.getValidateEventStatus(cartItem.getEventId(), userId,
            newQuantity);
        // Event 구매 가능 검증 - 2) Event의 구매 가능 상태 확인
        handlePurchaseValidationError(event);

        cartItem.addQuantity(request.quantity());

        CartItem savedCartItem = cartItemRepository.save(cartItem);

        // 양수 → CART_ADD, 음수 → CART_REMOVE, 0 → 미발행 (publishCartActionLog 내부 처리)
        int delta = request.quantity();
        if (delta != 0) {
            ActionType actionType = delta > 0 ? ActionType.CART_ADD : ActionType.CART_REMOVE;
            publishCartActionLog(userId, cartItem.getEventId(), actionType,
                Math.abs(delta), event.price());
        }

        return CartItemQuantityResponse.of(savedCartItem);
    }

    // 장바구니 아이템 삭제
    @Override
    @Transactional
    public CartItemDeleteResponse deleteTicket(UUID userId, Long cartItemId) {
        // 장바구니 가져오기 — Cart 없으면 ITEM_NOT_FOUND (#416)
        Cart cart = getCartOrThrowItemNotFound(userId);
        // 장바구니 아이템 가져오기
        CartItem cartItem = getCartItemById(cartItemId);

        // 장바구니 아이템이 유저의 장바구니 아이템인가 확인 예외
        if (!cartItem.getCartId().equals(cart.getId())) {
            throw new BusinessException(CartErrorCode.ITEM_NOT_FOUND);
        }

        // action.log CART_REMOVE 발행 (price 조회는 totalAmount 산출 목적만 — 실패 시 null)
        Integer price = fetchEventPriceSafely(cartItem.getEventId(), userId, cartItem.getQuantity());
        publishCartActionLog(userId, cartItem.getEventId(), ActionType.CART_REMOVE,
            cartItem.getQuantity(), price);

        cartItemRepository.deleteAllInBatch(List.of(cartItem));
        return CartItemDeleteResponse.of();
    }

    // =========================================================================
    // Private Helpers (Logic & Validation)
    // =========================================================================

    // action.log Spring 이벤트 발행. quantity=0 미발행. price null 시 totalAmount=null.
    private void publishCartActionLog(UUID userId, UUID eventId, ActionType actionType,
                                      int quantity, Integer price) {
        if (quantity == 0) {
            return;
        }
        Long totalAmount = (price != null) ? (long) price * quantity : null;
        eventPublisher.publishEvent(new ActionLogDomainEvent(
            userId, eventId, actionType,
            null, null, null,
            quantity, totalAmount, Instant.now()
        ));
    }

    // totalAmount 산출용 price 조회 — 실패 시 null (비즈니스 영향 없음, action.log 선택 필드 누락만)
    private Integer fetchEventPriceSafely(UUID eventId, UUID userId, int quantity) {
        try {
            return eventClient.getValidateEventStatus(eventId, userId, quantity).price();
        } catch (Exception e) {
            log.warn("[CartService] action.log price 조회 실패 — totalAmount=null. eventId={}", eventId, e);
            return null;
        }
    }

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
        try {
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
        } catch (DataIntegrityViolationException e) {
            // 광클 race: 동시 INSERT 중 다른 트랜잭션이 먼저 커밋 → (cart_id, event_id) UNIQUE 위반
            // findOrCreateCart 와 동일한 복구 패턴 — 재조회 후 수량 합산
            log.warn("[CartService] addOrUpdateCartItem UNIQUE 충돌 감지 — race 복구 재조회. cartId={}, eventId={}",
                cartId, request.eventId());
            CartItem existing = cartItemRepository.findByCartIdAndEventId(cartId, request.eventId())
                .orElseThrow(() -> new RuntimeException("장바구니 아이템 확보 실패", e));
            existing.addQuantity(request.quantity());
            return cartItemRepository.save(existing);
        }
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


    // Cart row 없음도 단건 ITEM_NOT_FOUND 로 통일 (#416: CART_EMPTY 400 대신 404 매핑)
    private Cart getCartOrThrowItemNotFound(UUID userId) {
        return cartRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.ITEM_NOT_FOUND));
    }

    // 장바구니 아이템 존재 유무 확인
    private CartItem getCartItemById(Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.ITEM_NOT_FOUND));
    }

}
