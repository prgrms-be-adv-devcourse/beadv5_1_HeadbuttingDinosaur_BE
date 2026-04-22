package org.example.ai.infrastructure.external.client;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.infrastructure.external.dto.req.UserTechStackRequest;
import org.example.ai.infrastructure.external.dto.res.UserTechStackResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@RequiredArgsConstructor
@Component
public class MemberServiceClient {

    private final WebClient webClient;

    @Value("${internal.member-service.url}")
    private String memberServiceUrl;

    public UserTechStackResponse getUserTechStack(String userId){
        log.info("[MemberClient] TechStack 조회 - userId: {}", userId);

        try {
            return webClient.get()
                .uri(memberServiceUrl + "/internal/members/" + userId + "/tech-stacks")
                .retrieve()
                .bodyToMono(UserTechStackResponse.class)
                .block();
        } catch (Exception e) {
            log.error("[MemberClient] TechStack 조회 실패 - userId: {}", userId, e);
            return new UserTechStackResponse(userId, List.of());
        }
    }

}
