package com.devticket.member.presentation.domain;

import com.devticket.member.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    EMAIL_FORMAT_INVALID(400, "MEMBER_001", "이메일 형식이 올바르지 않습니다."),
    PASSWORD_LENGTH_INVALID(400, "MEMBER_002", "비밀번호는 8자 이상 20자 이하여야 합니다."),
    NICKNAME_FORMAT_INVALID(400, "MEMBER_003", "닉네임은 2자 이상 12자 이하, 한글/영문/숫자만 허용됩니다."),
    EMAIL_DUPLICATED(409, "MEMBER_004", "이미 사용 중인 이메일입니다."),
    NICKNAME_DUPLICATED(409, "MEMBER_005", "이미 사용 중인 닉네임입니다."),
    LOGIN_FAILED(401, "MEMBER_006", "이메일 또는 비밀번호가 일치하지 않습니다."),
    ACCOUNT_SUSPENDED(403, "MEMBER_007", "정지된 계정입니다."),
    ACCOUNT_WITHDRAWN(403, "MEMBER_008", "탈퇴한 계정입니다."),
    MEMBER_NOT_FOUND(404, "MEMBER_009", "존재하지 않는 회원입니다."),
    PASSWORD_MISMATCH(400, "MEMBER_010", "기존 비밀번호가 일치하지 않습니다."),
    SELLER_APPLICATION_DUPLICATED(409, "MEMBER_011", "이미 판매자 전환 신청이 진행 중입니다."),
    SOCIAL_EMAIL_CONFLICT(409, "MEMBER_012", "동일 이메일의 계정이 이미 존재하여 소셜 가입이 불가합니다."),
    REFRESH_TOKEN_INVALID(401, "MEMBER_013", "Refresh Token이 유효하지 않습니다.");

    private final int status;
    private final String code;
    private final String message;
}
