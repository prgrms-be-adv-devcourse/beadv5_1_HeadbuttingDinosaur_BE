# admin API summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

관리자 진입점 모듈 — 회원/판매자/이벤트/정산/TechStack 통합 관리.

★ 요구사항:
- 매월 정산 — `runSettlement` 진입점
- 벡터DB — TechStack CRUD + ES 임베딩 동기화

## 외부 API (관리자 권한)

| 영역 | HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|---|
| Dashboard | GET | `/api/admin/dashboard` | `AdminDashboardController#getAdminDashboard` | 관리자 | 관리자 대시보드 통계 |
| Event | GET | `/api/admin/events` | `AdminEventController#getEventList` | 관리자 | 관리자 Event 리스트 조회 |
| Event | PATCH | `/api/admin/events/{eventId}/force-cancel` | `AdminEventController#cancelEvent` | 관리자 | 관리자 강제취소 진입점 (event 모듈 `forceCancel` 호출) |
| Seller | GET | `/api/admin/seller-applications` | `AdminSellerController#getSellerApplicationList` | 관리자 | 판매자 신청 리스트 조회 |
| Seller | PATCH | `/api/admin/seller-applications/{applicationId}` | `AdminSellerController#decideApplication` | 관리자 | 판매자 신청 승인/반려 |
| Settlement | GET | `/api/admin/settlements` | `AdminSettlementController#getAdminSettlementList` | 관리자 | 관리자 정산 내역 조회 |
| Settlement | POST | `/api/admin/settlements/run` ★ | `AdminSettlementController#runSettlement` | 관리자 | (#7) settlement 모듈 측 `SettlementAdminController#runSettlement` 위임 |
| TechStack | GET | `/api/admin/techstacks` ★ | `TechStackController#getTechStacks` | 관리자 | (§2 벡터DB) TechStack 전체 조회 |
| TechStack | POST | `/api/admin/techstacks` ★ | `TechStackController#createTechStack` | 관리자 | (§2 벡터DB) TechStack 생성 (OpenAI embedding 트리거) |
| TechStack | POST | `/api/admin/techstacks/reindex` ★ | `TechStackController#reindexEmptyEmbeddings` | 관리자 | (§2 벡터DB) 비어있는 embedding 재계산 (배치 트리거) |
| TechStack | DELETE | `/api/admin/techstacks/{id}` ★ | `TechStackController#deleteTechStack` | 관리자 | (§2 벡터DB) TechStack 삭제 |
| TechStack | PUT | `/api/admin/techstacks/{id}` ★ | `TechStackController#updateTechStack` | 관리자 | (§2 벡터DB) TechStack 수정 |
| Users | GET | `/api/admin/users` | `AdminUsersController#getUsers` | 관리자 | 회원 목록 조회 |
| Users | GET | `/api/admin/users/{userId}` | `AdminUsersController#getUserDetail` | 관리자 | 회원 상세 조회 |
| Users | PATCH | `/api/admin/users/{userId}/role` | `AdminUsersController#updateUserRole` | 관리자 | 회원 권한 변경 |
| Users | PATCH | `/api/admin/users/{userId}/status` | `AdminUsersController#penalizeUser` | 관리자 | 회원 제재 |

## 내부 API

| 영역 | HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|---|
| TechStack Internal | GET | `/internal/admin/tech-stacks` ★ | `InternalTechStackController#getTechStacks` | ai 모듈 | (§2 벡터DB) ai 측 `TechStackEmbeddingRepository` 가 기술 스택 임베딩 조회 시 호출 |

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
| settlement | `getSettlements`, `runSettlement` ★ (#7) | 정산 조회/실행 |
| 외부 | OpenAI Embedding ★ (§2 벡터DB) | TechStack 임베딩 생성 |
| 외부 | Elasticsearch ★ (§2 벡터DB) | TechStack ES 동기화 |

### 피호출 (REST)

- ai: `getTechStacks` (`/internal/admin/tech-stacks`) ★ (§2 벡터DB)
- 외부 (관리자 클라이언트) — 외부 API 표 참조


## 신규 인프라

- **TechStack ES 동기화** ★ (§2 벡터DB): `TechStackEsEventListener` + `TechStackEsRepository` + `TechStackEsRepositoryImpl` — admin 측 도메인 이벤트 → ES 인덱스 동기화 (in-process `@EventListener` 기반)
