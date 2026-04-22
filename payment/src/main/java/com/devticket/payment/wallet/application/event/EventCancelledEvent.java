package com.devticket.payment.wallet.application.event;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EventCancelledEvent {

    private Long eventId;
    private Long sellerId;
    private String cancelledBy; // "ADMIN" | "SELLER"
    private Long adminId;       // cancelledBy=ADMIN 시에만 존재 (nullable)
    private LocalDateTime timestamp;
}
