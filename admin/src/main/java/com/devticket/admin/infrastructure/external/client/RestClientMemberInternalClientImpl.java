package com.devticket.admin.infrastructure.external.client;

import com.devticket.admin.infrastructure.external.dto.req.InternalDecideSellerApplicationRequest;
import com.devticket.admin.infrastructure.external.dto.res.InternalDecideSellerApplicationResponse;
import com.devticket.admin.infrastructure.external.dto.res.InternalSellerApplicationResponse;
import com.devticket.admin.presentation.dto.req.UserRoleRequest;
import com.devticket.admin.presentation.dto.req.UserSearchCondition;
import com.devticket.admin.presentation.dto.req.UserStatusRequest;
import com.devticket.admin.presentation.dto.res.InternalMemberDetailResponse;
import com.devticket.admin.presentation.dto.res.InternalMemberPageResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;


@Primary
@RequiredArgsConstructor
@Component
public class RestClientMemberInternalClientImpl implements MemberInternalClient {

    private final RestClient restClient;

    @Value("${internal.member-service.url}")
    private String memberServerUrl;

    @Override
    public InternalMemberPageResponse getMembers(UserSearchCondition condition) {
        InternalMemberPageResponse page = restClient.get()
            .uri(memberServerUrl + "/internal/members?role={role}&status={status}&keyword={keyword}",
                condition.role(),
                condition.status(),
                condition.keyword())
            .retrieve()
            .body(new ParameterizedTypeReference<InternalMemberPageResponse>() {});
        return page != null ? page : new InternalMemberPageResponse(List.of(), 0, 0, 0, 0);
    }


    @Override
    public void updateUserStatus(UUID userId, UserStatusRequest request) {
        restClient.patch()
            .uri(memberServerUrl + "/internal/members/{userId}/status", userId)
            .body(request)
            .retrieve()
            .toBodilessEntity();
    }


    @Override
    public void updateUserRole(UUID userId, UserRoleRequest request) {
        restClient.patch()
            .uri(memberServerUrl + "/internal/members/{userId}/role", userId)
            .body(request)
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public InternalMemberDetailResponse getMember(UUID userId) {
        return restClient.get()
            .uri(memberServerUrl + "/internal/members/{userId}", userId)
            .retrieve()
            .body(InternalMemberDetailResponse.class);
    }

    // 판매자 신청 유저 조회
    @Override
    public List<InternalSellerApplicationResponse> getSellerApplications() {
        return restClient.get()
            .uri(memberServerUrl + "/internal/members/seller-applications")
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    // 판매자 신청 결정
    @Override
    public InternalDecideSellerApplicationResponse decideSellerApplication(UUID applicationId,
        InternalDecideSellerApplicationRequest request) {
        return restClient.patch()
            .uri(memberServerUrl + "/internal/members/seller-applications/{applicationId}", applicationId)
            .body(request)
            .retrieve()
            .body(InternalDecideSellerApplicationResponse.class);
    }
}
