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
        UserTechStackRequest request = new UserTechStackRequest(userId);

        log.info("[MemberClient] TechStack 조회 (Mock) - userId: {}", userId);

        // UserTeckStack Mock 데이터
        List<UserTechStackResponse.TechStackInfo> mockStacks = List.of(
            new UserTechStackResponse.TechStackInfo("1", "Java"),
            new UserTechStackResponse.TechStackInfo("2", "Spring")
        );

        return new UserTechStackResponse(userId, mockStacks);
    }

}
