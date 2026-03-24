package com.devticket.event.domain.model;

import com.devticket.event.domain.exception.BusinessException;
import com.devticket.event.domain.exception.EventErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String location;

    @Column(name = "event_date_time", nullable = false)
    private LocalDateTime eventDateTime;

    @Column(name = "sale_start_at", nullable = false)
    private LocalDateTime saleStartAt;

    @Column(name = "sale_end_at", nullable = false)
    private LocalDateTime saleEndAt;

    @Column(nullable = false)
    private Integer price;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "max_quantity", nullable = false)
    private Integer maxQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventCategory category;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventTechStack> eventTechStacks = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public Event(UUID sellerId, String title, String description, String location,
        LocalDateTime eventDateTime, LocalDateTime saleStartAt, LocalDateTime saleEndAt,
        Integer price, Integer totalQuantity, Integer maxQuantity, EventCategory category) {

        validateConstraints(price, totalQuantity, saleStartAt, saleEndAt, eventDateTime);

        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.location = location;
        this.eventDateTime = eventDateTime;
        this.saleStartAt = saleStartAt;
        this.saleEndAt = saleEndAt;
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.maxQuantity = maxQuantity;
        this.remainingQuantity = totalQuantity;
        this.status = EventStatus.ON_SALE;
        this.category = category;
    }

    private void validateConstraints(Integer price, Integer totalQuantity,
        LocalDateTime saleStartAt, LocalDateTime saleEndAt,
        LocalDateTime eventDateTime) {
        if (price < 0 || price > 9999999) {
            throw new BusinessException(EventErrorCode.INVALID_PRICE); // EVENT_003
        }
        if (totalQuantity < 5 || totalQuantity > 9999) {
            throw new BusinessException(EventErrorCode.INVALID_QUANTITY); // EVENT_004
        }
        if (saleStartAt.isAfter(saleEndAt) || saleEndAt.isAfter(eventDateTime)) {
            throw new BusinessException(EventErrorCode.INVALID_EVENT_DATES);
        }
    }
}