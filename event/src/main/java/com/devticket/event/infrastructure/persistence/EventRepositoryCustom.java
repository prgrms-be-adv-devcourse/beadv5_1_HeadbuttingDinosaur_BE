package com.devticket.event.infrastructure.persistence;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventRepositoryCustom {
    Page<Event> searchEvents(
        String keyword,
        EventCategory category,
        List<Long> techStacks,
        UUID sellerId,
        List<EventStatus> statuses, // 허용된 상태값 리스트
        Pageable pageable
    );

    List<Event> findEventsBySeller(UUID sellerId, EventStatus status);
}
