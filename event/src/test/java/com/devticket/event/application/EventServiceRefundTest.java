package com.devticket.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.common.messaging.KafkaTopics;
import com.devticket.event.common.messaging.event.EventForceCancelledEvent;
import com.devticket.event.common.messaging.event.EventSaleStoppedEvent;
import com.devticket.event.common.outbox.OutboxService;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.model.Event;
import com.devticket.event.infrastructure.client.MemberClient;
import com.devticket.event.infrastructure.client.OpenAiEmbeddingClient;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.presentation.dto.SellerEventUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * EventService 의 환불 Saga 관련 메서드(forceCancel, updateEvent CANCELLED Outbox) 검증.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceRefundTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private MessageDeduplicationService deduplicationService;

    @Mock
    private MemberClient memberClient;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private OpenAiEmbeddingClient openAiEmbeddingClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EventService eventService;

    private UUID eventId;
    private UUID sellerId;
    private Event event;
    private UUID adminId;



    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        event = Event.create(
            sellerId, "테스트 이벤트", "설명", "강남",
            LocalDateTime.now().plusDays(15),
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(10),
            10000, 100, 5, EventCategory.MEETUP
        );
        ReflectionTestUtils.setField(event, "eventId", eventId);
    }

    @Nested
    @DisplayName("forceCancel")
    class ForceCancel {

        @Test
        @DisplayName("어드민이 요청하면 FORCE_CANCELLED 로 전이하고 event.force-cancelled Outbox 를 발행한다")
        void forceCancel_admin_publishesForceCancel() {
            given(eventRepository.findWithDetailsByEventId(eventId)).willReturn(Optional.of(event));

            eventService.forceCancel(adminId, "ADMIN", eventId, "policy violation");

            assertThat(event.getStatus()).isEqualTo(EventStatus.FORCE_CANCELLED);
            verify(outboxService, times(1)).save(
                eq(eventId.toString()),
                eq(eventId.toString()),
                eq("EVENT_FORCE_CANCELLED"),
                eq(KafkaTopics.EVENT_FORCE_CANCELLED),
                argThat(payload -> payload instanceof EventForceCancelledEvent e
                    && e.eventId().equals(eventId)
                    && e.sellerId().equals(sellerId)
                    && "policy violation".equals(e.reason())
                    && e.occurredAt() != null)
            );
        }

        @Test
        @DisplayName("존재하지 않는 eventId 면 EVENT_NOT_FOUND 예외를 던지고 Outbox 발행하지 않는다")
        void forceCancel_notFound() {
            given(eventRepository.findWithDetailsByEventId(eventId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.forceCancel(adminId, "ADMIN", eventId, "reason"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND);

            verify(outboxService, never()).save(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("이미 CANCELLED 인 이벤트는 CANNOT_CHANGE_STATUS 로 거절된다")
        void forceCancel_alreadyCancelled() {
            ReflectionTestUtils.setField(event, "status", EventStatus.CANCELLED);
            given(eventRepository.findWithDetailsByEventId(eventId)).willReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.forceCancel(adminId, "ADMIN", eventId, "reason"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.CANNOT_CHANGE_STATUS);

            verify(outboxService, never()).save(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("이미 FORCE_CANCELLED 인 이벤트도 CANNOT_CHANGE_STATUS 로 거절된다")
        void forceCancel_alreadyForceCancelled() {
            ReflectionTestUtils.setField(event, "status", EventStatus.FORCE_CANCELLED);
            given(eventRepository.findWithDetailsByEventId(eventId)).willReturn(Optional.of(event));

            assertThatThrownBy(() -> eventService.forceCancel(adminId, "ADMIN", eventId, "reason"))
                .isInstanceOf(BusinessException.class);

            verify(outboxService, never()).save(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("판매자가 본인 이벤트를 취소하면 CANCELLED 로 전이하고 event.sale-stopped Outbox 를 발행한다")
        void forceCancel_seller_publishesSaleStopped() {
            given(eventRepository.findWithDetailsByEventId(eventId)).willReturn(Optional.of(event));

            eventService.forceCancel(sellerId, "SELLER", eventId, null);

            assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
            verify(outboxService, times(1)).save(
                eq(eventId.toString()),
                eq(eventId.toString()),
                eq("EVENT_SALE_STOPPED"),
                eq(KafkaTopics.EVENT_SALE_STOPPED),
                argThat(payload -> payload instanceof EventSaleStoppedEvent e
                    && e.eventId().equals(eventId)
                    && e.sellerId().equals(sellerId)
                    && e.occurredAt() != null)
            );
        }

        @Test
        @DisplayName("판매자가 타인 이벤트를 취소하면 UNAUTHORIZED_SELLER 예외가 발생한다")
        void forceCancel_seller_unauthorized() {
            given(eventRepository.findWithDetailsByEventId(eventId)).willReturn(Optional.of(event));

            assertThatThrownBy(() ->
                eventService.forceCancel(UUID.randomUUID(), "SELLER", eventId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.UNAUTHORIZED_SELLER);

            verify(outboxService, never()).save(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("판매자가 취소 불가 상태 이벤트를 취소하면 CANNOT_CHANGE_STATUS 예외가 발생한다")
        void forceCancel_seller_cannotCancel() {
            ReflectionTestUtils.setField(event, "status", EventStatus.FORCE_CANCELLED);
            given(eventRepository.findWithDetailsByEventId(eventId)).willReturn(Optional.of(event));

            assertThatThrownBy(() ->
                eventService.forceCancel(sellerId, "SELLER", eventId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.CANNOT_CHANGE_STATUS);

            verify(outboxService, never()).save(any(), any(), any(), any(), any());
        }
    }

}