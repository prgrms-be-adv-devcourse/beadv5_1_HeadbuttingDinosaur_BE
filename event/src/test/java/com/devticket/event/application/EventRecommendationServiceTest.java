package com.devticket.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.fixture.EventTestFixture;
import com.devticket.event.infrastructure.client.AiClient;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.presentation.dto.internal.InternalRecommendationResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventRecommendationServiceTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventRecommendationService eventRecommendationService;

    @Test
    void AI가_빈_목록을_반환하면_빈_추천_응답을_반환한다() {
        UUID userId = UUID.randomUUID();
        when(aiClient.getRecommendedEventIds(userId)).thenReturn(List.of());

        InternalRecommendationResponse response = eventRecommendationService.getRecommendations(userId);

        assertThat(response.events()).isEmpty();
    }

    @Test
    void ON_SALE_이벤트는_추천_결과에_포함된다() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Event event = EventTestFixture.createEventWithStatus(UUID.randomUUID(), EventStatus.ON_SALE);
        ReflectionTestUtils.setField(event, "eventId", eventId);

        when(aiClient.getRecommendedEventIds(userId)).thenReturn(List.of(eventId.toString()));
        when(eventRepository.findAllWithDetailsByEventIdIn(anyList())).thenReturn(List.of(event));
        when(eventRepository.findEventImagesByEventIdIn(anyList())).thenReturn(List.of(event));

        InternalRecommendationResponse response = eventRecommendationService.getRecommendations(userId);

        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).eventId()).isEqualTo(eventId);
    }

    @Test
    void SALE_ENDED_이벤트는_추천_결과에서_제외된다() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Event event = EventTestFixture.createEventWithStatus(UUID.randomUUID(), EventStatus.SALE_ENDED);
        ReflectionTestUtils.setField(event, "eventId", eventId);

        when(aiClient.getRecommendedEventIds(userId)).thenReturn(List.of(eventId.toString()));
        when(eventRepository.findAllWithDetailsByEventIdIn(anyList())).thenReturn(List.of(event));
        when(eventRepository.findEventImagesByEventIdIn(anyList())).thenReturn(List.of(event));

        InternalRecommendationResponse response = eventRecommendationService.getRecommendations(userId);

        assertThat(response.events()).isEmpty();
    }

    @Test
    void CANCELLED_이벤트는_추천_결과에서_제외된다() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Event event = EventTestFixture.createEventWithStatus(UUID.randomUUID(), EventStatus.CANCELLED);
        ReflectionTestUtils.setField(event, "eventId", eventId);

        when(aiClient.getRecommendedEventIds(userId)).thenReturn(List.of(eventId.toString()));
        when(eventRepository.findAllWithDetailsByEventIdIn(anyList())).thenReturn(List.of(event));
        when(eventRepository.findEventImagesByEventIdIn(anyList())).thenReturn(List.of(event));

        InternalRecommendationResponse response = eventRecommendationService.getRecommendations(userId);

        assertThat(response.events()).isEmpty();
    }

    @Test
    void DRAFT_이벤트는_추천_결과에서_제외된다() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Event event = EventTestFixture.createEventWithStatus(UUID.randomUUID(), EventStatus.DRAFT);
        ReflectionTestUtils.setField(event, "eventId", eventId);

        when(aiClient.getRecommendedEventIds(userId)).thenReturn(List.of(eventId.toString()));
        when(eventRepository.findAllWithDetailsByEventIdIn(anyList())).thenReturn(List.of(event));
        when(eventRepository.findEventImagesByEventIdIn(anyList())).thenReturn(List.of(event));

        InternalRecommendationResponse response = eventRecommendationService.getRecommendations(userId);

        assertThat(response.events()).isEmpty();
    }

    @Test
    void DB에_없는_이벤트_ID는_추천_결과에서_제외된다() {
        UUID userId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();

        when(aiClient.getRecommendedEventIds(userId)).thenReturn(List.of(missingId.toString()));
        when(eventRepository.findAllWithDetailsByEventIdIn(anyList())).thenReturn(List.of());
        when(eventRepository.findEventImagesByEventIdIn(anyList())).thenReturn(List.of());

        InternalRecommendationResponse response = eventRecommendationService.getRecommendations(userId);

        assertThat(response.events()).isEmpty();
    }

    @Test
    void AI_랭킹_순서를_유지하며_유효한_이벤트만_반환한다() {
        UUID userId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID cancelledId = UUID.randomUUID();

        Event first = EventTestFixture.createEventWithStatus(UUID.randomUUID(), EventStatus.ON_SALE);
        ReflectionTestUtils.setField(first, "eventId", firstId);

        Event second = EventTestFixture.createEventWithStatus(UUID.randomUUID(), EventStatus.SOLD_OUT);
        ReflectionTestUtils.setField(second, "eventId", secondId);

        Event cancelled = EventTestFixture.createEventWithStatus(UUID.randomUUID(), EventStatus.CANCELLED);
        ReflectionTestUtils.setField(cancelled, "eventId", cancelledId);

        // AI는 랭킹 순서로 반환 (cancelled는 두 번째)
        when(aiClient.getRecommendedEventIds(userId))
            .thenReturn(List.of(firstId.toString(), cancelledId.toString(), secondId.toString()));
        when(eventRepository.findAllWithDetailsByEventIdIn(anyList()))
            .thenReturn(List.of(first, second, cancelled));
        when(eventRepository.findEventImagesByEventIdIn(anyList()))
            .thenReturn(List.of(first, second, cancelled));

        InternalRecommendationResponse response = eventRecommendationService.getRecommendations(userId);

        assertThat(response.events()).hasSize(2);
        assertThat(response.events().get(0).eventId()).isEqualTo(firstId);   // 1순위
        assertThat(response.events().get(1).eventId()).isEqualTo(secondId);  // cancelled 건너뛰고 2순위
    }
}
