package com.devticket.member.application;

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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerApplicationService {

    private final UserRepository userRepository;
    private final SellerApplicationRepository sellerApplicationRepository;

    @Transactional
    public SellerApplicationResponse apply(UUID userId, SellerApplicationRequest request) {
        User user = findUserByUuidOrThrow(userId);
        validateNotAlreadySeller(user);
        validateNoPendingApplication(user.getUserId());

        SellerApplication application = new SellerApplication(
            user.getUserId(),
            request.bankName(),
            request.accountNumber(),
            request.accountHolder()
        );
        SellerApplication saved = sellerApplicationRepository.save(application);

        log.info("판매자 전환 신청 완료: userId={}", userId);
        return SellerApplicationResponse.from(saved);
    }

    public SellerApplicationStatusResponse getMyApplication(UUID userId) {
        User user = findUserByUuidOrThrow(userId);
        SellerApplication application = sellerApplicationRepository.findTopByUserIdOrderByCreatedAtDesc(user.getUserId())
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        return SellerApplicationStatusResponse.from(application);
    }

    // ========== 검증 ==========

    private void validateNotAlreadySeller(User user) {
        if (user.getRole() == UserRole.SELLER) {
            throw new BusinessException(MemberErrorCode.SELLER_APPLICATION_DUPLICATED);
        }
    }

    private void validateNoPendingApplication(UUID userId) {
        if (sellerApplicationRepository.existsByUserIdAndStatus(userId, SellerApplicationStatus.PENDING)) {
            throw new BusinessException(MemberErrorCode.SELLER_APPLICATION_DUPLICATED);
        }
    }

    // ========== 조회 ==========

    private User findUserByUuidOrThrow(UUID userId) {
        return userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
