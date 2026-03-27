package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.TechStack;
import com.devticket.member.presentation.domain.model.User;
import com.devticket.member.presentation.domain.model.UserProfile;
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

    public record TechStackInfo(Long techStackId, String name) {

    }

    public static GetProfileResponse from(User user, UserProfile profile,
        List<TechStack> techStacks) {
        List<TechStackInfo> techStackInfos = techStacks.stream()
            .map(ts -> new TechStackInfo(ts.getId(), ts.getName()))
            .toList();

        return new GetProfileResponse(
            user.getUserId(),
            user.getEmail(),
            profile.getNickname(),
            profile.getPosition() != null ? profile.getPosition().name() : null,
            techStackInfos,
            profile.getProfileImgUrl(),
            profile.getBio(),
            user.getRole().name(),
            user.getProviderType().name()
        );
    }
}
