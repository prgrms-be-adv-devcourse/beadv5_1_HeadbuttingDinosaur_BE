package com.devticket.settlement.infrastructure.client.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event 서비스의 공통 응답 래퍼 SuccessResponse<T>에 대응하는 역직렬화 DTO.
 * { "status": 200, "message": "성공", "data": { ... } }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventServiceResponse<T>(
    int status,
    String message,
    T data
) {

}