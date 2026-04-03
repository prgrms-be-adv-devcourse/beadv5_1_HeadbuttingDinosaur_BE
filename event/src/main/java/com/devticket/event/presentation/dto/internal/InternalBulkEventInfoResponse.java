package com.devticket.event.presentation.dto.internal;

import java.util.List;

public record InternalBulkEventInfoResponse(
    List<InternalEventInfoResponse> events
) {}
