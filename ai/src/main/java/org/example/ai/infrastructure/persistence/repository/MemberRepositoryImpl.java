package org.example.ai.infrastructure.persistence.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.ai.domain.repository.MemberRepository;
import org.example.ai.infrastructure.external.client.MemberServiceClient;
import org.example.ai.infrastructure.external.dto.res.UserTechStackResponse;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberServiceClient memberServiceClient;

    @Override
    public UserTechStackResponse getUserTechStack(String userId) {
        return memberServiceClient.getUserTechStack(userId);

    }
}
