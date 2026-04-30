package com.devticket.payment.refund.infrastructure.client;

import com.devticket.payment.refund.infrastructure.client.dto.InternalApiResponse;
import com.devticket.payment.refund.infrastructure.client.dto.InternalEventForceCancelRequest;
import com.devticket.payment.refund.infrastructure.client.dto.InternalEventInfoResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
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
        InternalApiResponse<InternalEventInfoResponse> response = restClient.get()
            .uri("/internal/events/{eventId}", eventId)
            .retrieve()
            .body(new ParameterizedTypeReference<InternalApiResponse<InternalEventInfoResponse>>() {});

        if (response == null || response.data() == null) {
            throw new IllegalStateException("Event internal API 응답이 비어있습니다: eventId=" + eventId);
        }
        return response.data();
    }

    public void forceCancel(UUID eventId, UUID userId, String reason) {
        restClient.patch()
            .uri("/internal/events/{eventId}/force-cancel", eventId)
            .header("X-User-Id", userId.toString())
            .body(new InternalEventForceCancelRequest(reason))
            .retrieve()
            .toBodilessEntity();
    }
}
