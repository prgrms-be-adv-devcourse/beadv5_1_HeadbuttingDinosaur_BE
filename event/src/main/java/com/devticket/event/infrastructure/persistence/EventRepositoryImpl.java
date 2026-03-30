package com.devticket.event.infrastructure.persistence;

import static com.devticket.event.domain.model.QEvent.event;
import static com.devticket.event.domain.model.QEventTechStack.eventTechStack;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EventRepositoryImpl implements EventRepositoryCustom {

    private final JPAQueryFactory queryFactory;


    @Override
    public Page<Event> searchEvents(
        String keyword, EventCategory category, List<Long> techStacks,
        UUID sellerId, List<EventStatus> statuses, Pageable pageable) {

        JPAQuery<Event> query = queryFactory.selectFrom(event);

        if (techStacks != null && !techStacks.isEmpty()) {
            query.leftJoin(event.eventTechStacks, eventTechStack);
        }

        List<Event> content = query
            .where(
                keywordContains(keyword),
                categoryEq(category),
                techStackIn(techStacks),
                sellerEq(sellerId),
                statusIn(statuses)
            )
            .distinct()
            .orderBy(getOrderSpecifiers(pageable))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        JPAQuery<Long> countQuery = queryFactory.select(event.countDistinct()).from(event);

        if (techStacks != null && !techStacks.isEmpty()) {
            countQuery.leftJoin(event.eventTechStacks, eventTechStack);
        }

        countQuery.where(
            keywordContains(keyword),
            categoryEq(category),
            techStackIn(techStacks),
            sellerEq(sellerId),
            statusIn(statuses)
        );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // Pageable의 Sort를 QueryDSL의 OrderSpecifier 배열로 변환하는 유틸리티 메서드
    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();

        if (pageable.getSort().isSorted()) {
            for (Sort.Order sortOrder : pageable.getSort()) {
                Order direction = sortOrder.getDirection().isAscending() ? Order.ASC : Order.DESC;

                // 클라이언트가 요청한 정렬 필드명에 따라 매핑
                switch (sortOrder.getProperty()) {
                    case "createdAt":
                        orderSpecifiers.add(new OrderSpecifier<>(direction, event.createdAt));
                        break;
                    case "price":
                        orderSpecifiers.add(new OrderSpecifier<>(direction, event.price));
                        break;
                    case "eventDateTime":
                        orderSpecifiers.add(new OrderSpecifier<>(direction, event.eventDateTime));
                        break;
                    // 필요한 정렬 기준을 계속 추가할 수 있습니다.
                    default:
                        // 알 수 없는 필드명일 경우 기본 정렬 (최신순)
                        orderSpecifiers.add(new OrderSpecifier<>(Order.DESC, event.createdAt));
                        break;
                }
            }
        } else {
            // 정렬 조건이 아예 없을 때의 기본 정렬 세팅
            orderSpecifiers.add(new OrderSpecifier<>(Order.DESC, event.createdAt));
        }

        return orderSpecifiers.toArray(new OrderSpecifier[0]);
    }

    @Override
    public List<Event> findEventsBySeller(UUID sellerId, EventStatus status) {
        return queryFactory
            .selectFrom(event)
            .where(
                sellerEq(sellerId),
                statusEq(status)
            )
            .orderBy(event.createdAt.desc())
            .fetch();
    }

    // --- 동적 쿼리 조건 메서드들 ---

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        return event.title.containsIgnoreCase(keyword)
            .or(event.description.containsIgnoreCase(keyword));
    }

    private BooleanExpression categoryEq(EventCategory category) {
        return category != null ? event.category.eq(category) : null;
    }

    private BooleanExpression techStackIn(List<Long> techStacks) {
        if (techStacks == null || techStacks.isEmpty()) return null;
        return eventTechStack.techStackId.in(techStacks);
    }

    private BooleanExpression sellerEq(UUID sellerId) {
        return sellerId != null ? event.sellerId.eq(sellerId) : null;
    }

    private BooleanExpression statusEq(EventStatus status) {
        return status != null ? event.status.eq(status) : null;
    }

    private BooleanExpression statusIn(List<EventStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        return event.status.in(statuses);
    }

}
