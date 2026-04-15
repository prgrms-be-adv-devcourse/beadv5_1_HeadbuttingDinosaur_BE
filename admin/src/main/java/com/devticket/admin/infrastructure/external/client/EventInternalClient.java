package com.devticket.admin.infrastructure.external.client;

import com.devticket.admin.infrastructure.external.dto.res.InternalAdminEventPageResponse;
import com.devticket.admin.presentation.dto.req.AdminEventSearchRequest;
import java.util.UUID;

public interface EventInternalClient {
    InternalAdminEventPageResponse getEvents(AdminEventSearchRequest condition);
    void forceCancel(UUID eventId);
}