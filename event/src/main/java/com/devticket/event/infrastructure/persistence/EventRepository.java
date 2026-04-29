package com.devticket.event.infrastructure.persistence;

import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("""
    SELECT e FROM Event e
    WHERE (:keyword IS NULL OR e.title LIKE CONCAT('%', :keyword, '%'))
      AND (:status IS NULL OR e.status = :status)
      AND (:sellerId IS NULL OR e.sellerId = :sellerId)
    ORDER BY e.createdAt DESC
    """)
    Page<Event> searchEvents(
        @Param("keyword") String keyword,
        @Param("status") EventStatus status,
        @Param("sellerId") UUID sellerId,
        Pageable pageable
    );

    @Query("SELECT e.eventId FROM Event e WHERE e.status IN :statuses")
    List<UUID> findAllEventIdsByStatusIn(@Param("statuses") List<EventStatus> statuses);

    // 외부 API
    List<Event> findAllByEventIdIn(List<UUID> eventIds);

    Optional<Event> findByEventId(UUID eventId);

    @Query("SELECT DISTINCT e FROM Event e " +
        "LEFT JOIN FETCH e.eventTechStacks " +
        "WHERE e.eventId = :eventId")
    Optional<Event> findWithDetailsByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT DISTINCT e FROM Event e " +
        "LEFT JOIN FETCH e.eventTechStacks " +
        "WHERE e.eventId IN :eventIds")
    List<Event> findAllWithDetailsByEventIdIn(@Param("eventIds") List<UUID> eventIds);

    // 공개 API - 목록 조회용 이미지 배치 로딩 (N+1 쿼리 제거)
    @Query("SELECT DISTINCT e FROM Event e " +
        "LEFT JOIN FETCH e.eventImages " +
        "WHERE e.eventId IN :eventIds")
    List<Event> findEventImagesByEventIdIn(@Param("eventIds") List<UUID> eventIds);

    // 내부 API
    List<Event> findAllByIdIn(List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.eventId = :eventId")
    Optional<Event> findByEventIdWithLock(@Param("eventId") UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id IN :ids ORDER BY e.id ASC")
    List<Event> findAllByIdInWithLock(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.eventId IN :eventIds ORDER BY e.eventId ASC")
    List<Event> findAllByEventIdInWithLock(@Param("eventIds") List<UUID> eventIds);

    List<Event> findAllByStatusAndSaleStartAtBefore(EventStatus status, LocalDateTime now);

    List<Event> findAllByStatusInAndSaleEndAtBefore(List<EventStatus> statuses, LocalDateTime now);

    List<Event> findAllByStatusInAndEventDateTimeBefore(List<EventStatus> statuses, LocalDateTime now);

    // 판매자별 이벤트 조회
    List<Event> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);

    List<Event> findBySellerIdAndStatusOrderByCreatedAtDesc(UUID sellerId, EventStatus status);

    @Query("SELECT e FROM Event e " +
        "WHERE e.sellerId = :sellerId " +
        "AND e.saleStartAt >= :periodStart " +
        "AND e.saleEndAt <= :periodEnd " +
        "ORDER BY e.createdAt DESC")
    List<Event> findEventsBySellerAndPeriod(
        @Param("sellerId") UUID sellerId,
        @Param("periodStart") LocalDateTime periodStart,
        @Param("periodEnd") LocalDateTime periodEnd
    );

    @Query("SELECT e FROM Event e " +
        "WHERE e.eventDateTime >= :startOfDay " +
        "AND e.eventDateTime < :startOfNextDay")
    List<Event> findAllByEventDate(
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("startOfNextDay") LocalDateTime startOfNextDay
    );


}
