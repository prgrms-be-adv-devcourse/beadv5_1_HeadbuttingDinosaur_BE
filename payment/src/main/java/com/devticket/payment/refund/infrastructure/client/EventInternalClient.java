package com.devticket.payment.refund.infrastructure.client;

import com.devticket.payment.refund.infrastructure.client.dto.InternalEventInfoResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class EventInternalClient {

    private final RestClient restClient;

    public EventInternalClient(
        @Value("${internal.event.base-url}") String baseUrl) {

        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    public InternalEventInfoResponse getEventInfo(UUID eventId) {
        return restClient.get()
            .uri("/internal/events/{eventId}", eventId)
            .retrieve()
            .body(InternalEventInfoResponse.class);
    }
}
