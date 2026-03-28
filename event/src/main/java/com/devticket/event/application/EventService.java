package com.devticket.event.application;

import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.model.Event;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.EventListRequest;
import com.devticket.event.presentation.dto.EventListResponse;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import com.devticket.event.presentation.dto.SellerEventCreateResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    @Transactional
    public SellerEventCreateResponse createEvent(UUID sellerId, SellerEventCreateRequest request) {

        // 1. 비즈니스 정책 검증
        if (request.saleStartAt().isAfter(request.saleEndAt()) || request.saleStartAt().isEqual(request.saleEndAt())) {
            throw new BusinessException(EventErrorCode.INVALID_SALE_PERIOD);
        }
        if (request.saleEndAt().isAfter(request.eventDateTime())) {
            throw new BusinessException(EventErrorCode.INVALID_EVENT_DATE);
        }

        LocalDateTime deadline = request.saleStartAt().minusDays(3);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException(EventErrorCode.REGISTRATION_TIME_EXCEEDED);
        }

        if (request.maxQuantity() > request.totalQuantity()) {
            throw new BusinessException(EventErrorCode.MAX_QUANTITY_EXCEEDED);
        }

        // 2. 정적 팩토리 메서드 사용
        Event event = Event.create(
            sellerId,
            request.title(),
            request.description(),
            request.location(),
            request.eventDateTime(),
            request.saleStartAt(),
            request.saleEndAt(),
            request.price(),
            request.totalQuantity(),
            request.maxQuantity(),
            request.category()
        );

        // 3. 이벤트 저장
        Event savedEvent = eventRepository.save(event);

        return SellerEventCreateResponse.from(savedEvent);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEvent(UUID eventId) {

        Event event = eventRepository.findByEventId(eventId)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));

        return EventDetailResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventListResponse getEventList(EventListRequest request, UUID currentUserId, Pageable pageable) {

        // 1. 판매자 본인이 자신의 이벤트를 조회하는 요청인지 확인
        boolean isOwnEventRequest = request.sellerId() != null && request.sellerId().equals(currentUserId);

        // 2. 권한 검증: 비공개 상태를 조회하려는데 본인이 아니면 예외 발생
        if (request.status() != null && !isPublicStatus(request.status()) && !isOwnEventRequest) {
            throw new BusinessException(EventErrorCode.UNAUTHORIZED_SELLER);
        }

        // 3. 데이터베이스 조회 (본인 요청 여부를 QueryDSL로 넘김)
        Page<Event> eventPage = eventRepository.searchEvents(request, isOwnEventRequest, pageable);

        return EventListResponse.of(eventPage);
    }

    private boolean isPublicStatus(EventStatus status) {
        return status == EventStatus.ON_SALE ||
            status == EventStatus.SOLD_OUT ||
            status == EventStatus.SALE_ENDED;
    }
}