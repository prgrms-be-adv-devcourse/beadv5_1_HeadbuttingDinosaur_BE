package com.devticket.event.infrastructure.client;

import com.devticket.event.infrastructure.client.dto.InternalMemberInfoResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class MemberClient {

    private final RestTemplate restTemplate;

    @Value("${service.member.url}")
    private String memberServiceUrl;

    public String getNickname(UUID sellerId) {
        try {
            InternalMemberInfoResponse response = restTemplate.getForObject(
                memberServiceUrl + "/internal/members/" + sellerId,
                InternalMemberInfoResponse.class
            );
            return response != null ? response.nickname() : null;
        } catch (Exception e) {
            return null; // 장애 시 null 허용 (이벤트 상세 자체는 살림)
        }
    }
}
