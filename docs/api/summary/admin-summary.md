# admin API summary

> 본 문서는 `docs/api/api-overview.md §1 admin` 의 깊이 확장판.
> 관리자 진입점 모듈 — 회원/판매자/이벤트/정산/TechStack 통합 관리.
> ⚠ admin 측 일부 endpoint 는 다른 모듈 위임 (event 강제취소, settlement 정산 등). 위임 정합성은 각 모듈 문서 참조.

## 외부 API (관리자 권한)

| 영역 | HTTP | Path | Controller#Method | 요청 DTO | 응답 DTO | 호출 주체 | 설명 |
|---|---|---|---|---|---|---|---|
| Dashboard | GET | `/api/admin/dashboard` | `AdminDashboardController#getAdminDashboard` | - | `AdminDashboardResponse` | 관리자 | 관리자 대시보드 통계 |
| Event | GET | `/api/admin/events` | `AdminEventController#getEventList` | `AdminEventSearchRequest` | `AdminEventListResponse` | 관리자 | 관리자 Event 리스트 조회 |
| Event | PATCH | `/api/admin/events/{eventId}/force-cancel` ★ | `AdminEventController#cancelEvent` | `AdminForceCancelEventRequest` (헤더 정합 2642e7fe/af824777/3b940227) | `EventCancelResponse` | 관리자 | 관리자 강제취소 진입점 (event 모듈 `forceCancel` 호출) |
| Seller | GET | `/api/admin/seller-applications` | `AdminSellerController#getSellerApplicationList` | - | `SellerApplicationListResponse` | 관리자 | 판매자 신청 리스트 조회 |
| Seller | PATCH | `/api/admin/seller-applications/{applicationId}` | `AdminSellerController#decideApplication` | `AdminDecideSellerApplicationRequest` | - | 관리자 | 판매자 신청 승인/반려 |
| Settlement | GET | `/api/admin/settlements` | `AdminSettlementController#getAdminSettlementList` | `AdminSettlementSearchRequest` | `AdminSettlementListResponse` | 관리자 | 관리자 정산 내역 조회 |
| Settlement | POST | `/api/admin/settlements/run` | `AdminSettlementController#runSettlement` | - | - | 관리자 | ⚠ 본문 주석 처리(`AdminSettlementController.java:43-45` dead) — settlement 모듈 측 `SettlementAdminController#runSettlement` 위임 미연결 |
| TechStack | GET | `/api/admin/techstacks` | `TechStackController#getTechStacks` | - | `List<GetTechStackResponse>` | 관리자 | TechStack 전체 조회 |
| TechStack | POST | `/api/admin/techstacks` | `TechStackController#createTechStack` | `CreateTechStackRequest` | `CreateTechStackResponse` | 관리자 | TechStack 생성 |
| TechStack | POST | `/api/admin/techstacks/reindex` | `TechStackController#reindexEmptyEmbeddings` | - | - | 관리자 | 비어있는 embedding 재계산 (배치 트리거) |
| TechStack | DELETE | `/api/admin/techstacks/{id}` | `TechStackController#deleteTechStack` | `DeleteTechStackRequest` | `DeleteTechStackResponse` | 관리자 | TechStack 삭제 |
| TechStack | PUT | `/api/admin/techstacks/{id}` | `TechStackController#updateTechStack` | `UpdateTechStackRequest` | `UpdateTechStackResponse` | 관리자 | TechStack 수정 |
| Users | GET | `/api/admin/users` | `AdminUsersController#getUsers` | `AdminUserSearchRequest` | `AdminUserListResponse` | 관리자 | 회원 목록 조회 |
| Users | GET | `/api/admin/users/{userId}` | `AdminUsersController#getUserDetail` | - | `AdminUserDetailResponse` | 관리자 | 회원 상세 조회 |
| Users | PATCH | `/api/admin/users/{userId}/role` | `AdminUsersController#updateUserRole` | `AdminUserUpdateRoleRequest` | - | 관리자 | 회원 권한 변경 |
| Users | PATCH | `/api/admin/users/{userId}/status` | `AdminUsersController#penalizeUser` | `AdminUserPenalizeRequest` | - | 관리자 | 회원 제재 |

## 내부 API

| 영역 | HTTP | Path | Controller#Method | 비고 |
|---|---|---|---|---|
| TechStack Internal | (검증 필요) | (검증 필요) | `InternalTechStackController` | ⚠ 코드 검증 필요 — endpoint 매핑/호출 주체 미확인. 가장 유력한 caller 는 ai 모듈 `TechStackEmbeddingRepository` (벡터 저장소 동기화 가능성) |

## Kafka

### 발행

**없음** (admin 모듈 코드 기준 Outbox 발행 0건). 강제취소는 admin → event REST 호출 → event 측에서 `event.force-cancelled` Outbox 발행.

### 수신

`TechStackEsEventListener` 가 admin 측 자체 도메인 이벤트(`TechStackCreatedEvent` / `TechStackUpdatedEvent` / `TechStackDeletedEvent`) 를 수신해 ES 동기화. ⚠ Kafka 가 아닌 Spring `@EventListener` 기반 (in-process).

## 호출 의존성

### 호출 (REST)

| 호출 대상 | 메서드 | 용도 |
|---|---|---|
| event | `forceCancel` (`PATCH /internal/events/{eventId}/force-cancel`) | 관리자 강제취소 |
| member | `searchMembers`, `getMemberInfo`, `updateMemberRole`, `updateMemberStatus`, `getSellerApplications`, `decideSellerApplication` | 회원/판매자 관리 |
| settlement | `getSettlements` (`SettlementInternalClient`), `runSettlement` | 정산 조회/실행 |
| 외부 | OpenAI Embedding | TechStack 임베딩 (admin 측에서 생성, ai 가 조회) |
| 외부 | Elasticsearch | TechStack ES 동기화 |

### 피호출 (REST)

- 외부 (관리자 클라이언트) — 외부 API 표 참조

## DTO 발췌

- **Request**: `AdminDecideSellerApplicationRequest`, `AdminUserSearchRequest`, `AdminUserUpdateRoleRequest`, `AdminUserPenalizeRequest`, `AdminEventSearchRequest`, `AdminForceCancelEventRequest`, `AdminSettlementSearchRequest`, `CreateTechStackRequest`, `UpdateTechStackRequest`, `DeleteTechStackRequest`
- **Response**: `AdminDashboardResponse`, `AdminActionHistorySummary`, `AdminEventListResponse`, `AdminEventResponse`, `EventCancelResponse`, `AdminSettlementListResponse`, `SettlementResponse` (admin 표면용 — settlement 측 동명 record 와 별도), `AdminUserListResponse`, `InternalMemberDetailResponse`, `InternalMemberPageResponse`, `SellerApplicationListResponse`, `GetTechStackResponse`, `CreateTechStackResponse`, `UpdateTechStackResponse`, `DeleteTechStackResponse`

> DTO 필드 표 / source 경로 깊이: `docs/dto/summary/admin-summary.md`

## 신규 인프라 / ⚠ 미결

- **TechStack ES 동기화**: `TechStackEsEventListener` + `TechStackEsRepository` + `TechStackEsRepositoryImpl` — admin 측 도메인 이벤트 → ES 인덱스 동기화 (in-process `@EventListener` 기반)
- **AdminSettlementController.runSettlement L43-45 dead**: 본문 주석 처리 (`AdminSettlementService.runSettlement(adminId)` 호출 라인 비활성). settlement 모듈 측 위임 결정 필요.
- **`/api/admin/settlements` path 충돌 가능성**: admin 의 `AdminSettlementController` 와 settlement 의 `SettlementAdminController` 가 같은 path prefix 사용. gateway 라우팅 / Spring 컴포넌트 스캔 분리로 의존하는 구조. 정합성 검증 필요.
