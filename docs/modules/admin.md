# admin

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

## 1. 모듈 책임

관리자 대시보드 — 이벤트 / 판매자 신청 / 정산 / 사용자 / 기술스택 관리.

**공통 패턴**: "다른 모듈 REST 호출 + AdminActionHistory(audit log) 저장" — 모든 변경 액션이 동일 구조 (코드: `AdminEventServiceImpl.forceCancel`, `AdminSettlementServiceImpl.runSettlement` 등). admin 은 **Kafka 비참여**, REST 트리거 + audit 기록만 담당.

**위임 (담당 안 함)**:
- 이벤트 강제취소 비즈니스 로직 → event 모듈 (`PATCH /internal/events/{eventId}/force-cancel`)
- 정산서 생성 → settlement 모듈
- 회원 상태/권한 변경 → member 모듈 (`PATCH /internal/members/{userId}/status`, `/role`)
- 강제취소 트리거된 환불 처리 → payment 모듈 Saga (admin 은 단지 event force-cancel 호출만)

## 2. 외부 API

상세는 [api/summary/admin-summary.md](../api/summary/admin-summary.md) 참조.

| 메서드 | 경로 | Controller | 비고 |
|---|---|---|---|
| GET | `/api/admin/dashboard` | `AdminDashboardController.getDashboard` | 관리자 진입 화면 요약 |
| GET | `/api/admin/events` | `AdminEventController.getEventList` | 관리자 이벤트 목록 조회 |
| PATCH | `/api/admin/events/{eventId}/force-cancel` | `AdminEventController.cancelEvent` | `X-User-Id` 헤더로 adminId 수신 → AdminEventService.forceCancel → event 측 internal 호출(`event.force-cancelled` 간접 트리거) |
| GET | `/api/admin/seller-applications` | `AdminSellerController.getSellerApplications` | member 측 위임 |
| PATCH | `/api/admin/seller-applications/{applicationId}` | `AdminSellerController.decideSellerApplication` | 승인/반려 |
| GET | `/api/admin/settlements` | `AdminSettlementController.getSettlements` | settlement 측 위임 |
| POST | `/api/admin/settlements/run` ★ | `AdminSettlementController.runSettlement` | settlement 측 정산 실행 위임 |
| GET | `/api/admin/users` | `AdminUsersController.getUsers` | member 위임 |
| GET | `/api/admin/users/{userId}` | `AdminUsersController.getUserDetail` | member 위임 |
| PATCH | `/api/admin/users/{userId}/status` | `AdminUsersController.updateUserStatus` | member 위임 |
| PATCH | `/api/admin/users/{userId}/role` | `AdminUsersController.updateUserRole` | member 위임 |
| POST/PUT/DELETE/GET | `/api/admin/techstacks/**` ★ | `TechStackController` | 기술 스택 CRUD + reindex (OpenAI 임베딩 → ES 인덱싱) |

**대상 구분**: 관리자만(`/api/admin/**`). gateway 에서 `admin-service` 라우팅 (`/api/admin/**` → admin 서버, application.yml 라우트 가장 마지막).

## 3. 내부 API (다른 서비스가 호출)

| 메서드 | 경로 | Controller | 호출 주체 | 비고 |
|---|---|---|---|---|
| GET | `/internal/admin/tech-stacks` ★ | `InternalTechStackController.getTechStacks` | (ai 등 다른 모듈) | 기술 스택 마스터 목록 |

> admin 이 외부 진입점이므로 internal 엔드포인트는 1건만 존재. 대부분 흐름은 `admin → 다른 모듈` 로 호출되는 단방향.

## 4. Kafka

### 발행 (Producer)

**없음** (kafka-design §3 line 70-73 표에 admin 행 없음).

### 수신 (Consumer)

**없음**. admin 은 Kafka 비참여.

> 강제취소 시 `event.force-cancelled` Outbox 발행은 event 모듈(`EventService.forceCancel`) 이 담당. admin 은 이를 트리거하는 REST 호출자.

### Spring `@EventListener` (in-process)

`TechStackEsEventListener` 가 admin 자체 도메인 이벤트(`TechStackCreatedEvent`/`TechStackUpdatedEvent`/`TechStackDeletedEvent`) 를 수신해 Elasticsearch 인덱스 동기화 — 임베딩 저장 흐름.

## 5. DTO

상세는 [dto/summary/admin-summary.md](../dto/summary/admin-summary.md) 참조. 핵심 발췌:

- **Dashboard/Event**: `AdminDashboardResponse`, `AdminEventListResponse`, `AdminEventSearchRequest`
- **SellerApplication**: `SellerApplicationListResponse`, `AdminDecideSellerApplicationRequest`
- **Settlement**: `AdminSettlementListResponse`, `SettlementResponse` (admin 표면용 — settlement 측 동명 record 와 별도)
- **User**: `AdminUserListResponse`, `UserDetailResponse`, `UserStatusRequest`, `UserRoleRequest`
- **TechStack**: `CreateTechStackRequest`, `UpdateTechStackRequest`, `DeleteTechStackRequest`, `GetTechStackResponse`, `CreateTechStackResponse`, `UpdateTechStackResponse`, `DeleteTechStackResponse`

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출**:
  - event: `forceCancel` (`PATCH /internal/events/{eventId}/force-cancel`) — 강제취소 시 `X-User-Id`, `X-User-Role` 헤더 전달
  - settlement: `runSettlement` ★, `getSettlements`
  - member: `searchMembers`, `updateMemberStatus`, `updateMemberRole`, `getSellerApplications`, `decideSellerApplication`
  - 외부: OpenAI Embedding (TechStack 임베딩), Elasticsearch (TechStack 인덱싱) —
- **Kafka 구독**: 없음

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출**:
  - ai: `getTechStacks` (`/internal/admin/tech-stacks`) — 기술 스택 임베딩 조회
- **Kafka 피구독**: 없음
