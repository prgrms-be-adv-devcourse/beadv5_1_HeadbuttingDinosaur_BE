package com.devticket.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devticket.member.common.exception.BusinessException;
import com.devticket.member.presentation.domain.MemberErrorCode;
import com.devticket.member.presentation.domain.Position;
import com.devticket.member.presentation.domain.ProviderType;
import com.devticket.member.presentation.domain.model.User;
import com.devticket.member.presentation.domain.model.UserProfile;
import com.devticket.member.presentation.domain.model.UserTechStack;
import com.devticket.member.presentation.domain.repository.RefreshTokenRepository;
import com.devticket.member.presentation.domain.repository.TechStackRepository;
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
import java.util.List;
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
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserTechStackRepository userTechStackRepository;

    @Mock
    private TechStackRepository techStackRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    // ========== 프로필 생성 ==========

    @Nested
    @DisplayName("프로필 생성")
    class CreateProfile {

        @Test
        void 존재하지_않는_userId로_프로필_생성시_실패() {
            // given
            SignUpProfileRequest request = new SignUpProfileRequest(
                "닉네임", "BACKEND", List.of(), null, null);
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.createProfile(999L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));

            verify(userProfileRepository, never()).save(any(UserProfile.class));
        }

        @Test
        void 닉네임_중복시_프로필_생성_실패() {
            // given
            SignUpProfileRequest request = new SignUpProfileRequest(
                "중복닉네임", "BACKEND", List.of(), null, null);
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findById(anyLong())).willReturn(Optional.of(user));
            given(userProfileRepository.existsByNickname("중복닉네임")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.createProfile(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.NICKNAME_DUPLICATED));

            verify(userProfileRepository, never()).save(any(UserProfile.class));
        }

        @Test
        void 정상_프로필_생성시_UserProfile과_UserTechStack이_생성된다() {
            // given
            SignUpProfileRequest request = new SignUpProfileRequest(
                "새닉네임", "BACKEND", List.of(), null, "자기소개");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findById(anyLong())).willReturn(Optional.of(user));
            given(userProfileRepository.existsByNickname("새닉네임")).willReturn(false);
            given(userProfileRepository.save(any(UserProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            SignUpProfileResponse response = userService.createProfile(1L, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.profileId()).isNotNull();
            verify(userProfileRepository).save(any(UserProfile.class));
        }
    }

    // ========== 프로필 조회 ==========

    @Nested
    @DisplayName("프로필 조회")
    class GetProfile {

        @Test
        void 존재하지_않는_userId로_프로필_조회시_실패() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.getProfile(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        }

        @Test
        void 정상_프로필_조회시_User_UserProfile_TechStack_정보가_반환된다() {
            // given
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            UserProfile profile = new UserProfile(1L, "닉네임", Position.BACKEND, null, "자기소개");
            List<UserTechStack> userTechStacks = List.of(new UserTechStack(1L, 10L));

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userProfileRepository.findByUserId(anyLong())).willReturn(Optional.of(profile));
            given(userTechStackRepository.findByUserId(anyLong())).willReturn(userTechStacks);
            given(techStackRepository.findAllById(anyList())).willReturn(List.of());

            // when
            GetProfileResponse response = userService.getProfile(1L);

            // then
            assertThat(response).isNotNull();
            assertThat(response.email()).isEqualTo("test@test.com");
            assertThat(response.nickname()).isEqualTo("닉네임");
            assertThat(response.position()).isEqualTo("BACKEND");
        }
    }

    // ========== 프로필 수정 ==========

    @Nested
    @DisplayName("프로필 수정")
    class UpdateProfile {

        @Test
        void 닉네임_변경시_중복이면_실패() {
            // given
            UpdateProfileRequest request = new UpdateProfileRequest(
                "중복닉네임", "BACKEND", null, List.of(), null);
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            UserProfile profile = new UserProfile(1L, "기존닉네임", Position.BACKEND, null, null);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userProfileRepository.findByUserId(anyLong())).willReturn(Optional.of(profile));
            given(userProfileRepository.existsByNickname("중복닉네임")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.updateProfile(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.NICKNAME_DUPLICATED));
        }

        @Test
        void 기존_닉네임과_동일하면_중복_검증을_스킵한다() {
            // given
            UpdateProfileRequest request = new UpdateProfileRequest(
                "기존닉네임", "FRONTEND", null, List.of(), "수정된 자기소개");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            UserProfile profile = new UserProfile(1L, "기존닉네임", Position.BACKEND, null, null);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userProfileRepository.findByUserId(anyLong())).willReturn(Optional.of(profile));
            given(userTechStackRepository.findByUserId(anyLong())).willReturn(List.of());

            // when
            UpdateProfileResponse response = userService.updateProfile(1L, request);

            // then
            verify(userProfileRepository, never()).existsByNickname(anyString());
            assertThat(response).isNotNull();
        }

        @Test
        void 기술_스택_수정시_기존_삭제_후_새로_삽입된다() {
            // given
            UpdateProfileRequest request = new UpdateProfileRequest(
                "기존닉네임", "BACKEND", null, List.of(), null);
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            UserProfile profile = new UserProfile(1L, "기존닉네임", Position.BACKEND, null, null);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userProfileRepository.findByUserId(anyLong())).willReturn(Optional.of(profile));
            given(userTechStackRepository.findByUserId(anyLong())).willReturn(List.of());

            // when
            userService.updateProfile(1L, request);

            // then
            verify(userTechStackRepository).deleteByUserId(1L);
        }
    }

    // ========== 비밀번호 변경 ==========

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePassword {

        @Test
        void 현재_비밀번호_불일치시_실패() {
            // given
            ChangePasswordRequest request = new ChangePasswordRequest(
                "wrongPassword!", "newPassword123!", "newPassword123!");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongPassword!", "$2a$10$hashedPassword")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.changePassword(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.PASSWORD_MISMATCH));
        }

        @Test
        void 소셜_사용자가_비밀번호_변경시_실패() {
            // given
            ChangePasswordRequest request = new ChangePasswordRequest(
                "anyPassword!", "newPassword123!", "newPassword123!");
            User socialUser = new User("google@test.com", ProviderType.GOOGLE, "google-id");
            given(userRepository.findById(1L)).willReturn(Optional.of(socialUser));

            // when & then
            assertThatThrownBy(() -> userService.changePassword(1L, request))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        void 새_비밀번호와_확인_불일치시_실패() {
            // given
            ChangePasswordRequest request = new ChangePasswordRequest(
                "currentPassword!", "newPassword123!", "different123!");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("currentPassword!", "$2a$10$hashedPassword")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.changePassword(1L, request))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        void 정상_비밀번호_변경시_BCrypt_해시_저장() {
            // given
            ChangePasswordRequest request = new ChangePasswordRequest(
                "currentPassword!", "newPassword123!", "newPassword123!");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("currentPassword!", "$2a$10$hashedPassword")).willReturn(true);
            given(passwordEncoder.encode("newPassword123!")).willReturn("$2a$10$newHashedPassword");

            // when
            ChangePasswordResponse response = userService.changePassword(1L, request);

            // then
            assertThat(response.success()).isTrue();
            verify(passwordEncoder).encode("newPassword123!");
        }
    }

    // ========== 회원 탈퇴 ==========

    @Nested
    @DisplayName("회원 탈퇴")
    class Withdraw {

        @Test
        void 정상_탈퇴시_상태_WITHDRAWN_및_withdrawnAt_기록() {
            // given
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            // when
            WithdrawResponse response = userService.withdraw(1L);

            // then
            assertThat(response.status()).isEqualTo("WITHDRAWN");
            assertThat(response.withdrawnAt()).isNotNull();
        }

        @Test
        void 정상_탈퇴시_RefreshToken_전체_삭제() {
            // given
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            // when
            userService.withdraw(1L);

            // then
            verify(refreshTokenRepository).deleteAllByUserId(1L);
        }
    }
}
