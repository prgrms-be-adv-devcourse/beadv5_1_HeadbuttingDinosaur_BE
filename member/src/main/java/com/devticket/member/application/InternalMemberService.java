package com.devticket.member.application;

import com.devticket.member.common.exception.BusinessException;
import com.devticket.member.presentation.domain.MemberErrorCode;
import com.devticket.member.presentation.domain.SellerApplicationDecision;
import com.devticket.member.presentation.domain.UserRole;
import com.devticket.member.presentation.domain.UserStatus;
import com.devticket.member.presentation.domain.model.SellerApplication;
import com.devticket.member.presentation.domain.model.TechStack;
import com.devticket.member.presentation.domain.model.User;
import com.devticket.member.presentation.domain.model.UserProfile;
import com.devticket.member.presentation.domain.repository.SellerApplicationRepository;
import com.devticket.member.presentation.domain.repository.TechStackRepository;
import com.devticket.member.presentation.domain.repository.UserProfileRepository;
import com.devticket.member.presentation.domain.repository.UserRepository;
import com.devticket.member.presentation.dto.internal.request.InternalDecideSellerApplicationRequest;
import com.devticket.member.presentation.dto.internal.request.InternalUpdateUserRoleRequest;
import com.devticket.member.presentation.dto.internal.request.InternalUpdateUserStatusRequest;
import com.devticket.member.presentation.dto.internal.response.InternalDecideSellerApplicationResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberInfoResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberRoleResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberStatusResponse;
import com.devticket.member.presentation.dto.internal.response.InternalPagedMemberResponse;
import com.devticket.member.presentation.dto.internal.response.InternalSellerApplicationResponse;
import com.devticket.member.presentation.dto.internal.response.InternalSellerInfoResponse;
import com.devticket.member.presentation.dto.internal.response.InternalTechStackListResponse;
import com.devticket.member.presentation.dto.internal.response.InternalUpdateRoleResponse;
import com.devticket.member.presentation.dto.internal.response.InternalUpdateStatusResponse;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalMemberService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final SellerApplicationRepository sellerApplicationRepository;
    private final TechStackRepository techStackRepository;

    public InternalMemberInfoResponse getMemberInfo(UUID userId) {
        User user = findUserByUuidOrThrow(userId);
        String nickname = userProfileRepository.findByUserId(user.getId())
            .map(UserProfile::getNickname)
            .orElse(null);
        return InternalMemberInfoResponse.from(user, nickname);
    }

    // 관리자 회원 목록 조회 (paged)
    public InternalPagedMemberResponse searchMembers(
        UserRole role, UserStatus status, String keyword, Pageable pageable
    ) {
        Page<InternalMemberInfoResponse> page = userRepository
            .searchMembersWithNickname(role, status, keyword, pageable)
            .map(row -> InternalMemberInfoResponse.from((User) row[0], (String) row[1]));
        return InternalPagedMemberResponse.from(page);
    }

    // 관리자 회원 상태 변경
    @Transactional
    public InternalUpdateStatusResponse updateMemberStatus(
        UUID userId, InternalUpdateUserStatusRequest request
    ) {
        User user = findUserByUuidOrThrow(userId);
        switch (request.status()) {
            case ACTIVE -> user.reactivate();
            case SUSPENDED -> user.suspend();
            case WITHDRAWN -> user.withdraw();
        }
        return InternalUpdateStatusResponse.from(user);
    }

    // 관리자 회원 권한 변경
    @Transactional
    public InternalUpdateRoleResponse updateMemberRole(
        UUID userId, InternalUpdateUserRoleRequest request
    ) {
        User user = findUserByUuidOrThrow(userId);
        user.changeRole(request.role());
        return InternalUpdateRoleResponse.from(user);
    }

    public List<InternalMemberInfoResponse> getMemberInfoBatch(List<UUID> userIds) {
        return userIds.stream()
            .distinct()
            .map(userId -> userRepository.findByUserId(userId)
                .map(user -> {
                    String nickname = userProfileRepository.findByUserId(user.getId())
                        .map(UserProfile::getNickname)
                        .orElse("");
                    return InternalMemberInfoResponse.from(user, nickname);
                })
                .orElse(null))
            .filter(Objects::nonNull)
            .toList();
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
        SellerApplication application = sellerApplicationRepository.findTopByUserIdOrderByCreatedAtDesc(user.getUserId())
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        return InternalSellerInfoResponse.from(user, application);
    }

    private User findUserByUuidOrThrow(UUID userId) {
        return userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    // 셀러 변환 요청 리스트 전체 조회
    public List<InternalSellerApplicationResponse>  getSellerApplications() {
        return sellerApplicationRepository.findAll().stream()
            .map(InternalSellerApplicationResponse::from)
            .toList();
    }

    // 판매자 승인 결정
    @Transactional
    public InternalDecideSellerApplicationResponse decideSellerApplication(UUID applicationId, InternalDecideSellerApplicationRequest request) {
        SellerApplication application = sellerApplicationRepository.findBySellerApplicationId(applicationId)
            .orElseThrow(()-> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        // 판매자 승인 시
        if(request.decision() == SellerApplicationDecision.APPROVED){
            application.approve();
            User user = userRepository.findByUserId(application.getUserId())
                .orElseThrow(()-> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
            user.changeRole(UserRole.SELLER);
        }
        // 판매자 미승인 시
        else if(request.decision().equals(SellerApplicationDecision.REJECTED)){
            application.reject();
        }

        return InternalDecideSellerApplicationResponse.from(application);
    }

    // seller 아이디 전체 반환
    public List<UUID> getSellerIds() {
        return userRepository.findByRole(UserRole.SELLER).stream()
            .map(User::getUserId)
            .toList();
    }

    public InternalTechStackListResponse getAllTechStacks() {
        List<TechStack> stacks = techStackRepository.findAll();
        return InternalTechStackListResponse.from(stacks);
    }
}
