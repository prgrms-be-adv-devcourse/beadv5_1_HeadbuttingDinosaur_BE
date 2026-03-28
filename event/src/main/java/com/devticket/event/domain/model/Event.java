package com.devticket.event.domain.model;

import com.devticket.event.common.entity.BaseEntity;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL") // Soft Delete: 조회 시 삭제된 데이터 제외
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID sellerId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 255)
    private String location;

    @Column(nullable = false)
    private LocalDateTime eventDateTime;

    @Column(nullable = false)
    private LocalDateTime saleStartAt;

    @Column(nullable = false)
    private LocalDateTime saleEndAt;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer maxQuantity;

    @Column(nullable = false)
    private Integer remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventCategory category;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventTechStack> eventTechStacks = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private Event(UUID eventId, UUID sellerId, String title, String description, String location,
        LocalDateTime eventDateTime, LocalDateTime saleStartAt, LocalDateTime saleEndAt,
        Integer price, Integer totalQuantity, Integer maxQuantity, Integer remainingQuantity,
        EventStatus status, EventCategory category) {
        this.eventId = eventId;
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
        this.remainingQuantity = remainingQuantity;
        this.status = status;
        this.category = category;
    }

    public static Event create(
        UUID sellerId, String title, String description, String location,
        LocalDateTime eventDateTime, LocalDateTime saleStartAt, LocalDateTime saleEndAt,
        Integer price, Integer totalQuantity, Integer maxQuantity, EventCategory category
    ) {
        return Event.builder()
            .eventId(UUID.randomUUID())
            .sellerId(sellerId)
            .title(title)
            .description(description)
            .location(location)
            .eventDateTime(eventDateTime)
            .saleStartAt(saleStartAt)
            .saleEndAt(saleEndAt)
            .price(price)
            .totalQuantity(totalQuantity)
            .maxQuantity(maxQuantity)
            .remainingQuantity(totalQuantity)
            .status(EventStatus.DRAFT)
//            .status(EventStatus.ON_SALE)
            .category(category)
            .build();
    }
}