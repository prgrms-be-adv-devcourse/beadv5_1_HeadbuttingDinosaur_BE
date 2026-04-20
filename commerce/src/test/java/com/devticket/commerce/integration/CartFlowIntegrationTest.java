package com.devticket.commerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.devticket.commerce.cart.application.usecase.CartItemUseCase;
import com.devticket.commerce.cart.application.usecase.CartUseCase;
import com.devticket.commerce.cart.domain.exception.CartErrorCode;
import com.devticket.commerce.cart.infrastructure.external.client.EventClient;
import com.devticket.commerce.cart.presentation.dto.req.CartItemQuantityRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartClearResponse;
import com.devticket.commerce.cart.presentation.dto.res.CartResponse;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.messaging.KafkaTopics;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * IT-#416 장바구니 빈 카트 UX 일관성 통합테스트.
 *
 * <p>검증 대상:
 * <ul>
 *   <li>신규 사용자(Cart row 없음) getCart → 200 빈 응답
 *   <li>Cart 없는 사용자 clearCart → 200 멱등 성공
 *   <li>Cart 없는 사용자 updateTicket/deleteTicket → 404 ITEM_NOT_FOUND
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaTopics.ORDER_CREATED},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class CartFlowIntegrationTest {

    @MockitoBean private LockProvider lockProvider;
    @MockitoBean private EventClient eventClient;

    @Autowired private CartUseCase cartUseCase;
    @Autowired private CartItemUseCase cartItemUseCase;

    @BeforeEach
    void setUp() {
        given(lockProvider.lock(any())).willReturn(Optional.of(() -> {}));
    }

    @Test
    @DisplayName("IT-#416-A: 신규 사용자 getCart → 200 빈 응답")
    void getCartReturnsEmptyForNewUser() {
        UUID userId = UUID.randomUUID();

        CartResponse response = cartUseCase.getCart(userId);

        assertThat(response.cartId()).isNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.totalAmount()).isZero();
    }

    @Test
    @DisplayName("IT-#416-B: Cart 없는 사용자 clearCart → 200 멱등 성공")
    void clearCartIsIdempotentForNewUser() {
        UUID userId = UUID.randomUUID();

        CartClearResponse response = cartUseCase.clearCart(userId);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("IT-#416-C: Cart 없는 사용자 updateTicket → ITEM_NOT_FOUND 404")
    void updateTicketThrowsItemNotFoundForNewUser() {
        UUID userId = UUID.randomUUID();
        CartItemQuantityRequest request = new CartItemQuantityRequest(1);

        assertThatThrownBy(() -> cartItemUseCase.updateTicket(userId, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CartErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("IT-#416-D: Cart 없는 사용자 deleteTicket → ITEM_NOT_FOUND 404")
    void deleteTicketThrowsItemNotFoundForNewUser() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> cartItemUseCase.deleteTicket(userId, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CartErrorCode.ITEM_NOT_FOUND);
    }
}
