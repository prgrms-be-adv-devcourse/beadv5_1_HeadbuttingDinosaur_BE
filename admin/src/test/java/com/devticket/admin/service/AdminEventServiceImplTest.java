package com.devticket.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.devticket.admin.application.service.AdminEventServiceImpl;
import com.devticket.admin.domain.model.AdminActionHistory;
import com.devticket.admin.domain.model.AdminActionType;
import com.devticket.admin.domain.model.AdminTargetType;
import com.devticket.admin.domain.repository.AdminActionRepository;
import com.devticket.admin.infrastructure.external.client.EventInternalClient;
import com.devticket.admin.infrastructure.external.dto.res.InternalAdminEventPageResponse;
import com.devticket.admin.infrastructure.external.dto.res.InternalAdminEventResponse;
import com.devticket.admin.presentation.dto.req.AdminEventSearchRequest;
import com.devticket.admin.presentation.dto.res.AdminEventListResponse;
import com.devticket.admin.presentation.dto.res.AdminEventResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminEventServiceImpl 단위 테스트")
class AdminEventServiceImplTest {

    @Mock
    private EventInternalClient eventInternalClient;

    @Mock
    private AdminActionRepository adminActionRepository;

    @InjectMocks
    private AdminEventServiceImpl adminEventService;

    @Nested
    @DisplayName("getEventList")
    class GetEventList {

        @Test
        @DisplayName("내부 클라이언트 응답을 매핑해 이벤트 목록을 반환한다")
        void shouldReturnMappedEventList() {
            // given
            AdminEventSearchRequest condition =
                new AdminEventSearchRequest("Spring", "ON_SALE", null, 0, 20);
            InternalAdminEventResponse internal = new InternalAdminEventResponse(
                "event-1", "Spring 밋업", "DevKim", "ON_SALE",
                "2026-04-10T19:00:00", 50, 30, "2026-03-01T15:00:00"
            );
            InternalAdminEventPageResponse page = new InternalAdminEventPageResponse(
                List.of(internal), 0, 20, 1L, 1
            );
            given(eventInternalClient.getEvents(condition)).willReturn(page);

            // when
            AdminEventListResponse response = adminEventService.getEventList(condition);

            // then
            assertThat(response.page()).isEqualTo(0);
            assertThat(response.size()).isEqualTo(20);
            assertThat(response.totalElements()).isEqualTo(1L);
            assertThat(response.totalPages()).isEqualTo(1);
            assertThat(response.content()).hasSize(1);

            AdminEventResponse mapped = response.content().get(0);
            assertThat(mapped.eventId()).isEqualTo("event-1");
            assertThat(mapped.title()).isEqualTo("Spring 밋업");
            assertThat(mapped.sellerNickname()).isEqualTo("DevKim");
            assertThat(mapped.status()).isEqualTo("ON_SALE");
            assertThat(mapped.eventDateTime()).isEqualTo("2026-04-10T19:00:00");
            assertThat(mapped.totalQuantity()).isEqualTo(50);
            assertThat(mapped.remainingQuantity()).isEqualTo(30);
            assertThat(mapped.createdAt()).isEqualTo("2026-03-01T15:00:00");
        }

        @Test
        @DisplayName("내부 클라이언트가 빈 목록을 반환하면 빈 응답을 그대로 반환한다")
        void shouldReturnEmptyListWhenNoEvents() {
            // given
            AdminEventSearchRequest condition =
                new AdminEventSearchRequest(null, null, null, 0, 20);
            given(eventInternalClient.getEvents(condition))
                .willReturn(new InternalAdminEventPageResponse(List.of(), 0, 20, 0L, 0));

            // when
            AdminEventListResponse response = adminEventService.getEventList(condition);

            // then
            assertThat(response.content()).isEmpty();
            assertThat(response.totalElements()).isZero();
            assertThat(response.totalPages()).isZero();
        }
    }

    @Nested
    @DisplayName("forceCancel")
    class ForceCancel {

        @Test
        @DisplayName("이벤트를 강제 취소하고 FORCE_CANCEL_EVENT 이력을 저장한다")
        void shouldForceCancelEventAndSaveHistory() {
            // given
            UUID adminId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();

            // when
            adminEventService.forceCancel(adminId, eventId);

            // then
            then(eventInternalClient).should().forceCancel(eventId);

            ArgumentCaptor<AdminActionHistory> captor = ArgumentCaptor.forClass(AdminActionHistory.class);
            then(adminActionRepository).should().save(captor.capture());

            AdminActionHistory saved = captor.getValue();
            assertThat(saved.getAdminId()).isEqualTo(adminId);
            assertThat(saved.getTargetType()).isEqualTo(AdminTargetType.EVENT);
            assertThat(saved.getTargetId()).isEqualTo(eventId);
            assertThat(saved.getActionType()).isEqualTo(AdminActionType.FORCE_CANCEL_EVENT);
        }
    }
}
