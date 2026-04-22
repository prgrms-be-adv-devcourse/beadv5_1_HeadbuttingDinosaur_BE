package com.devticket.commerce.ticket.infrastructure.external.client;

import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.exception.CommonErrorCode;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalMemberInfoResponse;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class TicketToMemberClient {

    private final RestClient restClient;

    public TicketToMemberClient(@Qualifier("ticketToMemberRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public InternalMemberInfoResponse getMemberInfo(UUID userId) {
        try {
            log.info("[TicketToMemberClient] getMemberInfo - userId: {}", userId);

            return restClient.get()
                .uri("/internal/members/{userId}", userId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[TicketToMemberClient] External API Error: Status {}", res.getStatusCode());
                    throw new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
                })
                .body(InternalMemberInfoResponse.class);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TicketToMemberClient] Critical Error: ", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
