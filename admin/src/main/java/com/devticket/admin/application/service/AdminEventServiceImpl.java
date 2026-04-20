package com.devticket.admin.application.service;

import com.devticket.admin.domain.model.AdminActionHistory;
import com.devticket.admin.domain.model.AdminActionType;
import com.devticket.admin.domain.model.AdminTargetType;
import com.devticket.admin.domain.repository.AdminActionRepository;
import com.devticket.admin.infrastructure.external.client.EventInternalClient;
import com.devticket.admin.infrastructure.external.dto.res.InternalAdminEventPageResponse;
import com.devticket.admin.presentation.dto.req.AdminEventSearchRequest;
import com.devticket.admin.presentation.dto.res.AdminEventListResponse;
import com.devticket.admin.presentation.dto.res.AdminEventResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEventServiceImpl implements AdminEventService {

    private final EventInternalClient eventInternalClient;
    private final AdminActionRepository adminActionRepository;   // ← 인터페이스 타입!

    @Override
    public AdminEventListResponse getEventList(AdminEventSearchRequest condition) {
        InternalAdminEventPageResponse page = eventInternalClient.getEvents(condition);
        List<AdminEventResponse> content = page.content().stream()
            .map(e -> new AdminEventResponse(
                e.eventId(), e.title(), e.sellerNickname(), e.status(),
                e.eventDateTime(), e.totalQuantity(), e.remainingQuantity(), e.createdAt()))
            .toList();
        return new AdminEventListResponse(content, page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    @Override
    @Transactional
    public void forceCancel(UUID adminId, UUID eventId) {
        eventInternalClient.forceCancel(adminId, eventId);
        adminActionRepository.save(
            AdminActionHistory.builder()
                .adminId(adminId)
                .targetType(AdminTargetType.EVENT)
                .targetId(eventId)
                .actionType(AdminActionType.FORCE_CANCEL_EVENT)   // ← 기존 enum 재사용
                .build()
        );
    }
}