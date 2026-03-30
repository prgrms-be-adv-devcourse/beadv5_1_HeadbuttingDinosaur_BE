package com.devticket.event.presentation.dto.internal;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record InternalBulkEventInfoRequest(
    @NotEmpty(message = "eventIds는 비어있을 수 없습니다")
    @NotNull(message = "eventIds는 null이 될 수 없습니다")
    List<@NotNull(message = "eventIds의 각 원소는 null이 될 수 없습니다") UUID> eventIds
) {}
