package com.devticket.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devticket.event.domain.exception.BusinessException;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventCategory;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("이벤트 생성 성공 - 조건을 만족하면 정상적으로 UUID를 반환한다")
    void createEvent_Success() {
        // given
        Long sellerId = 1L;
        LocalDateTime now = LocalDateTime.now();
        SellerEventCreateRequest request = createMockRequest(
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
    @DisplayName("이벤트 생성 실패 - 판매 시작일이 등록 시점 기준 3일 이내면 예외가 발생한다")
    void createEvent_Fail_RegistrationTimeExceeded() {
        // given
        Long sellerId = 1L;
        LocalDateTime now = LocalDateTime.now();
        // 당장 내일(1일 뒤)부터 판매 시작하도록 설정 (오류 유발)
        SellerEventCreateRequest request = createMockRequest(
            now.plusDays(1), now.plusDays(10), now.plusDays(15), 100, 4
        );

        // when & then
        // 프로젝트 구조에 맞게 BusinessException으로 검증 (또는 구조에 따라 EventException)
        assertThatThrownBy(() -> eventService.createEvent(sellerId, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.REGISTRATION_TIME_EXCEEDED.getMessage());
    }

    // 헬퍼 메서드 (테스트용 객체 생성)
    private SellerEventCreateRequest createMockRequest(
        LocalDateTime saleStart, LocalDateTime saleEnd, LocalDateTime eventDate,
        int totalQty, int maxQty) {
        return new SellerEventCreateRequest(
            "Spring Boot 3.x 심화 밋업", "설명", "강남역",
            eventDate, saleStart, saleEnd,
            50000, totalQty, maxQty, EventCategory.MEETUP,
            List.of(UUID.randomUUID(), UUID.randomUUID()), // ERD v3: UUID 리스트
            List.of("url1")
        );
    }
}