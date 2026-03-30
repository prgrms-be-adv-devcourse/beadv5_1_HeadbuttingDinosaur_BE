package com.devticket.admin.infrastructure.external.client;

import com.devticket.admin.infrastructure.external.dto.InternalMemberInfoResponse;
import com.devticket.admin.presentation.dto.req.UserRoleRequest;
import com.devticket.admin.presentation.dto.req.UserSearchCondition;
import com.devticket.admin.presentation.dto.req.UserStatusRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;


@Component
public class MockMemberInternalClientImpl implements MemberInternalClient {

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
}
