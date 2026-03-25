package com.devticket.event.common.response;

import lombok.Getter;

@Getter
public class SuccessResponse<T> {
    private final int status;
    private final String message;
    private final T data;

    private SuccessResponse(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    // 일반적인 200 OK 응답
    public static <T> SuccessResponse<T> success(T data) {
        return new SuccessResponse<>(200, "성공", data);
    }

    // 201 Created 응답 (생성 시 사용)
    public static <T> SuccessResponse<T> created(T data) {
        return new SuccessResponse<>(201, "생성 성공", data);
    }
}
