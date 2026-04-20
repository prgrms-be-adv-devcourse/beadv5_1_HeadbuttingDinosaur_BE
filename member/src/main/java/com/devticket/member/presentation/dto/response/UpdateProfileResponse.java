package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.UserProfile;
import com.devticket.member.presentation.domain.model.UserTechStack;
import java.util.List;

public record UpdateProfileResponse(
    String nickname,
    String position,
    String profileImageUrl,
    List<Long> techStackIds
) {

    public static UpdateProfileResponse from(UserProfile profile,
        List<UserTechStack> techStacks) {
        List<Long> stackIds = techStacks.stream()
            .map(UserTechStack::getTechStackId)
            .toList();

        return new UpdateProfileResponse(
            profile.getNickname(),
            profile.getPosition() != null ? profile.getPosition().name() : null,
            profile.getProfileImgUrl(),
            stackIds
        );
    }
}
