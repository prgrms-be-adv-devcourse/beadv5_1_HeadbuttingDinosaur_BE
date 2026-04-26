package com.devticket.admin.presentation.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminActionHistorySummary(
    String actionType,
    UUID adminId,
    LocalDateTime createdAt
) {}
