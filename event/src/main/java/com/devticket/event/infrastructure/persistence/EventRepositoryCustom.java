package com.devticket.event.infrastructure.persistence;

import com.devticket.event.domain.model.Event;
import com.devticket.event.presentation.dto.EventListRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventRepositoryCustom {
    Page<Event> searchEvents(EventListRequest request, boolean isOwnEventRequest, Pageable pageable);
}
