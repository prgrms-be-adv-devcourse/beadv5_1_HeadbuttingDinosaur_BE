package com.devticket.member.application;

import com.devticket.member.common.exception.BusinessException;
import com.devticket.member.infrastructure.external.client.AdminInternalClient;
import com.devticket.member.infrastructure.external.dto.res.InternalAdminTechStackResponse;
import com.devticket.member.infrastructure.jwt.JwtTokenProvider;
import com.devticket.member.presentation.domain.MemberErrorCode;
import com.devticket.member.presentation.domain.Position;
import com.devticket.member.presentation.domain.ProviderType;
import com.devticket.member.presentation.domain.model.RefreshToken;
import com.devticket.member.presentation.domain.model.User;
import com.devticket.member.presentation.domain.model.UserProfile;
import com.devticket.member.presentation.domain.model.UserTechStack;
import com.devticket.member.presentation.domain.repository.RefreshTokenRepository;
import com.devticket.member.presentation.domain.repository.UserProfileRepository;
import com.devticket.member.presentation.domain.repository.UserRepository;
import com.devticket.member.presentation.domain.repository.UserTechStackRepository;
import com.devticket.member.presentation.dto.request.ChangePasswordRequest;
import com.devticket.member.presentation.dto.request.SignUpProfileRequest;
import com.devticket.member.presentation.dto.request.UpdateProfileRequest;
import com.devticket.member.presentation.dto.response.ChangePasswordResponse;
import com.devticket.member.presentation.dto.response.GetProfileResponse;
import com.devticket.member.presentation.dto.response.SignUpProfileResponse;
import com.devticket.member.presentation.dto.response.UpdateProfileResponse;
import com.devticket.member.presentation.dto.response.WithdrawResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserTechStackRepository userTechStackRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AdminInternalClient adminInternalClient;


    @Transactional
    public SignUpProfileResponse createProfile(UUID userId, SignUpProfileRequest request) {
        User user = findUserByUuidOrThrow(userId);
        validateNicknameNotDuplicated(request.nickname());

        UserProfile profile = new UserProfile(
            user.getId(),
            request.nickname(),
            parsePosition(request.position()),
            request.profileImageUrl(),
            request.bio()
        );
        UserProfile savedProfile = userProfileRepository.save(profile);

        saveTechStacks(user.getId(), request.techStackIds());

        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), user.getEmail(), user.getRole(), true);
        String refreshToken = saveRefreshToken(user.getId());

        log.info("프로필 생성 완료: userId={}, nickname={}", userId, request.nickname());
        return SignUpProfileResponse.from(savedProfile, accessToken, refreshToken);
    }

    public GetProfileResponse getProfile(UUID userId) {
        User user = findUserByUuidOrThrow(userId);
        UserProfile profile = findProfileByUserIdOrThrow(user.getId());

        List<UserTechStack> userTechStacks = userTechStackRepository.findByUserId(user.getId());
        List<Long> techStackIds = userTechStacks.stream()
            .map(UserTechStack::getTechStackId)
            .toList();

        InternalAdminTechStackResponse response = adminInternalClient.getTechStacks();
        List<InternalAdminTechStackResponse.TechStackInfo> techStacks = response.techStacks()
            .stream()
            .filter(ts -> techStackIds.contains(ts.id()))
            .toList();

        return GetProfileResponse.from(user, profile, techStacks);
    }

    @Transactional
    public UpdateProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUserByUuidOrThrow(userId);
        UserProfile profile = findProfileByUserIdOrThrow(user.getId());

        if (request.nickname() != null && !request.nickname().equals(profile.getNickname())) {
            validateNicknameNotDuplicated(request.nickname());
        }

        profile.update(
            request.nickname() != null ? request.nickname() : profile.getNickname(),
            request.position() != null ? parsePosition(request.position()) : profile.getPosition(),
            request.profileImageUrl() != null ? request.profileImageUrl() : profile.getProfileImgUrl(),
            request.bio() != null ? request.bio() : profile.getBio()
        );

        userTechStackRepository.deleteByUserId(user.getId());
        saveTechStacks(user.getId(), request.techStackIds());

        List<UserTechStack> updatedTechStacks = userTechStackRepository.findByUserId(user.getId());

        log.info("프로필 수정 완료: userId={}", userId);
        return UpdateProfileResponse.from(profile, updatedTechStacks);
    }

    @Transactional
    public ChangePasswordResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findUserByUuidOrThrow(userId);

        validateLocalUser(user);
        validateCurrentPassword(request.currentPassword(), user.getPassword());
        validatePasswordConfirm(request.newPassword(), request.newPasswordConfirm());

        String encodedPassword = passwordEncoder.encode(request.newPassword());
        user.changePassword(encodedPassword);

        log.info("비밀번호 변경 완료: userId={}", userId);
        return new ChangePasswordResponse(true);
    }

    @Transactional
    public WithdrawResponse withdraw(UUID userId) {
        User user = findUserByUuidOrThrow(userId);

        user.withdraw();
        refreshTokenRepository.deleteAllByUserId(user.getId());

        log.info("회원 탈퇴 완료: userId={}", userId);
        return WithdrawResponse.from(user);
    }

    // ========== 검증 ==========

    private void validateNicknameNotDuplicated(String nickname) {
        if (userProfileRepository.existsByNickname(nickname)) {
            throw new BusinessException(MemberErrorCode.NICKNAME_DUPLICATED);
        }
    }

    private void validateLocalUser(User user) {
        if (user.getProviderType() != ProviderType.LOCAL) {
            throw new BusinessException(MemberErrorCode.LOGIN_FAILED);
        }
    }

    private void validateCurrentPassword(String rawPassword, String encodedPassword) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new BusinessException(MemberErrorCode.PASSWORD_MISMATCH);
        }
    }

    private void validatePasswordConfirm(String newPassword, String newPasswordConfirm) {
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new BusinessException(MemberErrorCode.PASSWORD_LENGTH_INVALID);
        }
    }

    // ========== 조회 ==========

    private User findUserByUuidOrThrow(UUID userId) {
        return userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    private UserProfile findProfileByUserIdOrThrow(Long userId) {
        return userProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    public InternalAdminTechStackResponse getAllTechStacks() {
        return adminInternalClient.getTechStacks();
    }

    // ========== 토큰 ==========

    private String saveRefreshToken(Long userId) {
        String token = jwtTokenProvider.createRefreshToken();
        long ttl = jwtTokenProvider.getRefreshTokenTtl();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ttl / 1000);
        refreshTokenRepository.save(new RefreshToken(userId, token, expiresAt));
        return token;
    }

    // ========== 유틸 ==========

    private Position parsePosition(String position) {
        return Position.valueOf(position.toUpperCase());
    }

    private void saveTechStacks(Long userId, List<Long> techStackIds) {
        if (techStackIds == null || techStackIds.isEmpty()) {
            return;
        }
        techStackIds.forEach(techStackId ->
            userTechStackRepository.save(new UserTechStack(userId, techStackId))
        );
    }
}