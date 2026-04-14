package com.devticket.event.infrastructure.client;

import com.devticket.event.infrastructure.client.dto.InternalMemberInfoResponse;
import com.devticket.event.infrastructure.client.dto.TechStackItem;
import com.devticket.event.infrastructure.client.dto.TechStackListResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
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

    public List<TechStackItem> getTechStacks() {
        try {
            String url = memberServiceUrl + "/internal/members/tech-stacks";
            TechStackListResponse response = restTemplate.getForObject(url, TechStackListResponse.class);
            if (response == null) {
                return List.of();
            }
            return response.techStacks() != null ? response.techStacks() : List.of();
        } catch (Exception e) {
            log.warn("[기술스택 조회 실패] Member 서비스 호출 오류", e);
            return List.of();
        }
    }
}
