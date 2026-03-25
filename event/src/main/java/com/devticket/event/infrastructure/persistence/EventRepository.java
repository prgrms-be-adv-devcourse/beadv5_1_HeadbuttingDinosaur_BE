package com.devticket.event.infrastructure.persistence;

import com.devticket.event.domain.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    // 기본적인 save(), findById() 등은 JpaRepository가 기본 제공합니다.
}
