package com.devticket.member.infrastructure.external.client;

import com.devticket.member.common.exception.BusinessException;
import com.devticket.member.presentation.dto.internal.response.InternalAdminTechStackResponse;
import com.devticket.member.presentation.dto.internal.response.InternalAdminTechStackResponse.TechStackInfo;
import com.devticket.member.presentation.domain.MemberErrorCode;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInternalClientImpl implements AdminInternalClient {

    private static final Duration TECH_STACK_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;

    @Value("${internal.admin-service.url}")
    private String uri;

    @Override
    public InternalAdminTechStackResponse getTechStacks() {
        try {
            List<TechStackInfo> techStacks = webClient.get()
                .uri(uri + "/internal/admin/tech-stacks")
                .retrieve()
                .bodyToFlux(InternalAdminTechStackResponse.TechStackInfo.class)
                .collectList()
                .block(TECH_STACK_TIMEOUT);
            return new InternalAdminTechStackResponse(techStacks);
        } catch (Exception e) {
            log.error("[AdminInternalClient] TechStack 조회 실패", e);
            throw new BusinessException(MemberErrorCode.ADMIN_INTERNAL_API_FAILED);
        }
    }

}