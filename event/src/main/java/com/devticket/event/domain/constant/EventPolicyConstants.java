package com.devticket.event.domain.constant;

public final class EventPolicyConstants {

    private EventPolicyConstants() {
        // 인스턴스화 방지
    }

    public static final int EVENT_MIN_PRICE = 0;
    public static final int EVENT_MAX_PRICE = 9_999_999;

    public static final int EVENT_MIN_CAPACITY = 5;
    public static final int EVENT_MAX_CAPACITY = 9_999;

    public static final int EVENT_MAX_IMAGES = 2;

    public static final int EVENT_REGISTER_DEADLINE_DAYS = 3;
    public static final int EVENT_EDIT_DEADLINE_DAYS = 1;
}
