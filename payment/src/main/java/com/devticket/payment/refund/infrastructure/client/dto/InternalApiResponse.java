package com.devticket.payment.refund.infrastructure.client.dto;

/**
 * Event 서비스 internal API 응답 wrapper.
 * Event 쪽 com.devticket.event.common.response.SuccessResponse 구조와 호환.
 */
public record InternalApiResponse<T>(
    int status,
    String message,
    T data
) {}
