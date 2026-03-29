package com.devticket.event.infrastructure.persistence;

import com.devticket.event.domain.model.Event;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long>, EventRepositoryCustom {

    Optional<Event> findByEventId(UUID eventId);

    @Query("SELECT DISTINCT e FROM Event e " +
           "LEFT JOIN FETCH e.eventTechStacks " +
           "WHERE e.eventId = :eventId")
    Optional<Event> findWithDetailsByEventId(@Param("eventId") UUID eventId);

}
