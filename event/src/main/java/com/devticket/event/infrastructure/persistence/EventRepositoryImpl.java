package com.devticket.event.infrastructure.persistence;

import static com.devticket.event.domain.model.QEvent.event;
import static com.devticket.event.domain.model.QEventTechStack.eventTechStack;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.presentation.dto.EventListRequest;
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
    public Page<Event> searchEvents(EventListRequest request, boolean isOwnEventRequest, Pageable pageable) {

        // 1. 기본 쿼리 시작 (불필요한 조인 제거)
        JPAQuery<Event> query = queryFactory.selectFrom(event);

        // 2. 기술 스택 필터가 있을 때만 동적으로 JOIN 추가
        if (request.techStacks() != null && !request.techStacks().isEmpty()) {
            query.leftJoin(event.eventTechStacks, eventTechStack);
        }

        // 3. 조건 및 페이징 적용
        List<Event> content = query
            .where(
                keywordContains(request.keyword()),
                categoryEq(request.category()),
                techStackIn(request.techStacks()),
                sellerEq(request.sellerId()),
                statusEq(request.status()),
                publicVisibilityEq(isOwnEventRequest, request.status()) // 권한 필터링
            )
            .distinct() // 기술 스택 다중 선택 시 중복 방지
            .orderBy(getOrderSpecifiers(pageable))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        // 4. 카운트 쿼리 (마찬가지로 동적 조인 적용)
        JPAQuery<Long> countQuery = queryFactory.select(event.countDistinct()).from(event);

        if (request.techStacks() != null && !request.techStacks().isEmpty()) {
            countQuery.leftJoin(event.eventTechStacks, eventTechStack);
        }

        countQuery.where(
            keywordContains(request.keyword()),
            categoryEq(request.category()),
            techStackIn(request.techStacks()),
            sellerEq(request.sellerId()),
            statusEq(request.status()),
            publicVisibilityEq(isOwnEventRequest, request.status())
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

    // 비공개 이벤트 차단 방어 로직
    private BooleanExpression publicVisibilityEq(boolean isOwnEventRequest, EventStatus requestedStatus) {
        if (isOwnEventRequest || requestedStatus != null) return null;
        return event.status.in(EventStatus.ON_SALE, EventStatus.SOLD_OUT, EventStatus.SALE_ENDED);
    }

}
