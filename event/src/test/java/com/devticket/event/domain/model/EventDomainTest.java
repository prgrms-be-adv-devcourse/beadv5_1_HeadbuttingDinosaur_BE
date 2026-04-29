package com.devticket.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.EventErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Event 도메인 메서드 단위 테스트 — deductStock / restoreStock 분기 격리 검증.
 *
 * audit-report 2026-04-28 P0 #2 — 도메인 분기는 그동안 EventInternalService /
 * StockRestoreService / OrderCancelledService / RefundStockRestoreService 호출
 * 경유로만 간접 검증되어 회귀 추적이 어려웠음. 도메인 진입점 직격 검증으로
 * 후속 흐름의 진실 원본을 격리.
 */
@DisplayName("Event 도메인 — deductStock / restoreStock 분기")
class EventDomainTest {

    private static final int TOTAL_QUANTITY = 10;
    private static final int MAX_PER_USER = 5;

    private Event onSaleEvent(int totalQuantity, int maxPerUser) {
        return Event.create(
            UUID.randomUUID(), "도메인 테스트 이벤트", "설명", "서울",
            LocalDateTime.now().plusDays(15),
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(10),
            10000, totalQuantity, maxPerUser, EventCategory.CONFERENCE
        );
    }

    private void setStatus(Event event, EventStatus status) {
        ReflectionTestUtils.setField(event, "status", status);
    }

    private void setRemaining(Event event, int remaining) {
        ReflectionTestUtils.setField(event, "remainingQuantity", remaining);
    }

    private void setSaleWindow(Event event, LocalDateTime start, LocalDateTime end) {
        ReflectionTestUtils.setField(event, "saleStartAt", start);
        ReflectionTestUtils.setField(event, "saleEndAt", end);
    }

    @Nested
    @DisplayName("deductStock — 차감 분기")
    class DeductStock {

        @Test
        @DisplayName("quantity < 1 이면 INVALID_STOCK_QUANTITY")
        void quantityBelowOne_throwsInvalid() {
            Event event = onSaleEvent(TOTAL_QUANTITY, MAX_PER_USER);

            assertThatThrownBy(() -> event.deductStock(0))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", EventErrorCode.INVALID_STOCK_QUANTITY);
        }

        @Test
        @DisplayName("판매 시작 전이면 PURCHASE_NOT_ALLOWED")
        void beforeSaleStart_throwsNotAllowed() {
            Event event = onSaleEvent(TOTAL_QUANTITY, MAX_PER_USER);
            setSaleWindow(event,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(10));

            assertThatThrownBy(() -> event.deductStock(1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", EventErrorCode.PURCHASE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("판매 종료 후이면 PURCHASE_NOT_ALLOWED")
        void afterSaleEnd_throwsNotAllowed() {
            Event event = onSaleEvent(TOTAL_QUANTITY, MAX_PER_USER);
            setSaleWindow(event,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(1));

            assertThatThrownBy(() -> event.deductStock(1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", EventErrorCode.PURCHASE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("status 가 ON_SALE 이 아니면 PURCHASE_NOT_ALLOWED")
        void nonOnSaleStatus_throwsNotAllowed() {
            Event event = onSaleEvent(TOTAL_QUANTITY, MAX_PER_USER);
            setStatus(event, EventStatus.SOLD_OUT);

            assertThatThrownBy(() -> event.deductStock(1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", EventErrorCode.PURCHASE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("quantity 가 maxQuantity(인당 한도) 초과이면 MAX_QUANTITY_EXCEEDED")
        void exceedsMaxPerUser_throwsMaxExceeded() {
            Event event = onSaleEvent(100, MAX_PER_USER);

            assertThatThrownBy(() -> event.deductStock(MAX_PER_USER + 1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", EventErrorCode.MAX_QUANTITY_EXCEEDED);
        }

        @Test
        @DisplayName("remainingQuantity 부족 시 OUT_OF_STOCK (max 분기 우회 — maxPerUser 충분히 큼)")
        void remainingLessThanRequested_throwsOutOfStock() {
            Event event = onSaleEvent(TOTAL_QUANTITY, 100);
            setRemaining(event, 3);

            assertThatThrownBy(() -> event.deductStock(5))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", EventErrorCode.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("정상 차감 시 remainingQuantity 만큼 감소하고 ON_SALE 유지")
        void normal_reducesRemainingAndKeepsOnSale() {
            Event event = onSaleEvent(TOTAL_QUANTITY, MAX_PER_USER);

            event.deductStock(3);

            assertThat(event.getRemainingQuantity()).isEqualTo(7);
            assertThat(event.getStatus()).isEqualTo(EventStatus.ON_SALE);
        }

        @Test
        @DisplayName("remainingQuantity 가 0 으로 떨어지면 SOLD_OUT 으로 전이")
        void remainingHitsZero_transitionsToSoldOut() {
            Event event = onSaleEvent(TOTAL_QUANTITY, 100);
            setRemaining(event, 5);

            event.deductStock(5);

            assertThat(event.getRemainingQuantity()).isZero();
            assertThat(event.getStatus()).isEqualTo(EventStatus.SOLD_OUT);
        }
    }

    @Nested
    @DisplayName("restoreStock — 복원 분기")
    class RestoreStock {

        @Test
        @DisplayName("quantity < 1 이면 INVALID_STOCK_QUANTITY")
        void quantityBelowOne_throwsInvalid() {
            Event event = onSaleEvent(TOTAL_QUANTITY, MAX_PER_USER);

            assertThatThrownBy(() -> event.restoreStock(0))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", EventErrorCode.INVALID_STOCK_QUANTITY);
        }

        @Test
        @DisplayName("status == CANCELLED 이면 CANNOT_CHANGE_STATUS")
        void cancelledStatus_throwsCannotChange() {
            Event event = onSaleEvent(TOTAL_QUANTITY, MAX_PER_USER);
            setStatus(event, EventStatus.CANCELLED);

            assertThatThrownBy(() -> event.restoreStock(1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", EventErrorCode.CANNOT_CHANGE_STATUS);
        }

        @Test
        @DisplayName("status == FORCE_CANCELLED 이면 CANNOT_CHANGE_STATUS")
        void forceCancelledStatus_throwsCannotChange() {
            Event event = onSaleEvent(TOTAL_QUANTITY, MAX_PER_USER);
            setStatus(event, EventStatus.FORCE_CANCELLED);

            assertThatThrownBy(() -> event.restoreStock(1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", EventErrorCode.CANNOT_CHANGE_STATUS);
        }

        @Test
        @DisplayName("remainingQuantity + quantity 가 totalQuantity 를 초과하면 totalQuantity 로 클램프")
        void overflow_clampsToTotalQuantity() {
            Event event = onSaleEvent(TOTAL_QUANTITY, MAX_PER_USER);
            setRemaining(event, 8);

            event.restoreStock(50);

            assertThat(event.getRemainingQuantity()).isEqualTo(TOTAL_QUANTITY);
        }

        @Test
        @DisplayName("SOLD_OUT 상태에서 복원으로 remainingQuantity > 0 되면 ON_SALE 로 전환")
        void fromSoldOut_transitionsToOnSale() {
            Event event = onSaleEvent(TOTAL_QUANTITY, MAX_PER_USER);
            setStatus(event, EventStatus.SOLD_OUT);
            setRemaining(event, 0);

            event.restoreStock(3);

            assertThat(event.getRemainingQuantity()).isEqualTo(3);
            assertThat(event.getStatus()).isEqualTo(EventStatus.ON_SALE);
        }

        @Test
        @DisplayName("ON_SALE 상태에서 복원 시 status 유지하고 remainingQuantity 만 증가")
        void onSaleNormalRestore_keepsStatusAndIncreasesRemaining() {
            Event event = onSaleEvent(TOTAL_QUANTITY, MAX_PER_USER);
            setRemaining(event, 5);

            event.restoreStock(3);

            assertThat(event.getRemainingQuantity()).isEqualTo(8);
            assertThat(event.getStatus()).isEqualTo(EventStatus.ON_SALE);
        }
    }
}
