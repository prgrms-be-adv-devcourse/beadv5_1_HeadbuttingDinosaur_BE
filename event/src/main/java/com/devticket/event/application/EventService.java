package com.devticket.event.application;

import com.devticket.event.domain.exception.BusinessException;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventStatus;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    @Transactional
    public UUID createEvent(Long sellerId, SellerEventCreateRequest request) {

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

        // 2. Event 엔티티 생성
        Event event = Event.builder()
            .eventId(UUID.randomUUID()) // 👈 v3 반영: 외부 노출용 고유 UUID 직접 생성
            .sellerId(sellerId)
            .title(request.title())
            .description(request.description())
            .location(request.location())
            .eventDateTime(request.eventDateTime())
            .saleStartAt(request.saleStartAt())
            .saleEndAt(request.saleEndAt())
            .price(request.price())
            .totalQuantity(request.totalQuantity())
            .maxQuantity(request.maxQuantity())
            .remainingQuantity(request.totalQuantity())
            .status(EventStatus.DRAFT)
            .category(request.category())
            .build();

        // 3. 이벤트 저장
        Event savedEvent = eventRepository.save(event);

        return savedEvent.getEventId();
    }
}