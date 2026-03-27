package com.devticket.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devticket.member.common.exception.BusinessException;
import com.devticket.member.infrastructure.jwt.JwtTokenProvider;
import com.devticket.member.infrastructure.oauth.GoogleTokenVerifier;
import com.devticket.member.infrastructure.oauth.dto.GoogleUserInfo;
import com.devticket.member.presentation.domain.MemberErrorCode;
import com.devticket.member.presentation.domain.Position;
import com.devticket.member.presentation.domain.ProviderType;
import com.devticket.member.presentation.domain.UserRole;
import com.devticket.member.presentation.domain.model.RefreshToken;
import com.devticket.member.presentation.domain.model.User;
import com.devticket.member.presentation.domain.model.UserProfile;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GoogleTokenVerifier googleTokenVerifier;

    private void stubJwtCreation() {
        given(jwtTokenProvider.createAccessToken(any(), anyString(), any(UserRole.class), anyBoolean()))
            .willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken()).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenTtl()).willReturn(604800000L);
    }

    // ========== 회원가입 ==========

    @Nested
    @DisplayName("회원가입")
    class Signup {

        @Test
        void 이메일_중복시_회원가입_실패() {
            // given
            SignUpRequest request = new SignUpRequest("existing@test.com", "password123!", "password123!");
            given(userRepository.existsByEmail("existing@test.com")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.EMAIL_DUPLICATED));

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void 비밀번호와_비밀번호_확인_불일치시_회원가입_실패() {
            // given
            SignUpRequest request = new SignUpRequest("test@test.com", "password123!", "different123!");
            given(userRepository.existsByEmail("test@test.com")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class);

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void 정상_가입시_비밀번호가_BCrypt로_해시된다() {
            // given
            SignUpRequest request = new SignUpRequest("test@test.com", "password123!", "password123!");
            given(userRepository.existsByEmail("test@test.com")).willReturn(false);
            given(passwordEncoder.encode("password123!")).willReturn("$2a$10$hashedPassword");
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
            stubJwtCreation();

            // when
            authService.signup(request);

            // then
            verify(passwordEncoder).encode("password123!");
            verify(userRepository).save(any(User.class));
        }

        @Test
        void 정상_가입시_RefreshToken이_DB에_저장된다() {
            // given
            SignUpRequest request = new SignUpRequest("test@test.com", "password123!", "password123!");
            given(userRepository.existsByEmail("test@test.com")).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("$2a$10$hashedPassword");
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
            stubJwtCreation();

            // when
            authService.signup(request);

            // then
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        void 정상_가입시_응답에_userId가_포함된다() {
            // given
            SignUpRequest request = new SignUpRequest("test@test.com", "password123!", "password123!");
            given(userRepository.existsByEmail("test@test.com")).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("$2a$10$hashedPassword");
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
            stubJwtCreation();

            // when
            SignUpResponse response = authService.signup(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.userId()).isNotNull();
        }
    }

    // ========== 로그인 ==========

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        void 존재하지_않는_이메일로_로그인시_실패() {
            // given
            LoginRequest request = new LoginRequest("unknown@test.com", "password123!");
            given(userRepository.findByEmail("unknown@test.com")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.LOGIN_FAILED));
        }

        @Test
        void 비밀번호_불일치시_로그인_실패() {
            // given
            LoginRequest request = new LoginRequest("test@test.com", "wrongPassword!");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongPassword!", "$2a$10$hashedPassword")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.LOGIN_FAILED));
        }

        @Test
        void 정지된_계정으로_로그인시_실패() {
            // given
            LoginRequest request = new LoginRequest("test@test.com", "password123!");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            user.suspend();
            given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.ACCOUNT_SUSPENDED));
        }

        @Test
        void 탈퇴한_계정으로_로그인시_실패() {
            // given
            LoginRequest request = new LoginRequest("test@test.com", "password123!");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            user.withdraw();
            given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.ACCOUNT_WITHDRAWN));
        }

        @Test
        void 프로필_미완성_사용자_로그인시_isProfileCompleted가_false이다() {
            // given
            LoginRequest request = new LoginRequest("test@test.com", "password123!");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("password123!", "$2a$10$hashedPassword")).willReturn(true);
            given(userProfileRepository.findByUserId(any())).willReturn(Optional.empty());
            stubJwtCreation();

            // when
            LoginResponse response = authService.login(request);

            // then
            assertThat(response.isProfileCompleted()).isFalse();
        }

        @Test
        void 프로필_완성_사용자_로그인시_isProfileCompleted가_true이다() {
            // given
            LoginRequest request = new LoginRequest("test@test.com", "password123!");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            UserProfile profile = new UserProfile(user.getId(), "닉네임", Position.BACKEND, null, null);
            given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("password123!", "$2a$10$hashedPassword")).willReturn(true);
            given(userProfileRepository.findByUserId(any())).willReturn(Optional.of(profile));
            stubJwtCreation();

            // when
            LoginResponse response = authService.login(request);

            // then
            assertThat(response.isProfileCompleted()).isTrue();
        }

        @Test
        void 정상_로그인시_JWT_발급_및_RefreshToken_저장() {
            // given
            LoginRequest request = new LoginRequest("test@test.com", "password123!");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("password123!", "$2a$10$hashedPassword")).willReturn(true);
            given(userProfileRepository.findByUserId(any())).willReturn(Optional.empty());
            stubJwtCreation();

            // when
            LoginResponse response = authService.login(request);

            // then
            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }
    }

    // ========== 소셜 로그인 ==========

    @Nested
    @DisplayName("소셜 로그인")
    class SocialLogin {

        private final GoogleUserInfo googleUserInfo = new GoogleUserInfo(
            "google@test.com", "구글유저", "google-provider-id-123"
        );

        @Test
        void 동일_이메일_LOCAL_계정_존재시_가입_거절() {
            // given
            SocialSignUpOrLoginRequest request = new SocialSignUpOrLoginRequest("GOOGLE", "google-id-token");
            User localUser = new User("google@test.com", "$2a$10$hashedPassword");
            given(googleTokenVerifier.verify("google-id-token")).willReturn(googleUserInfo);
            given(userRepository.findByEmail("google@test.com")).willReturn(Optional.of(localUser));

            // when & then
            assertThatThrownBy(() -> authService.socialLogin(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.SOCIAL_EMAIL_CONFLICT));

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void 신규_사용자_소셜_가입시_isNewUser_true_및_isProfileCompleted_false() {
            // given
            SocialSignUpOrLoginRequest request = new SocialSignUpOrLoginRequest("GOOGLE", "google-id-token");
            given(googleTokenVerifier.verify("google-id-token")).willReturn(googleUserInfo);
            given(userRepository.findByEmail("google@test.com")).willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
            given(userProfileRepository.findByUserId(any())).willReturn(Optional.empty());
            stubJwtCreation();

            // when
            SocialSignUpOrLoginResponse response = authService.socialLogin(request);

            // then
            assertThat(response.isNewUser()).isTrue();
            assertThat(response.isProfileCompleted()).isFalse();
            verify(userRepository).save(any(User.class));
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        void 기존_GOOGLE_사용자_프로필_미완성시_isProfileCompleted_false() {
            // given
            SocialSignUpOrLoginRequest request = new SocialSignUpOrLoginRequest("GOOGLE", "google-id-token");
            User googleUser = new User("google@test.com", ProviderType.GOOGLE, "google-provider-id-123");
            given(googleTokenVerifier.verify("google-id-token")).willReturn(googleUserInfo);
            given(userRepository.findByEmail("google@test.com")).willReturn(Optional.of(googleUser));
            given(userProfileRepository.findByUserId(any())).willReturn(Optional.empty());
            stubJwtCreation();

            // when
            SocialSignUpOrLoginResponse response = authService.socialLogin(request);

            // then
            assertThat(response.isNewUser()).isFalse();
            assertThat(response.isProfileCompleted()).isFalse();
        }

        @Test
        void 기존_GOOGLE_사용자_프로필_완성시_isProfileCompleted_true() {
            // given
            SocialSignUpOrLoginRequest request = new SocialSignUpOrLoginRequest("GOOGLE", "google-id-token");
            User googleUser = new User("google@test.com", ProviderType.GOOGLE, "google-provider-id-123");
            UserProfile profile = new UserProfile(googleUser.getId(), "구글유저", Position.BACKEND, null, null);
            given(googleTokenVerifier.verify("google-id-token")).willReturn(googleUserInfo);
            given(userRepository.findByEmail("google@test.com")).willReturn(Optional.of(googleUser));
            given(userProfileRepository.findByUserId(any())).willReturn(Optional.of(profile));
            stubJwtCreation();

            // when
            SocialSignUpOrLoginResponse response = authService.socialLogin(request);

            // then
            assertThat(response.isNewUser()).isFalse();
            assertThat(response.isProfileCompleted()).isTrue();
        }
    }

    // ========== 토큰 재발급 ==========

    @Nested
    @DisplayName("토큰 재발급")
    class Reissue {

        @Test
        void 존재하지_않는_RefreshToken으로_재발급시_실패() {
            // given
            TokenRefreshRequest request = new TokenRefreshRequest("invalid-refresh-token");
            given(refreshTokenRepository.findByToken("invalid-refresh-token")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.REFRESH_TOKEN_INVALID));
        }

        @Test
        void 만료된_RefreshToken으로_재발급시_실패() {
            // given
            TokenRefreshRequest request = new TokenRefreshRequest("expired-refresh-token");
            RefreshToken expiredToken = new RefreshToken(1L, "expired-refresh-token",
                LocalDateTime.now().minusDays(1));
            given(refreshTokenRepository.findByToken("expired-refresh-token"))
                .willReturn(Optional.of(expiredToken));

            // when & then
            assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.REFRESH_TOKEN_INVALID));
        }

        @Test
        void 정상_재발급시_기존_토큰_삭제_후_새_토큰_발급() {
            // given
            TokenRefreshRequest request = new TokenRefreshRequest("valid-refresh-token");
            RefreshToken validToken = new RefreshToken(1L, "valid-refresh-token",
                LocalDateTime.now().plusDays(7));
            User user = new User("test@test.com", "$2a$10$hashedPassword");

            given(refreshTokenRepository.findByToken("valid-refresh-token"))
                .willReturn(Optional.of(validToken));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userProfileRepository.findByUserId(any())).willReturn(Optional.empty());
            stubJwtCreation();

            // when
            TokenRefreshResponse response = authService.reissue(request);

            // then
            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
            verify(refreshTokenRepository).deleteByToken("valid-refresh-token");
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }
    }

    // ========== 로그아웃 ==========

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        void 정상_로그아웃시_RefreshToken_삭제() {
            // given
            String refreshToken = "refresh-token-to-delete";

            // when
            LogoutResponse response = authService.logout(refreshToken);

            // then
            assertThat(response.success()).isTrue();
            verify(refreshTokenRepository).deleteByToken(refreshToken);
        }
    }
}