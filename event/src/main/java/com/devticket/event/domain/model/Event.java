package com.devticket.event.domain.model;

import com.devticket.event.common.entity.BaseEntity;
import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.EventErrorCode;
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
@Table(name = "events", schema = "event")
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

    @Column(nullable = false)
    private Integer cancelledQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventCategory category;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventTechStack> eventTechStacks = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventImage> eventImages = new ArrayList<>();

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
//            .status(EventStatus.DRAFT)
            .status(EventStatus.ON_SALE)
            .category(category)
            .build();
    }

    public boolean canBeUpdated() {
        return this.status == EventStatus.DRAFT || this.status == EventStatus.ON_SALE;
    }

    public boolean canBeCancelled() {
        return this.status != EventStatus.CANCELLED
            && this.status != EventStatus.FORCE_CANCELLED
            && this.status != EventStatus.SALE_ENDED;
    }

    public void update(String title, String description, String location,
        LocalDateTime eventDateTime, LocalDateTime saleStartAt, LocalDateTime saleEndAt,
        Integer price, Integer totalQuantity, Integer maxQuantity, EventCategory category) {
        int delta = totalQuantity - this.totalQuantity;
        this.remainingQuantity = Math.max(0, this.remainingQuantity + delta);
        this.title = title;
        this.description = description;
        this.location = location;
        this.eventDateTime = eventDateTime;
        this.saleStartAt = saleStartAt;
        this.saleEndAt = saleEndAt;
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.maxQuantity = maxQuantity;
        this.category = category;
    }

    public void cancel() {
        this.status = EventStatus.CANCELLED;
    }

    public void deductStock(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(EventErrorCode.INVALID_STOCK_QUANTITY);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(this.saleStartAt) || now.isAfter(this.saleEndAt)) {
            throw new BusinessException(EventErrorCode.PURCHASE_NOT_ALLOWED);
        }
        if (this.status != EventStatus.ON_SALE) {
            throw new BusinessException(EventErrorCode.PURCHASE_NOT_ALLOWED);
        }
        if (quantity > this.maxQuantity) {
            throw new BusinessException(EventErrorCode.MAX_QUANTITY_EXCEEDED);
        }
        if (this.remainingQuantity < quantity) {
            throw new BusinessException(EventErrorCode.OUT_OF_STOCK);
        }
        this.remainingQuantity -= quantity;
        if (this.remainingQuantity == 0) {
            this.status = EventStatus.SOLD_OUT;
        }
    }

    public void restoreStock(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(EventErrorCode.INVALID_STOCK_QUANTITY);
        }
        if (this.status == EventStatus.CANCELLED || this.status == EventStatus.FORCE_CANCELLED) {
            throw new BusinessException(EventErrorCode.CANNOT_CHANGE_STATUS);
        }
        this.remainingQuantity = Math.min(this.totalQuantity, this.remainingQuantity + quantity);
        if (this.status == EventStatus.SOLD_OUT && this.remainingQuantity > 0) {
            this.status = EventStatus.ON_SALE;
        }
    }

    public boolean isPurchasable(int requestedQuantity) {
        if (requestedQuantity < 1) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return this.status == EventStatus.ON_SALE
            && !now.isBefore(this.saleStartAt) && !now.isAfter(this.saleEndAt)
            && this.remainingQuantity >= requestedQuantity
            && requestedQuantity <= this.maxQuantity;
    }

}