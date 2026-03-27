package com.devticket.event.infrastructure.persistence;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.model.Event;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventRepositoryCustom {
    Page<Event> searchEvents(String keyword, EventCategory category, List<Long> techStackIds, Pageable pageable);
}
