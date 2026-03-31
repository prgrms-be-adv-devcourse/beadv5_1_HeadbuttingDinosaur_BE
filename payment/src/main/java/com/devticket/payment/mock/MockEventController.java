package com.devticket.payment.mock;

import com.devticket.payment.refund.infrastructure.client.dto.InternalEventInfoResponse;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/event")
@Profile("local")
public class MockEventController {

    @GetMapping("/internal/events/{eventId}")
    public InternalEventInfoResponse getEventInfo(@PathVariable String eventId) {
        return new InternalEventInfoResponse(
            15L,
            7L,
            "Spring Boot 심화 밋업",
            50000,
            "ON_SALE",
            "MEETUP",
            50,
            4,
            23,
            LocalDateTime.now().plusDays(10).toString(),  // 환불율 100% 테스트 (7일 이상)
            LocalDateTime.now().minusDays(5).toString(),
            LocalDateTime.now().plusDays(9).toString()
        );
    }
}
