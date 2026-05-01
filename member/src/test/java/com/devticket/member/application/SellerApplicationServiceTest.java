package com.devticket.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devticket.member.common.exception.BusinessException;
import com.devticket.member.presentation.domain.MemberErrorCode;
import com.devticket.member.presentation.domain.SellerApplicationStatus;
import com.devticket.member.presentation.domain.UserRole;
import com.devticket.member.presentation.domain.model.SellerApplication;
import com.devticket.member.presentation.domain.model.User;
import com.devticket.member.presentation.domain.repository.SellerApplicationRepository;
import com.devticket.member.presentation.domain.repository.UserRepository;
import com.devticket.member.presentation.dto.request.SellerApplicationRequest;
import com.devticket.member.presentation.dto.response.SellerApplicationResponse;
import com.devticket.member.presentation.dto.response.SellerApplicationStatusResponse;
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
class SellerApplicationServiceTest {

    @InjectMocks
    private SellerApplicationService sellerApplicationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SellerApplicationRepository sellerApplicationRepository;

    private static final UUID TEST_USER_UUID = UUID.randomUUID();

    // ========== 판매자 전환 신청 ==========

    @Nested
    @DisplayName("판매자 전환 신청")
    class Apply {

        @Test
        void 이미_SELLER인_사용자_신청시_실패() {
            // given
            SellerApplicationRequest request = new SellerApplicationRequest(
                "국민은행", "123-456-789", "홍길동");
            User sellerUser = new User("seller@test.com", "$2a$10$hashedPassword");
            sellerUser.changeRole(UserRole.SELLER);
            given(userRepository.findByUserId(any(UUID.class))).willReturn(Optional.of(sellerUser));

            // when & then
            assertThatThrownBy(() -> sellerApplicationService.apply(TEST_USER_UUID, request))
                .isInstanceOf(BusinessException.class);

            verify(sellerApplicationRepository, never()).save(any(SellerApplication.class));
        }

        @Test
        void PENDING_상태_중복_신청시_실패() {
            // given
            SellerApplicationRequest request = new SellerApplicationRequest(
                "국민은행", "123-456-789", "홍길동");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findByUserId(any(UUID.class))).willReturn(Optional.of(user));
            given(sellerApplicationRepository.existsByUserIdAndStatus(any(), any(SellerApplicationStatus.class)))
                .willReturn(true);

            // when & then
            assertThatThrownBy(() -> sellerApplicationService.apply(TEST_USER_UUID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.SELLER_APPLICATION_DUPLICATED));

            verify(sellerApplicationRepository, never()).save(any(SellerApplication.class));
        }

        @Test
        void 정상_신청시_SellerApplication_생성_확인() {
            // given
            SellerApplicationRequest request = new SellerApplicationRequest(
                "국민은행", "123-456-789", "홍길동");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findByUserId(any(UUID.class))).willReturn(Optional.of(user));
            given(sellerApplicationRepository.existsByUserIdAndStatus(any(), any(SellerApplicationStatus.class)))
                .willReturn(false);
            given(sellerApplicationRepository.save(any(SellerApplication.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            SellerApplicationResponse response = sellerApplicationService.apply(TEST_USER_UUID, request);

            // then
            verify(sellerApplicationRepository).save(any(SellerApplication.class));
        }

        @Test
        void 정상_신청시_응답에_applicationId가_포함된다() {
            // given
            SellerApplicationRequest request = new SellerApplicationRequest(
                "국민은행", "123-456-789", "홍길동");
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findByUserId(any(UUID.class))).willReturn(Optional.of(user));
            given(sellerApplicationRepository.existsByUserIdAndStatus(any(), any(SellerApplicationStatus.class)))
                .willReturn(false);
            given(sellerApplicationRepository.save(any(SellerApplication.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            SellerApplicationResponse response = sellerApplicationService.apply(TEST_USER_UUID, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.applicationId()).isNotNull();
        }
    }

    // ========== 신청 상태 조회 ==========

    @Nested
    @DisplayName("신청 상태 조회")
    class GetMyApplication {

        @Test
        void 신청_내역_없는_사용자_조회시_실패() {
            // given
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            given(userRepository.findByUserId(any(UUID.class))).willReturn(Optional.of(user));
            given(sellerApplicationRepository.findTopByUserIdOrderByCreatedAtDesc(any())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sellerApplicationService.getMyApplication(TEST_USER_UUID))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        void 정상_조회시_status와_createdAt_반환() {
            // given
            User user = new User("test@test.com", "$2a$10$hashedPassword");
            SellerApplication application = new SellerApplication(
                user.getUserId(), "국민은행", "123-456-789", "홍길동");
            given(userRepository.findByUserId(any(UUID.class))).willReturn(Optional.of(user));
            given(sellerApplicationRepository.findTopByUserIdOrderByCreatedAtDesc(any())).willReturn(
                Optional.of(application));

            // when
            SellerApplicationStatusResponse response = sellerApplicationService.getMyApplication(TEST_USER_UUID);

            // then
            assertThat(response.status()).isEqualTo("PENDING");
            assertThat(response.createdAt()).isNotNull();
        }
    }
}