package com.devticket.event.infrastructure.persistence;

import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventViewRepository extends JpaRepository<EventView, Long> {
    Optional<EventView> findByEvent(Event event);

    @Query("SELECT ev.event FROM EventView ev WHERE ev.event.status = com.devticket.event.domain.enums.EventStatus.ON_SALE ORDER BY ev.viewCount DESC LIMIT :limit")
    List<Event> findTopByViewCount(@Param("limit") int limit);


    @Query("SELECT ev FROM EventView ev WHERE ev.event.eventId IN :eventIds")
    List<EventView> findAllByEventIdIn(@Param("eventIds") List<UUID> eventIds);
}
