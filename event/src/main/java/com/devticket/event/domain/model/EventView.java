package com.devticket.event.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "event_view", schema = "event")
public class EventView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 1:1 관계
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, unique = true, updatable = false)
    private Event event;

    @Column(nullable = false)
    private Long viewCount = 0L;


    public static EventView of(Event event) {
        EventView eventView = new EventView();
        eventView.event = event;
        eventView.viewCount = 0L;
        return eventView;
    }

    public void increaseViewCount() {
        this.viewCount++;
    }


}
