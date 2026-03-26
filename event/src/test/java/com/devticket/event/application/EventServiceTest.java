package com.devticket.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.model.Event;
import com.devticket.event.fixture.EventTestFixture;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    // 이벤트 생성 테스트

    @Test
    void 정상적인_조건일_경우_이벤트가_성공적으로_생성되고_UUID를_반환한다() {
        // given
        Long sellerId = 1L;
        LocalDateTime now = LocalDateTime.now();
        SellerEventCreateRequest request = EventTestFixture.createEventRequest(
            now.plusDays(4), now.plusDays(10), now.plusDays(15), 100, 4
        );

        UUID expectedUuid = UUID.randomUUID();
        Event savedEvent = Event.builder()
            .eventId(expectedUuid)
            .sellerId(sellerId)
            .build();

        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

        // when
        UUID resultId = eventService.createEvent(sellerId, request);

        // then
        assertThat(resultId).isEqualTo(expectedUuid);
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void 판매시작일이_등록시점_기준_3일_이내면_예외가_발생한다() {
        // given
        Long sellerId = 1L;
        LocalDateTime now = LocalDateTime.now();
        SellerEventCreateRequest request = EventTestFixture.createEventRequest(
            now.plusDays(1), now.plusDays(10), now.plusDays(15), 100, 4
        );

        // when & then
        assertThatThrownBy(() -> eventService.createEvent(sellerId, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.REGISTRATION_TIME_EXCEEDED.getMessage());
    }

    @Test
    void 인당_최대구매수량이_총수량보다_크면_예외가_발생한다() {
        // given
        Long sellerId = 1L;
        LocalDateTime now = LocalDateTime.now();
        SellerEventCreateRequest request = EventTestFixture.createEventRequest(
            now.plusDays(4), now.plusDays(10), now.plusDays(15), 10, 20
        );

        // when & then
        assertThatThrownBy(() -> eventService.createEvent(sellerId, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.MAX_QUANTITY_EXCEEDED.getMessage());
    }

    @Test
    void 행사일이_판매종료일보다_빠르면_예외가_발생한다() {
        // given
        Long sellerId = 1L;
        LocalDateTime now = LocalDateTime.now();
        // 행사일(plusDays(5))이 판매종료일(plusDays(10))보다 빠른 모순된 상황
        SellerEventCreateRequest request = EventTestFixture.createEventRequest(
            now.plusDays(4), now.plusDays(10), now.plusDays(5), 100, 4
        );

        // when & then
        assertThatThrownBy(() -> eventService.createEvent(sellerId, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.INVALID_EVENT_DATE.getMessage());
    }

    // 이벤트 상세 조회 테스트

    @Test
    void 존재하는_이벤트_조회시_상세정보를_반환한다() {
        // given
        Long sellerId = 1L;

        Event event = EventTestFixture.createEvent(sellerId);
        UUID eventId = event.getEventId();

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.of(event));

        // when
        EventDetailResponse response = eventService.getEvent(eventId);

        // then
        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.title()).isEqualTo("상세 조회 테스트 밋업");
        verify(eventRepository).findByEventId(eventId);
    }

    @Test
    void 존재하지_않는_이벤트_조회시_예외가_발생한다() {
        // given
        UUID invalidEventId = UUID.randomUUID(); // 아무 UUID나 생성
        when(eventRepository.findByEventId(invalidEventId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventService.getEvent(invalidEventId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.EVENT_NOT_FOUND.getMessage());
    }
}