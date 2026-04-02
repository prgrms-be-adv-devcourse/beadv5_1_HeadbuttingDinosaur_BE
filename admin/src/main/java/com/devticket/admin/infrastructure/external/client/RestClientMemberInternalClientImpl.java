package com.devticket.admin.infrastructure.external.client;

import com.devticket.admin.infrastructure.external.dto.req.InternalDecideSellerApplicationRequest;
import com.devticket.admin.infrastructure.external.dto.res.InternalDecideSellerApplicationResponse;
import com.devticket.admin.infrastructure.external.dto.res.InternalMemberInfoResponse;
import com.devticket.admin.infrastructure.external.dto.res.InternalSellerApplicationResponse;
import com.devticket.admin.presentation.dto.req.UserRoleRequest;
import com.devticket.admin.presentation.dto.req.UserSearchCondition;
import com.devticket.admin.presentation.dto.req.UserStatusRequest;
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
    public List<InternalMemberInfoResponse> getMembers(UserSearchCondition condition) {
        return List.of(
            new InternalMemberInfoResponse(
                "550e8400-e29b-41d4-a716-446655440000",
                "test@test.com",
                "SELLER",
                "ACTIVE",
                "Google"
            )

        );
    }

    @Override
    public void updateUserStatus(UUID userId, UserStatusRequest request) {

    }

    @Override
    public void updateUserRole(UUID userId, UserRoleRequest request) {

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
