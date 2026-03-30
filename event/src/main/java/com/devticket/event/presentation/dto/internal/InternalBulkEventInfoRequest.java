package com.devticket.event.presentation.dto.internal;

import java.util.List;
import java.util.UUID;

public record InternalBulkEventInfoRequest(
    List<UUID> eventIds
) {}
