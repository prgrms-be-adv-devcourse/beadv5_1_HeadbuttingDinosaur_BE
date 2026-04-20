package com.devticket.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.common.messaging.KafkaTopics;
import com.devticket.event.common.messaging.event.OrderCreatedEvent;
import com.devticket.event.common.messaging.event.StockDeductedEvent;
import com.devticket.event.common.messaging.event.StockFailedEvent;
import com.devticket.event.common.outbox.OutboxService;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.exception.StockDeductionException;
import com.devticket.event.domain.model.Event;
import com.devticket.event.infrastructure.client.MemberClient;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.infrastructure.search.EventSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventServiceKafkaTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private MessageDeduplicationService deduplicationService;

    @Spy
    private ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private MemberClient memberClient;

    @Mock
    private EventSearchRepository eventSearchRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @InjectMocks
    private EventService eventService;

    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final String TOPIC = KafkaTopics.ORDER_CREATED;

    // ── processOrderCreated ────────────────────────────────────────────────

    @Test
    void 중복_메시지이면_재고_차감_없이_즉시_반환한다() {
        // given
        given(deduplicationService.isDuplicate(MESSAGE_ID)).willReturn(true);
        String payload = toJson(singleItemEvent(UUID.randomUUID(), UUID.randomUUID(), 2));

        // when
        eventService.processOrderCreated(MESSAGE_ID, TOPIC, payload);

        // then
        then(eventRepository).shouldHaveNoInteractions();
        then(outboxService).shouldHaveNoInteractions();
        then(deduplicationService).should(never()).markProcessed(any(), any());
    }

    @Test
    void 단건_재고_차감_성공_시_stock_deducted_Outbox를_저장하고_markProcessed를_호출한다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = onSaleEvent(eventId, 10, 5);

        given(deduplicationService.isDuplicate(MESSAGE_ID)).willReturn(false);
        given(eventRepository.findByEventIdWithLock(eventId)).willReturn(Optional.of(event));

        String payload = toJson(singleItemEvent(orderId, eventId, 2));

        // when
        eventService.processOrderCreated(MESSAGE_ID, TOPIC, payload);

        // then — Outbox에 올바른 값으로 저장됐는지 검증
        then(outboxService).should().save(
                eq(orderId.toString()),
                eq(orderId.toString()),
                eq("STOCK_DEDUCTED"),
                eq(KafkaTopics.STOCK_DEDUCTED),
                argThat(e -> {
                    StockDeductedEvent sde = (StockDeductedEvent) e;
                    return sde.orderId().equals(orderId)
                            && sde.eventId().equals(eventId)
                            && sde.quantity() == 2;
                })
        );
        then(deduplicationService).should().markProcessed(MESSAGE_ID, TOPIC);
        assertThat(event.getRemainingQuantity()).isEqualTo(8); // 10 - 2
    }

    @Test
    void 다건_주문_모두_성공_시_항목별로_stock_deducted_Outbox를_저장한다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        given(deduplicationService.isDuplicate(MESSAGE_ID)).willReturn(false);
        given(eventRepository.findByEventIdWithLock(eventId1)).willReturn(Optional.of(onSaleEvent(eventId1, 10, 5)));
        given(eventRepository.findByEventIdWithLock(eventId2)).willReturn(Optional.of(onSaleEvent(eventId2, 10, 5)));

        String payload = toJson(multiItemEvent(orderId, eventId1, eventId2, 3, 3));

        // when
        eventService.processOrderCreated(MESSAGE_ID, TOPIC, payload);

        // then — 2건 각각 Outbox 저장
        then(outboxService).should(times(2)).save(
                eq(orderId.toString()),
                eq(orderId.toString()),
                eq("STOCK_DEDUCTED"),
                eq(KafkaTopics.STOCK_DEDUCTED),
                any(StockDeductedEvent.class)
        );
        then(deduplicationService).should().markProcessed(MESSAGE_ID, TOPIC);
    }

    @Test
    void 재고_부족_시_StockDeductionException을_던지고_Outbox를_저장하지_않는다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = onSaleEvent(eventId, 1, 10); // 잔여 1석인데 5석 요청

        given(deduplicationService.isDuplicate(MESSAGE_ID)).willReturn(false);
        given(eventRepository.findByEventIdWithLock(eventId)).willReturn(Optional.of(event));

        String payload = toJson(singleItemEvent(orderId, eventId, 5));

        // when & then
        assertThatThrownBy(() -> eventService.processOrderCreated(MESSAGE_ID, TOPIC, payload))
                .isInstanceOf(StockDeductionException.class)
                .satisfies(ex -> {
                    StockDeductionException sde = (StockDeductionException) ex;
                    assertThat(sde.getOrderId()).isEqualTo(orderId);
                    assertThat(sde.getEventId()).isEqualTo(eventId);
                    assertThat(sde.getMessage())
                            .isEqualTo(EventErrorCode.OUT_OF_STOCK.getMessage());
                });

        then(outboxService).shouldHaveNoInteractions();
        then(deduplicationService).should(never()).markProcessed(any(), any());
    }

    @Test
    void 이벤트가_존재하지_않으면_StockDeductionException을_던진다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        given(deduplicationService.isDuplicate(MESSAGE_ID)).willReturn(false);
        given(eventRepository.findByEventIdWithLock(eventId)).willReturn(Optional.empty());

        String payload = toJson(singleItemEvent(orderId, eventId, 2));

        // when & then
        assertThatThrownBy(() -> eventService.processOrderCreated(MESSAGE_ID, TOPIC, payload))
                .isInstanceOf(StockDeductionException.class)
                .satisfies(ex -> {
                    StockDeductionException sde = (StockDeductionException) ex;
                    assertThat(sde.getOrderId()).isEqualTo(orderId);
                    assertThat(sde.getEventId()).isEqualTo(eventId);
                });

        then(outboxService).shouldHaveNoInteractions();
    }

    @Test
    void 다건_주문_중_하나_재고_부족_시_실패한_항목의_eventId로_StockDeductionException을_던진다() {
        // given
        UUID orderId = UUID.randomUUID();
        // MSB가 동일한 양수 UUID 사용 — Java UUID 비교는 signed long 기준이므로 혼동 없는 순서 고정
        UUID firstId  = UUID.fromString("00000000-0000-0000-0000-000000000001"); // 먼저 처리됨 (성공)
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002"); // 나중에 처리됨 (재고 부족)

        given(deduplicationService.isDuplicate(MESSAGE_ID)).willReturn(false);
        given(eventRepository.findByEventIdWithLock(firstId)).willReturn(Optional.of(onSaleEvent(firstId, 10, 5)));
        given(eventRepository.findByEventIdWithLock(secondId)).willReturn(Optional.of(onSaleEvent(secondId, 1, 10)));

        String payload = toJson(multiItemEvent(orderId, firstId, secondId, 3, 5)); // secondId: 잔여 1 < 요청 5

        // when & then
        assertThatThrownBy(() -> eventService.processOrderCreated(MESSAGE_ID, TOPIC, payload))
                .isInstanceOf(StockDeductionException.class)
                .satisfies(ex -> {
                    StockDeductionException sde = (StockDeductionException) ex;
                    assertThat(sde.getEventId()).isEqualTo(secondId);
                });

        then(outboxService).shouldHaveNoInteractions(); // 부분 커밋 없음
    }

    @Test
    void 매진_상태_이벤트이면_StockDeductionException을_던진다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = onSaleEvent(eventId, 10, 5);
        ReflectionTestUtils.setField(event, "status", EventStatus.SOLD_OUT);

        given(deduplicationService.isDuplicate(MESSAGE_ID)).willReturn(false);
        given(eventRepository.findByEventIdWithLock(eventId)).willReturn(Optional.of(event));

        String payload = toJson(singleItemEvent(orderId, eventId, 2));

        // when & then
        assertThatThrownBy(() -> eventService.processOrderCreated(MESSAGE_ID, TOPIC, payload))
                .isInstanceOf(StockDeductionException.class)
                .satisfies(ex -> {
                    StockDeductionException sde = (StockDeductionException) ex;
                    assertThat(sde.getOrderId()).isEqualTo(orderId);
                    assertThat(sde.getEventId()).isEqualTo(eventId);
                });
    }

    @Test
    void 판매_기간_외_이벤트이면_StockDeductionException을_던진다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = onSaleEvent(eventId, 10, 5);
        // saleStartAt을 미래로 설정 → 아직 판매 시작 전
        ReflectionTestUtils.setField(event, "saleStartAt", LocalDateTime.now().plusDays(3));

        given(deduplicationService.isDuplicate(MESSAGE_ID)).willReturn(false);
        given(eventRepository.findByEventIdWithLock(eventId)).willReturn(Optional.of(event));

        String payload = toJson(singleItemEvent(orderId, eventId, 2));

        // when & then
        assertThatThrownBy(() -> eventService.processOrderCreated(MESSAGE_ID, TOPIC, payload))
                .isInstanceOf(StockDeductionException.class)
                .satisfies(ex -> {
                    StockDeductionException sde = (StockDeductionException) ex;
                    assertThat(sde.getEventId()).isEqualTo(eventId);
                    assertThat(sde.getMessage())
                            .isEqualTo(EventErrorCode.PURCHASE_NOT_ALLOWED.getMessage());
                });
    }

    // ── saveStockFailed ────────────────────────────────────────────────────

    @Test
    void 중복_messageId이면_stock_failed_Outbox를_저장하지_않는다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        given(deduplicationService.isDuplicate(MESSAGE_ID)).willReturn(true);

        // when
        eventService.saveStockFailed(MESSAGE_ID, TOPIC, orderId, eventId, "재고 부족");

        // then
        then(outboxService).shouldHaveNoInteractions();
        then(deduplicationService).should(never()).markProcessed(any(), any());
    }

    @Test
    void stock_failed_Outbox를_저장하고_markProcessed를_호출한다() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String reason = EventErrorCode.OUT_OF_STOCK.getMessage();
        given(deduplicationService.isDuplicate(MESSAGE_ID)).willReturn(false);

        // when
        eventService.saveStockFailed(MESSAGE_ID, TOPIC, orderId, eventId, reason);

        // then
        then(outboxService).should().save(
                eq(orderId.toString()),
                eq(orderId.toString()),
                eq("STOCK_FAILED"),
                eq(KafkaTopics.STOCK_FAILED),
                argThat(e -> {
                    StockFailedEvent sfe = (StockFailedEvent) e;
                    return sfe.orderId().equals(orderId)
                            && sfe.eventId().equals(eventId)
                            && sfe.reason().equals(reason);
                })
        );
        then(deduplicationService).should().markProcessed(MESSAGE_ID, TOPIC);
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    /** 현재 판매 중인 이벤트 생성 (saleStartAt = 어제, saleEndAt = 5일 후) */
    private Event onSaleEvent(UUID eventId, int remainingQuantity, int maxQuantity) {
        Event event = Event.create(
                UUID.randomUUID(), "테스트 이벤트", "설명", "서울",
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(5),
                50000, 100, maxQuantity, EventCategory.MEETUP
        );
        ReflectionTestUtils.setField(event, "eventId", eventId);
        ReflectionTestUtils.setField(event, "remainingQuantity", remainingQuantity);
        return event;
    }

    /** 단건 OrderItem을 가진 OrderCreatedEvent */
    private OrderCreatedEvent singleItemEvent(UUID orderId, UUID eventId, int quantity) {
        return new OrderCreatedEvent(
                orderId,
                UUID.randomUUID(),
                List.of(new OrderCreatedEvent.OrderItem(eventId, quantity)),
                50000 * quantity,
                Instant.now()
        );
    }

    /** 2건 OrderItem을 가진 OrderCreatedEvent */
    private OrderCreatedEvent multiItemEvent(
            UUID orderId, UUID eventId1, UUID eventId2, int qty1, int qty2) {
        return new OrderCreatedEvent(
                orderId,
                UUID.randomUUID(),
                List.of(
                        new OrderCreatedEvent.OrderItem(eventId1, qty1),
                        new OrderCreatedEvent.OrderItem(eventId2, qty2)
                ),
                50000 * (qty1 + qty2),
                Instant.now()
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("테스트 픽스처 직렬화 실패", e);
        }
    }
}
