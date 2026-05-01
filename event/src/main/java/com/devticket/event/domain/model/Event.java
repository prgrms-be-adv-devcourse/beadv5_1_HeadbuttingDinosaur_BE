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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "events", schema = "event")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SQLRestriction("deleted_at IS NULL")
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
    @Builder.Default
    private Integer cancelledQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventCategory category;

    @Version
    private Long version;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EventTechStack> eventTechStacks = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EventImage> eventImages = new ArrayList<>();

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
            .status(LocalDateTime.now().isBefore(saleStartAt) ? EventStatus.DRAFT : EventStatus.ON_SALE)
            .category(category)
            .build();
    }

    public boolean canBeUpdated() {
        return this.status == EventStatus.DRAFT || this.status == EventStatus.ON_SALE;
    }

    public boolean canBeCancelled() {
        return this.status != EventStatus.CANCELLED
            && this.status != EventStatus.FORCE_CANCELLED
            && this.status != EventStatus.SALE_ENDED
            && this.status != EventStatus.ENDED;
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

    /**
     * 판매 중지 (Action B) — status=CANCELLED 로 전이.
     * 신규 판매만 차단하고 기존 구매자에게는 영향 없음 — 환불 트리거 X.
     *
     * <p>셀러가 "이벤트 수정" 화면에서 status 를 CANCELLED 로 명시 변경할 때만 호출됨
     * ({@code EventService.updateEvent} 경로).
     * 기존 구매자 환불을 동반하는 강제 취소는 {@link #forceCancel()} 사용.
     */
    public void cancel() {
        this.status = EventStatus.CANCELLED;
    }

    /**
     * 강제 취소 (Action A) — status=FORCE_CANCELLED 로 전이.
     * 환불 fan-out 을 트리거하므로 기존 구매자에게 환불 처리됨.
     *
     * <p>어드민 또는 셀러(본인 이벤트) 의 강제 취소 시 호출됨
     * ({@code EventService.forceCancel} 경로).
     * 단순 판매 중단은 {@link #cancel()} 사용.
     */
    public void forceCancel() {
        if (!canBeCancelled()) {
            throw new BusinessException(EventErrorCode.CANNOT_CHANGE_STATUS);
        }
        this.status = EventStatus.FORCE_CANCELLED;
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
        if (this.status == EventStatus.CANCELLED
                || this.status == EventStatus.FORCE_CANCELLED
                || this.status == EventStatus.ENDED) {
            throw new BusinessException(EventErrorCode.CANNOT_CHANGE_STATUS);
        }
        this.remainingQuantity = Math.min(this.totalQuantity, this.remainingQuantity + quantity);
        if (this.status == EventStatus.SOLD_OUT && this.remainingQuantity > 0) {
            this.status = EventStatus.ON_SALE;
        }
    }

    /**
     * 종료된 이벤트(CANCELLED / FORCE_CANCELLED / ENDED)의 환불 카운터.
     * remainingQuantity 는 그대로 두고 cancelledQuantity 만 누적.
     * 운영 대시보드 / 정산에서 "결제됐다가 영구 환불된 티켓 수" 추적 용도.
     */
    public void markCancelledStock(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(EventErrorCode.INVALID_STOCK_QUANTITY);
        }
        int current = this.cancelledQuantity == null ? 0 : this.cancelledQuantity;
        this.cancelledQuantity = current + quantity;
    }

    public void expireSale() {
        if ((this.status == EventStatus.ON_SALE || this.status == EventStatus.SOLD_OUT)
                && LocalDateTime.now().isAfter(this.saleEndAt)) {
            this.status = EventStatus.SALE_ENDED;
        }
    }

    public void endEvent() {
        if ((this.status == EventStatus.ON_SALE
                || this.status == EventStatus.SOLD_OUT
                || this.status == EventStatus.SALE_ENDED)
                && LocalDateTime.now().isAfter(this.eventDateTime)) {
            this.status = EventStatus.ENDED;
        }
    }

    public void promoteToOnSale() {
        if (this.status == EventStatus.DRAFT && !LocalDateTime.now().isBefore(this.saleStartAt)) {
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