package com.devticket.event.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventStatus {
    DRAFT("임시 저장"),
    ON_SALE("판매 중"),
    SOLD_OUT("매진"),
    SALE_ENDED("판매 종료"),
    ENDED("행사 종료"),
    CANCELLED("취소됨(판매자)"),
    FORCE_CANCELLED("강제 취소됨(어드민)");

    private final String description;
}
