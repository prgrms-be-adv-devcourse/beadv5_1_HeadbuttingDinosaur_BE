package com.devticket.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.devticket.member.common.exception.BusinessException;
import com.devticket.member.presentation.domain.MemberErrorCode;
import com.devticket.member.presentation.domain.UserRole;
import com.devticket.member.presentation.domain.model.SellerApplication;
import com.devticket.member.presentation.domain.model.User;
import com.devticket.member.presentation.domain.repository.SellerApplicationRepository;
import com.devticket.member.presentation.domain.repository.UserRepository;
import com.devticket.member.presentation.dto.internal.response.InternalMemberInfoResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberRoleResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberStatusResponse;
import com.devticket.member.presentation.dto.internal.response.InternalSellerInfoResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalMemberServiceTest {

    @InjectMocks
    private InternalMemberService internalMemberService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SellerApplicationRepository sellerApplicationRepository;

    private static final UUID TEST_USER_UUID = UUID.randomUUID();

    // ========== 유저 기본 정보 조회 ==========

    @Nested
    @DisplayName("유저 기본 정보 조회")
    class GetMemberInfo {

        @Test
        void 존재하지_않는_userId로_조회시_실패() {
            // given
            UUID unknownUuid = UUID.randomUUID();
            given(userRepository.findByUserId(unknownUuid)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> internalMemberService.getMemberInfo(unknownUuid))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        }

        @Test
        void 정상_조회시_email_role_status_providerType_반환() {
            // given
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findByUserId(any(UUID.class))).willReturn(Optional.of(user));

            // when
            InternalMemberInfoResponse response = internalMemberService.getMemberInfo(TEST_USER_UUID);

            // then
            assertThat(response.email()).isEqualTo("test@test.com");
            assertThat(response.role()).isEqualTo("USER");
            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.providerType()).isEqualTo("LOCAL");
        }
    }

    // ========== 회원 상태 확인 ==========

    @Nested
    @DisplayName("회원 상태 확인")
    class GetMemberStatus {

        @Test
        void 존재하지_않는_userId로_조회시_실패() {
            // given
            UUID unknownUuid = UUID.randomUUID();
            given(userRepository.findByUserId(unknownUuid)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> internalMemberService.getMemberStatus(unknownUuid))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        void 정상_조회시_status_반환() {
            // given
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findByUserId(any(UUID.class))).willReturn(Optional.of(user));

            // when
            InternalMemberStatusResponse response = internalMemberService.getMemberStatus(TEST_USER_UUID);

            // then
            assertThat(response.userId()).isNotNull();
            assertThat(response.status()).isEqualTo("ACTIVE");
        }
    }

    // ========== 권한 확인 ==========

    @Nested
    @DisplayName("권한 확인")
    class GetMemberRole {

        @Test
        void 정상_조회시_role_반환() {
            // given
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            user.changeRole(UserRole.SELLER);
            given(userRepository.findByUserId(any(UUID.class))).willReturn(Optional.of(user));

            // when
            InternalMemberRoleResponse response = internalMemberService.getMemberRole(TEST_USER_UUID);

            // then
            assertThat(response.role()).isEqualTo("SELLER");
        }
    }

    // ========== 정산 계좌 조회 ==========

    @Nested
    @DisplayName("정산 계좌 조회")
    class GetSellerInfo {

        @Test
        void 판매자_정보_없는_사용자_조회시_실패() {
            // given
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findByUserId(any(UUID.class))).willReturn(Optional.of(user));
            given(sellerApplicationRepository.findTopByUserIdOrderByCreatedAtDesc(any())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> internalMemberService.getSellerInfo(TEST_USER_UUID))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        void 정상_조회시_계좌_정보_반환() {
            // given
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            SellerApplication application = new SellerApplication(
                user.getId(), "국민은행", "123-456-789", "홍길동");
            application.approve();
            given(userRepository.findByUserId(any(UUID.class))).willReturn(Optional.of(user));
            given(sellerApplicationRepository.findTopByUserIdOrderByCreatedAtDesc(any())).willReturn(
                Optional.of(application));

            // when
            InternalSellerInfoResponse response = internalMemberService.getSellerInfo(TEST_USER_UUID);

            // then
            assertThat(response.bankName()).isEqualTo("국민은행");
            assertThat(response.accountNumber()).isEqualTo("123-456-789");
            assertThat(response.accountHolder()).isEqualTo("홍길동");
        }
    }
}