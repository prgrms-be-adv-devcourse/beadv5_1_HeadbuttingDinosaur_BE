package com.devticket.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.model.Event;
import com.devticket.event.fixture.EventTestFixture;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.EventListRequest;
import com.devticket.event.presentation.dto.EventListResponse;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import com.devticket.event.presentation.dto.SellerEventCreateResponse;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

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
        UUID sellerId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        SellerEventCreateRequest request = EventTestFixture.createEventRequest(
            now.plusDays(4), now.plusDays(10), now.plusDays(15), 100, 4
        );

        UUID expectedUuid = UUID.randomUUID();

        Event savedEvent = EventTestFixture.createEvent(sellerId);
        ReflectionTestUtils.setField(savedEvent, "eventId", expectedUuid);
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

        // when
        SellerEventCreateResponse response = eventService.createEvent(sellerId, request);

        // then
        assertThat(response.eventId()).isEqualTo(expectedUuid);
        assertThat(response.status()).isEqualTo(EventStatus.DRAFT);

        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void 판매시작일이_등록시점_기준_3일_이내면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
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
        UUID sellerId = UUID.randomUUID();
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
        UUID sellerId = UUID.randomUUID();
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
        UUID sellerId = UUID.randomUUID();

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

    // 이벤트 목록 조회 및 검색 테스트

    @Test
    void 검색_조건이_주어지면_파라미터를_분해하여_Repository를_호출한다() {
        // given
        EventListRequest request = new EventListRequest("스프링", EventCategory.MEETUP, List.of(1L, 2L), null, null);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Event> mockPage = EventTestFixture.createEventPage();

        // 서비스가 내부적으로 계산할 '일반 조회 허용 상태값 리스트'
        List<EventStatus> publicStatuses = List.of(EventStatus.ON_SALE, EventStatus.SOLD_OUT, EventStatus.SALE_ENDED);

        // DTO가 해체되어 각각의 파라미터로 전달됨을 검증
        given(eventRepository.searchEvents(
            eq("스프링"), eq(EventCategory.MEETUP), eq(List.of(1L, 2L)), isNull(), eq(publicStatuses), eq(pageable)
        )).willReturn(mockPage);

        // when (currentUserId는 일반 조회이므로 null로 전달)
        EventListResponse response = eventService.getEventList(request, null, pageable);

        // then
        assertThat(response.totalElements()).isEqualTo(mockPage.getTotalElements());
        assertThat(response.content()).isNotEmpty();

        // verify 역시 해체된 파라미터로 검증
        verify(eventRepository).searchEvents(
            "스프링", EventCategory.MEETUP, List.of(1L, 2L), null, publicStatuses, pageable
        );
    }

    @Test
    void 검색_조건이_모두_null일_경우_전체_이벤트를_조회한다() {
        // given
        EventListRequest request = new EventListRequest(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);
        List<EventStatus> publicStatuses = List.of(EventStatus.ON_SALE, EventStatus.SOLD_OUT, EventStatus.SALE_ENDED);

        given(eventRepository.searchEvents(isNull(), isNull(), isNull(), isNull(), eq(publicStatuses), eq(pageable)))
            .willReturn(EventTestFixture.createEventPage());

        // when
        EventListResponse response = eventService.getEventList(request, null, pageable);

        // then
        assertThat(response).isNotNull();
        verify(eventRepository).searchEvents(null, null, null, null, publicStatuses, pageable);
    }

    @Test
    void 검색_결과가_없을_경우_빈_리스트를_정상적으로_반환한다() {
        // given
        EventListRequest request = new EventListRequest("절대검색안될키워드", null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Event> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        List<EventStatus> publicStatuses = List.of(EventStatus.ON_SALE, EventStatus.SOLD_OUT, EventStatus.SALE_ENDED);

        given(eventRepository.searchEvents(eq("절대검색안될키워드"), isNull(), isNull(), isNull(), eq(publicStatuses), eq(pageable)))
            .willReturn(emptyPage);

        // when
        EventListResponse response = eventService.getEventList(request, null, pageable);

        // then
        assertThat(response.totalElements()).isZero();
        assertThat(response.content()).isEmpty();
    }
}