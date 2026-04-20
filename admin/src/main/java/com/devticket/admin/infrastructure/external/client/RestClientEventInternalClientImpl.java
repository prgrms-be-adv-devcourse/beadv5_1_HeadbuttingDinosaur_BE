package com.devticket.admin.infrastructure.external.client;

import com.devticket.admin.infrastructure.external.dto.req.InternalEventForceCancelRequest;
import com.devticket.admin.infrastructure.external.dto.res.InternalAdminEventPageResponse;
import com.devticket.admin.infrastructure.external.dto.res.InternalResponse;
import com.devticket.admin.presentation.dto.req.AdminEventSearchRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Primary
@Component
@RequiredArgsConstructor
public class RestClientEventInternalClientImpl implements EventInternalClient {

    private final RestClient restClient;

    @Value("${internal.event-service.url}")
    private String eventServerUrl;

    @Override
    public InternalAdminEventPageResponse getEvents(AdminEventSearchRequest condition) {
        InternalResponse<InternalAdminEventPageResponse> response = restClient.get()
            .uri(eventServerUrl + "/internal/events?keyword={k}&status={s}&sellerId={sid}&page={p}&size={z}",
                nvl(condition.keyword()),
                nvl(condition.status()),
                nvl(condition.sellerId()),
                condition.page() == null ? 0 : condition.page(),
                condition.size() == null ? 20 : condition.size())
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<
                InternalResponse<InternalAdminEventPageResponse>>() {});

        if (response == null || response.data() == null) {
            return new InternalAdminEventPageResponse(java.util.List.of(), 0, 20, 0L, 0);
        }

        return response.data();
    }

    @Override
    public void forceCancel(UUID adminId, UUID eventId) {
        restClient.patch()
            .uri(eventServerUrl + "/internal/events/{eventId}/force-cancel", eventId)
            .header("X-User-Id", adminId.toString())
            .header("X-User-Role", "ADMIN")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new InternalEventForceCancelRequest("관리자 강제 취소"))
            .retrieve()
            .toBodilessEntity();
    }

    private static String nvl(String v) { return v == null ? "" : v; }
}
