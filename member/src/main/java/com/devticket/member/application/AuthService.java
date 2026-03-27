package com.devticket.member.application;


import com.devticket.member.common.exception.BusinessException;
import com.devticket.member.infrastructure.jwt.JwtTokenProvider;
import com.devticket.member.infrastructure.oauth.OAuthUserInfoVerifierRouter;
import com.devticket.member.infrastructure.oauth.dto.OAuthUserInfo;
import com.devticket.member.presentation.domain.MemberErrorCode;
import com.devticket.member.presentation.domain.ProviderType;
import com.devticket.member.presentation.domain.UserStatus;
import com.devticket.member.presentation.domain.model.RefreshToken;
import com.devticket.member.presentation.domain.model.User;
import com.devticket.member.presentation.domain.repository.RefreshTokenRepository;
import com.devticket.member.presentation.domain.repository.UserProfileRepository;
import com.devticket.member.presentation.domain.repository.UserRepository;
import com.devticket.member.presentation.dto.request.LoginRequest;
import com.devticket.member.presentation.dto.request.SignUpRequest;
import com.devticket.member.presentation.dto.request.SocialSignUpOrLoginRequest;
import com.devticket.member.presentation.dto.request.TokenRefreshRequest;
import com.devticket.member.presentation.dto.response.LoginResponse;
import com.devticket.member.presentation.dto.response.LogoutResponse;
import com.devticket.member.presentation.dto.response.SignUpResponse;
import com.devticket.member.presentation.dto.response.SocialSignUpOrLoginResponse;
import com.devticket.member.presentation.dto.response.TokenRefreshResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final OAuthUserInfoVerifierRouter oAuthVerifierRouter;

    @Transactional
    public SignUpResponse signup(SignUpRequest request) {
        validatePasswordConfirm(request.password(), request.passwordConfirm());
        validateEmailNotDuplicated(request.email());

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = new User(request.email(), encodedPassword);
        User savedUser = userRepository.save(user);

        String accessToken = issueAccessToken(savedUser, false);
        String refreshToken = saveRefreshToken(savedUser.getId());

        log.info("회원가입 완료: email={}", savedUser.getEmail());
        return SignUpResponse.from(savedUser, accessToken, refreshToken);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = findUserByEmailOrThrow(request.email());
        validateUserStatus(user);
        validatePassword(request.password(), user.getPassword());

        boolean profileCompleted = isProfileCompleted(user.getId());
        String accessToken = issueAccessToken(user, profileCompleted);
        String refreshToken = saveRefreshToken(user.getId());

        log.info("로그인 완료: email={}, profileCompleted={}", user.getEmail(), profileCompleted);
        return LoginResponse.from(user, accessToken, refreshToken, profileCompleted);
    }

    @Transactional
    public SocialSignUpOrLoginResponse socialLogin(SocialSignUpOrLoginRequest request) {
        ProviderType providerType = parseProviderType(request.providerType());
        OAuthUserInfo oAuthUserInfo = oAuthVerifierRouter.verify(providerType, request.idToken());

        Optional<User> existingUser = userRepository.findByEmail(oAuthUserInfo.email());

        return existingUser.map(this::handleExistingSocialUser)
            .orElseGet(() -> handleNewSocialUser(oAuthUserInfo, providerType));

    }

    @Transactional
    public TokenRefreshResponse reissue(TokenRefreshRequest request) {
        RefreshToken refreshToken = findRefreshTokenOrThrow(request.refreshToken());
        validateRefreshTokenExpiry(refreshToken);

        User user = findUserByIdOrThrow(refreshToken.getUserId());

        refreshTokenRepository.deleteByToken(request.refreshToken());

        boolean profileCompleted = isProfileCompleted(user.getId());
        String newAccessToken = issueAccessToken(user, profileCompleted);
        String newRefreshToken = saveRefreshToken(user.getId());

        log.info("토큰 재발급 완료: userId={}", user.getId());
        return new TokenRefreshResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public LogoutResponse logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
        log.info("로그아웃 완료");
        return new LogoutResponse(true);
    }

    // ========== 소셜 로그인 분기 ==========

    private SocialSignUpOrLoginResponse handleExistingSocialUser(User user) {
        if (user.getProviderType() == ProviderType.LOCAL) {
            throw new BusinessException(MemberErrorCode.SOCIAL_EMAIL_CONFLICT);
        }

        boolean profileCompleted = isProfileCompleted(user.getId());
        String accessToken = issueAccessToken(user, profileCompleted);
        String refreshToken = saveRefreshToken(user.getId());

        log.info("소셜 로그인 완료: email={}", user.getEmail());
        return SocialSignUpOrLoginResponse.from(
            user, accessToken, refreshToken, false, profileCompleted);
    }

    private SocialSignUpOrLoginResponse handleNewSocialUser(OAuthUserInfo oAuthUserInfo,
        ProviderType providerType) {
        User newUser = new User(oAuthUserInfo.email(), providerType, oAuthUserInfo.providerId());
        User savedUser = userRepository.save(newUser);

        boolean profileCompleted = isProfileCompleted(savedUser.getId());
        String accessToken = issueAccessToken(savedUser, profileCompleted);
        String refreshToken = saveRefreshToken(savedUser.getId());

        log.info("소셜 회원가입 완료: email={}", savedUser.getEmail());
        return SocialSignUpOrLoginResponse.from(
            savedUser, accessToken, refreshToken, true, profileCompleted);
    }

    // ========== 토큰 발급 ==========

    private String issueAccessToken(User user, boolean profileCompleted) {
        return jwtTokenProvider.createAccessToken(
            user.getId(), user.getEmail(), user.getRole(), profileCompleted);
    }

    private String saveRefreshToken(Long userId) {
        String token = jwtTokenProvider.createRefreshToken();
        long ttl = jwtTokenProvider.getRefreshTokenTtl();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ttl / 1000);

        refreshTokenRepository.save(new RefreshToken(userId, token, expiresAt));
        return token;
    }

    // ========== 검증 ==========

    private ProviderType parseProviderType(String providerType) {
        try {
            return ProviderType.valueOf(providerType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(MemberErrorCode.LOGIN_FAILED);
        }
    }

    private void validateEmailNotDuplicated(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
        }
    }

    private void validatePasswordConfirm(String password, String passwordConfirm) {
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(MemberErrorCode.PASSWORD_LENGTH_INVALID);
        }
    }

    private void validateUserStatus(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(MemberErrorCode.ACCOUNT_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(MemberErrorCode.ACCOUNT_WITHDRAWN);
        }
    }

    private void validatePassword(String rawPassword, String encodedPassword) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new BusinessException(MemberErrorCode.LOGIN_FAILED);
        }
    }

    private void validateRefreshTokenExpiry(RefreshToken refreshToken) {
        if (refreshToken.isExpired()) {
            throw new BusinessException(MemberErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    // ========== 조회 ==========

    private User findUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.LOGIN_FAILED));
    }

    private User findUserByIdOrThrow(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    private RefreshToken findRefreshTokenOrThrow(String token) {
        return refreshTokenRepository.findByToken(token)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.REFRESH_TOKEN_INVALID));
    }

    private boolean isProfileCompleted(Long userId) {
        return userProfileRepository.findByUserId(userId).isPresent();
    }
}