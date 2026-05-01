package com.devticket.payment.payment.domain.enums;

import java.util.Set;

public enum PaymentStatus {
    READY,
    SUCCESS,
    FAILED,
    CANCELLED,
    REFUNDED;

    public boolean canTransitionTo(PaymentStatus target) {
        return getAllowedTransitions().contains(target);
    }

    private Set<PaymentStatus> getAllowedTransitions() {
        return switch (this) {
            case READY -> Set.of(SUCCESS, FAILED, CANCELLED);
            case SUCCESS -> Set.of(REFUNDED, CANCELLED);
            case FAILED, CANCELLED, REFUNDED -> Set.of();
        };
    }
}
