# admin

> 본 페이지는 ServiceOverview.md §3 admin 섹션의 확장판입니다.

## 1. 모듈 책임

관리자 대시보드 — 이벤트 / 판매자 신청 / 정산 / 사용자 / 기술스택 관리.

**공통 패턴**: "다른 모듈 REST 호출 + AdminActionHistory(audit log) 저장" — 모든 변경 액션이 동일 구조 (코드: `AdminEventServiceImpl.forceCancel`, `AdminSettlementServiceImpl.runSettlement` 등). admin은 **Kafka 비참여**, REST 트리거 + audit 기록만 담당.

**위임 (담당 안 함)**:
- 이벤트 강제취소 비즈니스 로직 → event 모듈 (`PATCH /internal/events/{eventId}/force-cancel`)
- 정산서 생성 → settlement 모듈 (`POST /internal/settlements/run` ⚠ Legacy)
- 회원 상태/권한 변경 → member 모듈 (`PATCH /internal/members/{userId}/status`, `/role`)
- 강제취소 트리거된 환불 처리 → payment 모듈 Saga (admin은 단지 event force-cancel 호출만)

## 2. 외부 API

상세는 [api/summary/admin-summary.md](../api/summary/admin-summary.md) 참조. ★ 핵심 플로우 발췌:

| 메서드 | 경로 | Controller | 비고 |
|---|---|---|---|
| GET | `/api/admin/dashboard` | `AdminDashboardController.getDashboard` | 관리자 진입 화면 요약 |
| GET | `/api/admin/events` | `AdminEventController.getEventList` | 관리자 이벤트 목록 조회 |
| PATCH | `/api/admin/events/{eventId}/force-cancel` ★ | `AdminEventController.cancelEvent` | `X-User-Id` 헤더로 adminId 수신 → AdminEventService.forceCancel → event 측 internal 호출(`event.force-cancelled` 간접 트리거) |
| GET | `/api/admin/seller-applications` | `AdminSellerController.getSellerApplications` | member 측 위임 |
| PATCH | `/api/admin/seller-applications/{applicationId}` | `AdminSellerController.decideSellerApplication` | 승인/반려 |
| GET | `/api/admin/settlements` | `AdminSettlementController.getSettlements` | settlement 측 위임 |
| POST | `/api/admin/settlements/run` ⚠ | `AdminSettlementController.runSettlement` | settlement Legacy `runSettlement` 호출 (settlement 측에서는 컨트롤러 위임 전환 진행 중 — ServiceOverview §4-3) |
| GET | `/api/admin/users` | `AdminUsersController.getUsers` | member 위임 |
| GET | `/api/admin/users/{userId}` | `AdminUsersController.getUserDetail` | 신규 |
| PATCH | `/api/admin/users/{userId}/status` | `AdminUsersController.updateUserStatus` | member 위임 |
| PATCH | `/api/admin/users/{userId}/role` | `AdminUsersController.updateUserRole` | member 위임 |
| POST/PUT/DELETE/GET | `/api/admin/techstacks/**` | `TechStackController` | 기술 스택 CRUD + reindex |

**대상 구분**: 관리자만(`/api/admin/**`). gateway에서 `admin-service` 라우팅 (`/api/admin/**` → admin 서버, application.yml 라우트 가장 마지막).

## 3. 내부 API (다른 서비스가 호출)

| 메서드 | 경로 | Controller | 호출 주체 | 비고 |
|---|---|---|---|---|
| GET | `/internal/admin/tech-stacks` | `InternalTechStackController.getTechStacks` | (다른 모듈) | 기술 스택 마스터 목록 |

> admin이 외부 진입점이므로 internal 엔드포인트는 1건만 존재. 대부분 흐름은 `admin → 다른 모듈`로 호출되는 단방향.

## 4. Kafka

### 발행 (Producer)

**없음** (kafka-design §3 line 70-73 표에 admin 행 없음).

### 수신 (Consumer)

**없음**. admin은 Kafka 비참여.

> 강제취소 시 `event.force-cancelled` Outbox 발행은 event 모듈(`EventService.forceCancel`)이 담당. admin은 이를 트리거하는 REST 호출자.

## 5. DTO

상세는 [dto/dto-overview.md](../dto/dto-overview.md) admin 섹션 참조. 핵심 발췌:

- **Dashboard/Event**: `AdminDashboardResponse`, `AdminEventListResponse`, `AdminEventSearchRequest`
- **SellerApplication**: `AdminSellerApplicationListResponse`, `AdminSellerApplicationDecisionRequest`
- **Settlement**: admin 측은 settlement 응답을 그대로 위임하므로 별도 DTO 최소
- **User**: `AdminUserListResponse`, `AdminUserDetailResponse`, `AdminUserStatusUpdateRequest`, `AdminUserRoleUpdateRequest`
- **TechStack**: `TechStackRequest/Response`, `InternalTechStackListResponse`

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출**:
  - event: `forceCancel` (`PATCH /internal/events/{eventId}/force-cancel`) — 강제취소 시 `X-User-Id`, `X-User-Role` 헤더 전달 (3b940227, 2642e7fe, af824777). `RestClientEventInternalClientImpl`이 PATCH·body·에러전파 모두 처리(테스트 9fb62971로 검증).
  - settlement: `runSettlement` (`POST /internal/settlements/run` ⚠ Legacy), `getSettlements` (`GET /internal/settlements`)
  - member: `searchMembers`, `updateMemberStatus`, `updateMemberRole`, `getSellerApplications`, `decideSellerApplication`
- **Kafka 구독**: 없음.

### 피의존 모듈 (호출됨 / 구독됨)

없음. admin은 외부 진입점이라 다른 모듈이 admin을 호출하지 않음.

### ⚠ 미결 (모듈 누적 1건, 패턴 A)

- `AdminSettlementService.runSettlement` — settlement 측 Legacy 경로 동반 호출. 같은 사유의 마커가 settlement 측에도 있음(`SettlementInternalService.runSettlement`). settlement 측 컨트롤러는 이미 신규 경로 위임으로 전환됨(36b33e9b)이나 admin 호출 endpoint 자체는 그대로라 정리 후 admin client 갱신 필요.

처리 계획 상세: [ServiceOverview.md §4-3](../ServiceOverview.md) (Legacy 정산 경로) 참조.
