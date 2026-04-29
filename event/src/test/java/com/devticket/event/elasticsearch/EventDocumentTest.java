package com.devticket.event.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventTechStack;
import com.devticket.event.infrastructure.search.EventDocument;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * EventDocument 변환 로직 단위 테스트.
 * Spring 컨텍스트 / ES 불필요.
 */
class EventDocumentTest {

    // ── EventDocument.from() 변환 검증 ────────────────────────────────────

    @Test
    void from_이벤트의_모든_기본_필드를_올바르게_변환한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = Event.create(
            sellerId, "스프링 부트 밋업", "설명", "강남역",
            LocalDateTime.now().plusDays(15),
            LocalDateTime.now().plusDays(4),
            LocalDateTime.now().plusDays(10),
            50000, 100, 4, EventCategory.MEETUP
        );
        UUID eventId = UUID.randomUUID();
        ReflectionTestUtils.setField(event, "eventId", eventId);

        // when
        EventDocument doc = EventDocument.from(event);

        // then
        assertThat(doc.getId()).isEqualTo(eventId.toString());
        // AI 추천 서비스 계약 (PR #540) — EventInternalService 재색인 경로에서도 body 에 eventId 포함되어야 함
        assertThat(doc.getEventId()).isEqualTo(eventId.toString());
        assertThat(doc.getTitle()).isEqualTo("스프링 부트 밋업");
        assertThat(doc.getCategory()).isEqualTo(EventCategory.MEETUP.name());
        assertThat(doc.getStatus()).isEqualTo(EventStatus.DRAFT.name());
        assertThat(doc.getSellerId()).isEqualTo(sellerId.toString());
    }

    @Test
    void from_techStacks가_있는_이벤트는_이름_목록으로_변환된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = Event.create(
            sellerId, "기술 밋업", "설명", "판교",
            LocalDateTime.now().plusDays(10),
            LocalDateTime.now().plusDays(3),
            LocalDateTime.now().plusDays(7),
            30000, 50, 5, EventCategory.CONFERENCE
        );
        ReflectionTestUtils.setField(event, "eventId", UUID.randomUUID());

        EventTechStack spring = EventTechStack.of(event, 1L, "Spring");
        EventTechStack react = EventTechStack.of(event, 2L, "React");
        event.getEventTechStacks().add(spring);
        event.getEventTechStacks().add(react);

        // when
        EventDocument doc = EventDocument.from(event);

        // then
        assertThat(doc.getTechStacks()).containsExactlyInAnyOrder("Spring", "React");
    }

    @Test
    void from_techStacks가_없는_이벤트는_빈_리스트로_변환된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = Event.create(
            sellerId, "테크 없는 이벤트", "설명", "서울",
            LocalDateTime.now().plusDays(10),
            LocalDateTime.now().plusDays(3),
            LocalDateTime.now().plusDays(7),
            0, 50, 5, EventCategory.HACKATHON
        );
        ReflectionTestUtils.setField(event, "eventId", UUID.randomUUID());

        // when
        EventDocument doc = EventDocument.from(event);

        // then
        assertThat(doc.getTechStacks()).isEmpty();
    }

    @Test
    void from_indexedAt은_초_단위로_truncate된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = Event.create(
            sellerId, "타임스탬프 테스트", "설명", "서울",
            LocalDateTime.now().plusDays(10),
            LocalDateTime.now().plusDays(3),
            LocalDateTime.now().plusDays(7),
            0, 50, 5, EventCategory.MEETUP
        );
        ReflectionTestUtils.setField(event, "eventId", UUID.randomUUID());

        LocalDateTime beforeConvert = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        // when
        EventDocument doc = EventDocument.from(event);

        // then — 나노초가 없어야 한다
        assertThat(doc.getIndexedAt().getNano()).isZero();
        assertThat(doc.getIndexedAt()).isAfterOrEqualTo(beforeConvert);
    }

    // ── EventDocument.builder() 검증 ──────────────────────────────────────

    @Test
    void builder_id만_지정해도_나머지_필드는_null로_생성된다() {
        // given
        String id = UUID.randomUUID().toString();

        // when
        EventDocument doc = EventDocument.builder()
            .id(id)
            .build();

        // then
        assertThat(doc.getId()).isEqualTo(id);
        assertThat(doc.getTitle()).isNull();
        assertThat(doc.getCategory()).isNull();
        assertThat(doc.getTechStacks()).isNull();
        assertThat(doc.getStatus()).isNull();
        assertThat(doc.getSellerId()).isNull();
    }

    @Test
    void builder_모든_필드를_명시하면_그대로_저장된다() {
        // given
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        // when
        EventDocument doc = EventDocument.builder()
            .id(id)
            .title("전체 필드 테스트")
            .category("MEETUP")
            .status("ON_SALE")
            .sellerId(UUID.randomUUID().toString())
            .techStacks(java.util.List.of("Java", "Kotlin"))
            .indexedAt(now)
            .build();

        // then
        assertThat(doc.getId()).isEqualTo(id);
        assertThat(doc.getTitle()).isEqualTo("전체 필드 테스트");
        assertThat(doc.getCategory()).isEqualTo("MEETUP");
        assertThat(doc.getStatus()).isEqualTo("ON_SALE");
        assertThat(doc.getTechStacks()).containsExactly("Java", "Kotlin");
        assertThat(doc.getIndexedAt()).isEqualTo(now);
    }
}
