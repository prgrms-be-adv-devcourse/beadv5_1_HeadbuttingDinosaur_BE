package com.devticket.event.infrastructure.persistence;

import static com.devticket.event.domain.model.QEvent.event;
import static com.devticket.event.domain.model.QEventTechStack.eventTechStack;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.model.Event;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EventRepositoryImpl implements EventRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Event> searchEvents(String keyword, EventCategory category, List<Long> techStackIds, Pageable pageable) {

        // 1. 데이터 조회 쿼리 (기술 스택 필터링을 위한 JOIN 및 distinct 포함)
        List<Event> content = queryFactory
            .selectFrom(event)
            .leftJoin(event.eventTechStacks, eventTechStack) // Event 엔티티 내의 연관관계 필드명
            .where(
                keywordContains(keyword),
                categoryEq(category),
                techStackIn(techStackIds)
            )
            .distinct()
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        // 2. 카운트 쿼리 (페이징 처리를 위함)
        JPAQuery<Long> countQuery = queryFactory
            .select(event.countDistinct())
            .from(event)
            .leftJoin(event.eventTechStacks, eventTechStack)
            .where(
                keywordContains(keyword),
                categoryEq(category),
                techStackIn(techStackIds)
            );

        // PageableExecutionUtils를 쓰면 필요할 때만 count 쿼리를 날려 성능이 최적화됩니다.
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // --- 동적 쿼리를 위한 다형성 조건 메서드들 ---

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return event.title.containsIgnoreCase(keyword)
            .or(event.description.containsIgnoreCase(keyword));
    }

    private BooleanExpression categoryEq(EventCategory category) {
        if (category == null) {
            return null;
        }
        return event.category.eq(category);
    }

    private BooleanExpression techStackIn(List<Long> techStacks) {
        if (techStacks == null || techStacks.isEmpty()) {
            return null;
        }
        return eventTechStack.techStackId.in(techStacks);
    }
}
