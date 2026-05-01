package org.example.ai.domain.repository;

import java.util.List;
import org.example.ai.infrastructure.external.dto.res.UserTechStackResponse;

public interface MemberRepository {
    UserTechStackResponse getUserTechStack(String userId);
}
