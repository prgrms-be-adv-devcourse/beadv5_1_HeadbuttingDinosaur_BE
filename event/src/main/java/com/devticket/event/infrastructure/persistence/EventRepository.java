package com.devticket.event.infrastructure.persistence;

import com.devticket.event.domain.model.Event;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long>, EventRepositoryCustom {

    // 외부 API
    Optional<Event> findByEventId(UUID eventId);

    @Query("SELECT DISTINCT e FROM Event e " +
        "LEFT JOIN FETCH e.eventTechStacks " +
        "WHERE e.eventId = :eventId")
    Optional<Event> findWithDetailsByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT DISTINCT e FROM Event e " +
        "LEFT JOIN FETCH e.eventTechStacks " +
        "WHERE e.eventId IN :eventIds")
    List<Event> findAllWithDetailsByEventIdIn(@Param("eventIds") List<UUID> eventIds);

    // 내부 API
    List<Event> findAllByIdIn(List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id IN :ids ORDER BY e.id ASC")
    List<Event> findAllByIdInWithLock(@Param("ids") List<Long> ids);

}
