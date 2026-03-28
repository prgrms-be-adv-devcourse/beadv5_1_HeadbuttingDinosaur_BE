package com.devticket.member.application;

import com.devticket.member.common.exception.BusinessException;
import com.devticket.member.presentation.domain.MemberErrorCode;
import com.devticket.member.presentation.domain.model.SellerApplication;
import com.devticket.member.presentation.domain.model.User;
import com.devticket.member.presentation.domain.repository.SellerApplicationRepository;
import com.devticket.member.presentation.domain.repository.UserRepository;
import com.devticket.member.presentation.dto.internal.response.InternalMemberInfoResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberRoleResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberStatusResponse;
import com.devticket.member.presentation.dto.internal.response.InternalSellerInfoResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalMemberService {

    private final UserRepository userRepository;
    private final SellerApplicationRepository sellerApplicationRepository;

    public InternalMemberInfoResponse getMemberInfo(UUID userId) {
        User user = findUserByUuidOrThrow(userId);
        return InternalMemberInfoResponse.from(user);
    }

    public InternalMemberStatusResponse getMemberStatus(UUID userId) {
        User user = findUserByUuidOrThrow(userId);
        return InternalMemberStatusResponse.from(user);
    }

    public InternalMemberRoleResponse getMemberRole(UUID userId) {
        User user = findUserByUuidOrThrow(userId);
        return InternalMemberRoleResponse.from(user);
    }

    public InternalSellerInfoResponse getSellerInfo(UUID userId) {
        User user = findUserByUuidOrThrow(userId);
        SellerApplication application = sellerApplicationRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        return InternalSellerInfoResponse.from(user, application);
    }

    private User findUserByUuidOrThrow(UUID userId) {
        return userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}