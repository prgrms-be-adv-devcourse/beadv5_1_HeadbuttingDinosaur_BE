package com.devticket.commerce.cart.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.devticket.commerce.cart.domain.exception.CartErrorCode;
import com.devticket.commerce.cart.domain.model.Cart;
import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
import com.devticket.commerce.cart.domain.repository.CartRepository;
import com.devticket.commerce.cart.infrastructure.external.client.EventClient;
import com.devticket.commerce.cart.infrastructure.external.client.dto.InternalPurchaseValidationResponse;
import com.devticket.commerce.cart.presentation.dto.req.CartItemQuantityRequest;
import com.devticket.commerce.cart.presentation.dto.req.CartItemRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartItemQuantityResponse;
import com.devticket.commerce.cart.presentation.dto.res.CartItemResponse;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private EventClient eventClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CartService cartService;

    private static final Long CART_ID = 1L;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager noopTxManager = new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition def) { return new SimpleTransactionStatus(); }
            @Override public void commit(TransactionStatus status) {}
            @Override public void rollback(TransactionStatus status) {}
        };

        cartService = new CartService(
            cartRepository, cartItemRepository, ticketRepository,
            eventClient, new TransactionTemplate(noopTxManager), eventPublisher
        );
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private Cart cart(UUID userId) {
        return Cart.builder().id(CART_ID).userId(userId).build();
    }

    private CartItem cartItem(UUID eventId, int quantity) {
        return CartItem.create(CART_ID, eventId, quantity);
    }

    private InternalPurchaseValidationResponse purchasableEvent(UUID eventId, int maxQuantity) {
        return new InternalPurchaseValidationResponse(eventId, true, null, maxQuantity, "테스트 이벤트", 10_000);
    }

    // ── save — 1인당 한도 검증 ─────────────────────────────────────────────────

    @Nested
    class save_한도_검증 {

        @Test
        void 장바구니와_구매_이력이_없을_때_한도_이내면_성공한다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            CartItem newItem = cartItem(eventId, 1);

            given(eventClient.getValidateEventStatus(eventId, userId, 1))
                .willReturn(purchasableEvent(eventId, 2));
            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findByCartIdAndEventId(CART_ID, eventId)).willReturn(Optional.empty());
            given(ticketRepository.countByUserIdAndEventIdAndStatus(userId, eventId, TicketStatus.ISSUED)).willReturn(0);
            given(cartItemRepository.save(any())).willReturn(newItem);

            // 0(cart) + 0(issued) + 1(new) = 1 <= 2
            CartItemResponse response = cartService.save(userId, new CartItemRequest(eventId, 1));

            assertThat(response).isNotNull();
        }

        @Test
        void 장바구니_수량_구매_이력_새_요청_합산이_한도_이내면_성공한다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            CartItem existing = cartItem(eventId, 2);

            given(eventClient.getValidateEventStatus(eventId, userId, 1))
                .willReturn(purchasableEvent(eventId, 5));
            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findByCartIdAndEventId(CART_ID, eventId)).willReturn(Optional.of(existing));
            given(ticketRepository.countByUserIdAndEventIdAndStatus(userId, eventId, TicketStatus.ISSUED)).willReturn(1);
            given(cartItemRepository.save(any())).willReturn(existing);

            // 2(cart) + 1(issued) + 1(new) = 4 <= 5
            CartItemResponse response = cartService.save(userId, new CartItemRequest(eventId, 1));

            assertThat(response).isNotNull();
        }

        @Test
        void 장바구니_기존_수량과_새_요청_합산이_한도_초과면_예외를_던진다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            CartItem existing = cartItem(eventId, 2);

            given(eventClient.getValidateEventStatus(eventId, userId, 1))
                .willReturn(purchasableEvent(eventId, 2));
            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findByCartIdAndEventId(CART_ID, eventId)).willReturn(Optional.of(existing));
            given(ticketRepository.countByUserIdAndEventIdAndStatus(userId, eventId, TicketStatus.ISSUED)).willReturn(0);

            // 2(cart) + 0(issued) + 1(new) = 3 > 2
            assertThatThrownBy(() -> cartService.save(userId, new CartItemRequest(eventId, 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(CartErrorCode.EXCEED_MAX_PURCHASE));
        }

        @Test
        void 구매_완료_수량과_새_요청_합산이_한도_초과면_예외를_던진다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();

            given(eventClient.getValidateEventStatus(eventId, userId, 1))
                .willReturn(purchasableEvent(eventId, 2));
            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findByCartIdAndEventId(CART_ID, eventId)).willReturn(Optional.empty());
            given(ticketRepository.countByUserIdAndEventIdAndStatus(userId, eventId, TicketStatus.ISSUED)).willReturn(2);

            // 0(cart) + 2(issued) + 1(new) = 3 > 2
            assertThatThrownBy(() -> cartService.save(userId, new CartItemRequest(eventId, 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(CartErrorCode.EXCEED_MAX_PURCHASE));
        }

        @Test
        void 각각은_한도_이내여도_세_수량_합산이_초과면_예외를_던진다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            CartItem existing = cartItem(eventId, 1);

            given(eventClient.getValidateEventStatus(eventId, userId, 2))
                .willReturn(purchasableEvent(eventId, 3));
            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findByCartIdAndEventId(CART_ID, eventId)).willReturn(Optional.of(existing));
            given(ticketRepository.countByUserIdAndEventIdAndStatus(userId, eventId, TicketStatus.ISSUED)).willReturn(1);

            // 1(cart) + 1(issued) + 2(new) = 4 > 3
            assertThatThrownBy(() -> cartService.save(userId, new CartItemRequest(eventId, 2)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(CartErrorCode.EXCEED_MAX_PURCHASE));
        }

        @Test
        void 구매_이력_집계는_ISSUED_상태만_조회한다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            CartItem newItem = cartItem(eventId, 1);

            given(eventClient.getValidateEventStatus(eventId, userId, 1))
                .willReturn(purchasableEvent(eventId, 2));
            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findByCartIdAndEventId(CART_ID, eventId)).willReturn(Optional.empty());
            given(ticketRepository.countByUserIdAndEventIdAndStatus(userId, eventId, TicketStatus.ISSUED)).willReturn(0);
            given(cartItemRepository.save(any())).willReturn(newItem);

            cartService.save(userId, new CartItemRequest(eventId, 1));

            then(ticketRepository).should()
                .countByUserIdAndEventIdAndStatus(userId, eventId, TicketStatus.ISSUED);
        }
    }

    // ── updateTicket — 1인당 한도 검증 ──────────────────────────────────────

    @Nested
    class updateTicket_한도_검증 {

        @Test
        void 변경_후_장바구니_수량과_구매_이력_합산이_한도_이내면_성공한다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            Long cartItemId = 10L;
            CartItem existing = cartItem(eventId, 1); // 기존 1개

            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findById(cartItemId)).willReturn(Optional.of(existing));
            // newQuantity = 1(기존) + 1(delta) = 2
            given(eventClient.getValidateEventStatus(eventId, userId, 2))
                .willReturn(purchasableEvent(eventId, 3));
            given(ticketRepository.countByUserIdAndEventIdAndStatus(userId, eventId, TicketStatus.ISSUED)).willReturn(1);
            given(cartItemRepository.save(any())).willReturn(existing);

            // 2(newQty) + 1(issued) = 3 <= 3
            CartItemQuantityResponse response = cartService.updateTicket(
                userId, cartItemId, new CartItemQuantityRequest(1));

            assertThat(response).isNotNull();
        }

        @Test
        void 변경_후_장바구니_수량과_구매_이력_합산이_한도_초과면_예외를_던진다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            Long cartItemId = 10L;
            CartItem existing = cartItem(eventId, 1); // 기존 1개

            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findById(cartItemId)).willReturn(Optional.of(existing));
            // newQuantity = 1(기존) + 1(delta) = 2
            given(eventClient.getValidateEventStatus(eventId, userId, 2))
                .willReturn(purchasableEvent(eventId, 2));
            given(ticketRepository.countByUserIdAndEventIdAndStatus(userId, eventId, TicketStatus.ISSUED)).willReturn(1);

            // 2(newQty) + 1(issued) = 3 > 2
            assertThatThrownBy(() -> cartService.updateTicket(
                    userId, cartItemId, new CartItemQuantityRequest(1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(CartErrorCode.EXCEED_MAX_PURCHASE));
        }

        @Test
        void 구매_이력_집계는_ISSUED_상태만_조회한다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            Long cartItemId = 10L;
            CartItem existing = cartItem(eventId, 1);

            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findById(cartItemId)).willReturn(Optional.of(existing));
            given(eventClient.getValidateEventStatus(eventId, userId, 2))
                .willReturn(purchasableEvent(eventId, 3));
            given(ticketRepository.countByUserIdAndEventIdAndStatus(userId, eventId, TicketStatus.ISSUED)).willReturn(0);
            given(cartItemRepository.save(any())).willReturn(existing);

            cartService.updateTicket(userId, cartItemId, new CartItemQuantityRequest(1));

            then(ticketRepository).should()
                .countByUserIdAndEventIdAndStatus(userId, eventId, TicketStatus.ISSUED);
        }
    }
}
