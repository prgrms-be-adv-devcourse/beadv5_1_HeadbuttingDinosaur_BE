package com.devticket.admin.infrastructure.external.dto.res;

public record InternalResponse<T>(
    Boolean success,
    T data,
    Object error
) {}