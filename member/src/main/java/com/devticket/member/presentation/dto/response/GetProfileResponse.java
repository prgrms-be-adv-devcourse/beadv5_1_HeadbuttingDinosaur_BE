package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.User;
import com.devticket.member.presentation.domain.model.UserProfile;
import com.devticket.member.presentation.dto.internal.response.InternalAdminTechStackResponse;
import java.util.List;
import java.util.UUID;

public record GetProfileResponse(
    UUID userId,
    String email,
    String nickname,
    String position,
    List<TechStackInfo> techStacks,
    String profileImageUrl,
    String bio,
    String role,
    String providerType
) {

    public record TechStackInfo(
        Long id,
        String name
    ) {}

    public static GetProfileResponse from(User user, UserProfile profile,
        List<InternalAdminTechStackResponse.TechStackInfo> techStacks
    ) {
        List<TechStackInfo> responseTechStacks = techStacks.stream()
            .map(techStack -> new TechStackInfo(techStack.id(), techStack.name()))
            .toList();

        return new GetProfileResponse(
            user.getUserId(),
            user.getEmail(),
            profile.getNickname(),
            profile.getPosition() != null ? profile.getPosition().name() : null,
            responseTechStacks,
            profile.getProfileImgUrl(),
            profile.getBio(),
            user.getRole().name(),
            user.getProviderType().name()
        );
    }
}