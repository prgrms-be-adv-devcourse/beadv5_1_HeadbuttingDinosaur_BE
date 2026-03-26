package com.devticket.event.domain.model;

import com.devticket.event.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "event_tech_stack")
public class EventTechStack extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "tech_stack_id", nullable = false)
    private Long techStackId;

    @Builder(access = AccessLevel.PRIVATE)
    private EventTechStack(Event event, Long techStackId) {
        this.event = event;
        this.techStackId = techStackId;
    }

    public static EventTechStack of(Event event, Long techStackId) {
        return EventTechStack.builder()
            .event(event)
            .techStackId(techStackId)
            .build();
    }

}
