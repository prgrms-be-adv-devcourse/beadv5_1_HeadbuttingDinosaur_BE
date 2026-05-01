package com.devticket.admin.application.service;

import com.devticket.admin.presentation.dto.req.AdminEventSearchRequest;
import com.devticket.admin.presentation.dto.res.AdminEventListResponse;
import java.util.UUID;

public interface AdminEventService {
    AdminEventListResponse getEventList(AdminEventSearchRequest condition);
    void forceCancel(UUID adminId, UUID eventId);
}
