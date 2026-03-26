package com.devticket.event.infrastructure.persistence;

import com.devticket.event.domain.model.Event;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByEventId(UUID eventId);

}
