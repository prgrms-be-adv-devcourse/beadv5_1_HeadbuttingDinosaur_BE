package com.devticket.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.model.Event;
import com.devticket.event.infrastructure.client.MemberClient;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.infrastructure.search.EventSearchRepository;
import com.devticket.event.presentation.dto.internal.InternalBulkStockAdjustmentRequest;
import com.devticket.event.presentation.dto.internal.InternalBulkStockAdjustmentRequest.StockAdjustmentItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * EventInternalService.adjustStockBulk() — 동시성 직렬화 + 부분 실패 시 전체 롤백 검증.
 *
 * 주의: 동시성 시나리오는 별도 스레드가 자체 트랜잭션으로 commit 해야 하므로
 * 클래스 레벨 @Transactional(NEVER) 로 @DataJpaTest 의 기본 테스트 트랜잭션을 비활성화한다.
 * 데이터 격리는 @AfterEach 의 deleteAll() 로 보장한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(EventInternalService.class)
@Transactional(propagation = Propagation.NEVER)
@DisplayName("EventInternalService.adjustStockBulk — 동시성 / 원자적 롤백")
class EventInternalServiceConcurrencyTest {

    @Autowired private EventInternalService eventInternalService;
    @Autowired private EventRepository eventRepository;

    @MockitoBean private MemberClient memberClient;
    @MockitoBean private EventSearchRepository eventSearchRepository;

    @AfterEach
    void cleanup() {
        eventRepository.deleteAll();
    }

    private Event saveEvent(int totalQuantity, int remaining, int maxPerUser) {
        Event event = Event.create(
            UUID.randomUUID(), "동시성 테스트 이벤트", "설명", "서울",
            LocalDateTime.now().plusDays(15),
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(10),
            10000, totalQuantity, maxPerUser, EventCategory.CONFERENCE
        );
        ReflectionTestUtils.setField(event, "remainingQuantity", remaining);
        return eventRepository.saveAndFlush(event);
    }

    @Nested
    @DisplayName("동시성 — Pessimistic Lock 직렬화")
    class Concurrency {

        @Test
        @DisplayName("단일 이벤트 50 스레드 동시 차감 시 정확히 재고 N건만 성공한다")
        void singleEvent_concurrentDeduct_serializedToInitialStock() throws Exception {
            int initialStock = 30;
            int threadCount = 50;
            UUID eventId = saveEvent(initialStock, initialStock, 1).getEventId();

            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger success = new AtomicInteger();
            AtomicInteger failure = new AtomicInteger();

            try {
                for (int i = 0; i < threadCount; i++) {
                    pool.submit(() -> {
                        try {
                            start.await();
                            eventInternalService.adjustStockBulk(new InternalBulkStockAdjustmentRequest(
                                List.of(new StockAdjustmentItem(eventId, 1))
                            ));
                            success.incrementAndGet();
                        } catch (Exception ex) {
                            failure.incrementAndGet();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                start.countDown();
                assertThat(done.await(30, TimeUnit.SECONDS))
                    .as("락 직렬화 실패 시 30초 안에 완주 불가")
                    .isTrue();
            } finally {
                pool.shutdownNow();
            }

            Event after = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(success.get()).isEqualTo(initialStock);
            assertThat(failure.get()).isEqualTo(threadCount - initialStock);
            assertThat(after.getRemainingQuantity()).isZero();
            assertThat(after.getStatus()).isEqualTo(EventStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("다중 이벤트를 다른 순서로 동시 차감해도 락 정렬로 deadlock 없이 완주한다")
        void multiEvent_reverseOrder_completesWithoutDeadlock() throws Exception {
            int perEvent = 20;
            UUID eventA = saveEvent(perEvent, perEvent, 5).getEventId();
            UUID eventB = saveEvent(perEvent, perEvent, 5).getEventId();

            int threadCount = perEvent;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger success = new AtomicInteger();

            try {
                for (int i = 0; i < threadCount; i++) {
                    final boolean reversed = (i % 2 == 0);
                    pool.submit(() -> {
                        try {
                            start.await();
                            List<StockAdjustmentItem> items = reversed
                                ? List.of(new StockAdjustmentItem(eventB, 1), new StockAdjustmentItem(eventA, 1))
                                : List.of(new StockAdjustmentItem(eventA, 1), new StockAdjustmentItem(eventB, 1));
                            eventInternalService.adjustStockBulk(new InternalBulkStockAdjustmentRequest(items));
                            success.incrementAndGet();
                        } catch (Exception ignored) {
                            // deadlock 검증이 본질 — 실패 카운트는 사용 안 함
                        } finally {
                            done.countDown();
                        }
                    });
                }
                start.countDown();
                assertThat(done.await(30, TimeUnit.SECONDS))
                    .as("락 정렬 정책상 deadlock 없이 완주해야 함")
                    .isTrue();
            } finally {
                pool.shutdownNow();
            }

            assertThat(success.get())
                .as("모든 스레드가 락 정렬 후 직렬 처리되어 성공")
                .isEqualTo(threadCount);
            assertThat(eventRepository.findByEventId(eventA).orElseThrow().getRemainingQuantity()).isZero();
            assertThat(eventRepository.findByEventId(eventB).orElseThrow().getRemainingQuantity()).isZero();
        }
    }

    @Nested
    @DisplayName("부분 실패 — 전체 롤백 (All or Nothing)")
    class AtomicRollback {

        @Test
        @DisplayName("벌크 중 1건 OUT_OF_STOCK 발생 시 앞 항목의 차감도 모두 롤백된다")
        void partialFailure_outOfStock_rollsBackPriorChanges() {
            // 같은 id 의 두 항목으로 처리 순서를 결정적으로 만든다.
            // 1번째: -3 성공 (remaining 10 → 7), 2번째: -50 OUT_OF_STOCK 실패
            // bulk @Transactional 롤백 → 최종 remaining = 10 (초기값)
            int initialStock = 10;
            UUID eventId = saveEvent(initialStock, initialStock, 100).getEventId();

            assertThatThrownBy(() -> eventInternalService.adjustStockBulk(
                new InternalBulkStockAdjustmentRequest(List.of(
                    new StockAdjustmentItem(eventId, 3),
                    new StockAdjustmentItem(eventId, 50)
                ))
            ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", EventErrorCode.OUT_OF_STOCK);

            Event after = eventRepository.findByEventId(eventId).orElseThrow();
            assertThat(after.getRemainingQuantity())
                .as("앞 항목(-3) 차감이 롤백되어 초기값 유지")
                .isEqualTo(initialStock);
            assertThat(after.getStatus()).isEqualTo(EventStatus.ON_SALE);
        }

        @Test
        @DisplayName("벌크 중 존재하지 않는 eventId 가 섞이면 전체 롤백된다")
        void partialFailure_eventNotFound_rollsBackAll() {
            int initialStock = 10;
            UUID realId = saveEvent(initialStock, initialStock, 5).getEventId();
            UUID missingId = UUID.randomUUID();

            assertThatThrownBy(() -> eventInternalService.adjustStockBulk(
                new InternalBulkStockAdjustmentRequest(List.of(
                    new StockAdjustmentItem(realId, 3),
                    new StockAdjustmentItem(missingId, 1)
                ))
            ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", EventErrorCode.EVENT_NOT_FOUND);

            assertThat(eventRepository.findByEventId(realId).orElseThrow().getRemainingQuantity())
                .as("미존재 id 동반 시 전체 롤백 → 실재 이벤트도 원복")
                .isEqualTo(initialStock);
        }
    }
}
