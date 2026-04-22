package com.devticket.apigateway.infrastructure.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GatewayErrorCode {

    AUTHENTICATION_REQUIRED(401, "COMMON_002", "인증이 필요합니다."),
    TOKEN_EXPIRED(401, "COMMON_003", "토큰이 만료되었습니다."),
    INVALID_TOKEN(401, "COMMON_004", "유효하지 않은 토큰입니다."),
    ACCESS_DENIED(403, "COMMON_005", "접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR(500, "COMMON_006", "서버 내부 오류가 발생했습니다."),
    SERVICE_CONNECT_FAILED(502, "COMMON_007", "외부 서비스 연동에 실패했습니다."),
    SERVICE_UNAVAILABLE(503, "COMMON_008", "서비스를 일시적으로 이용할 수 없습니다."),
    RATE_LIMIT_EXCEEDED(429, "COMMON_009", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    PROFILE_INCOMPLETE(403, "COMMON_010", "프로필 설정을 완료해야 서비스를 이용할 수 있습니다.");

    private final int status;
    private final String code;
    private final String message;
}

