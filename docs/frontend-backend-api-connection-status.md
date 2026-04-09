# 프론트-백엔드 API 연결 상태 점검표

> 기준: `frontend/src/api/*.api.ts`에 정의된 프론트 호출 경로와, 각 모듈 Controller 매핑 및 API Gateway 라우팅을 교차 점검.

## 상태 범례

- <span style="color:#0B6E3E; font-weight:700;">🟢 구현</span>: 프론트 호출 경로와 백엔드 엔드포인트/게이트웨이 라우팅이 모두 정합.
- <span style="color:#B00020; font-weight:700;">🔴 미구현</span>: 프론트에서 호출하지만 백엔드 엔드포인트 자체가 없음.
- <span style="color:#C2185B; font-weight:700;">🩷 구현 필요</span>: 엔드포인트는 있으나 경로 불일치/라우팅 불일치/TODO·Mock 등으로 실연동 불가 또는 불완전.

## 한눈에 보는 모듈별 현황

| 모듈 | 🟢 구현 | 🔴 미구현 | 🩷 구현 필요 | 비고 |
|---|---:|---:|---:|---|
| Auth/Member | 4 | 0 | 0 | 기본 인증/회원 API 정합 |
| Commerce (Cart/Order/Ticket) | 3 | 0 | 1 | 판매자 참여자 조회 경로 불일치 |
| Event/Seller Event | 3 | 3 | 4 | seller 전용 API 경로 다수 불일치 |
| Payment/Wallet/Refund | 3 | 1 | 2 | Wallet 충전 헤더/환불 경로 이슈 |
| Settlement(판매자) | 0 | 0 | 1 | 게이트웨이 라우팅 대상 서비스 불일치 |
| Admin | 1 | 2 | 4 | `/api` prefix 혼재 + Mock/TODO 존재 |

---

## 상세 점검

### 1) Auth/Member

| 프론트 API | 백엔드/게이트웨이 | 상태 | 메모 |
|---|---|---|---|
| `/auth/signup`, `/auth/login`, `/auth/social/google`, `/auth/logout`, `/auth/reissue` | Member AuthController + gateway member 라우팅 일치 | 🟢 구현 | 정상 연동 가능 |
| `/users/profile`, `/users/me`, `/users/me/password`, `/users/me(DELETE)` | Member UserController 일치 | 🟢 구현 | 정상 |
| `/seller-applications`, `/seller-applications/me` | Member SellerApplicationController 일치 | 🟢 구현 | 정상 |
| `/tech-stacks` | Member TechStackController 일치 | 🟢 구현 | 정상 |

### 2) Commerce (Cart/Order/Ticket)

| 프론트 API | 백엔드/게이트웨이 | 상태 | 메모 |
|---|---|---|---|
| `/cart`, `/cart/items`, `/cart/items/{id}` | CartController + gateway commerce 라우팅 일치 | 🟢 구현 | 정상 |
| `/orders`, `/orders/{id}`, `/orders/{id}/cancel` | OrderController 일치 | 🟢 구현 | 정상 |
| `/tickets`, `/tickets/{id}` | TicketController 일치 | 🟢 구현 | 정상 |
| `/seller/events/{eventId}/participants` | SellerTicketController는 `/seller/events/**`( `/api` 없음), gateway는 `/api/seller/events/**`를 event-service로 전달 | 🩷 구현 필요 | prefix/라우팅 모두 재정렬 필요 |

### 3) Event / Seller Event

| 프론트 API | 백엔드/게이트웨이 | 상태 | 메모 |
|---|---|---|---|
| `/events`, `/events/{eventId}` | EventController 일치 | 🟢 구현 | 정상 |
| `/events`(판매자 생성) | EventController `POST /api/events` 존재 | 🟢 구현 | 정상 |
| `/events/search` | 별도 search 매핑 없음 (`GET /api/events` 단일 목록 API만 존재) | 🔴 미구현 | 프론트 전용 search endpoint 미구현 |
| `/seller/events`(판매자 목록) | 공개 API에 seller 목록 endpoint 없음 (`/internal/events/by-seller/{sellerId}`만 존재) | 🔴 미구현 | 외부 공개 endpoint 추가 필요 |
| `/seller/events/{id}`(판매자 상세) | 실제는 `/api/events/seller/{eventId}` | 🩷 구현 필요 | 프론트 경로 변경 또는 백엔드 alias 추가 |
| `/seller/events/{id}` PATCH | 실제는 `/api/events/{eventId}` | 🩷 구현 필요 | 경로 정합 필요 |
| `/seller/events/{id}/cancel` | cancel 전용 endpoint 없음 | 🔴 미구현 | 상태 전이 API 필요 |
| `/seller/events/{id}/statistics` | 실제는 `/api/events/{eventId}/statistics` | 🩷 구현 필요 | 경로 정합 필요 |
| `/seller/events/{id}/refunds` | Refund API는 `/api/seller/refunds/events/{eventId}` | 🩷 구현 필요 | 도메인 경로 표준화 필요 |

### 4) Payment / Wallet / Refund

| 프론트 API | 백엔드/게이트웨이 | 상태 | 메모 |
|---|---|---|---|
| `/payments/ready`, `/payments/confirm` | PaymentController 일치 | 🟢 구현 | 정상 |
| `/wallet`, `/wallet/transactions`, `/wallet/withdraw` | WalletController 일치 | 🟢 구현 | 정상 |
| `/wallet/charge/confirm` | WalletController 일치 | 🟢 구현 | 정상 |
| `/wallet/charge` | WalletController는 `Idempotency-Key` 헤더 필수, 프론트 API에 헤더 주입 없음 | 🩷 구현 필요 | 헤더 생성/전달 로직 프론트에 추가 필요 |
| `/refunds`, `/refunds/{id}`, `/refunds/pg` | 백엔드는 `/api/refunds/{refundId}`, `/api/refunds/pg/{ticketId}` | 🩷 구현 필요 | 프론트 `refundByPg('/refunds/pg')` 호출 규격 불일치 |
| `/refunds/wallet` | wallet 환불 endpoint 없음 | 🔴 미구현 | Wallet 환불 API 신설 또는 프론트 제거 필요 |

### 5) Settlement(판매자)

| 프론트 API | 백엔드/게이트웨이 | 상태 | 메모 |
|---|---|---|---|
| `/seller/settlements`, `/seller/settlements/{id}` | SettlementController는 존재하나 `/api` prefix 없음. gateway는 해당 경로를 event-service(8082)로 전달 | 🩷 구현 필요 | ① controller를 `/api/seller/settlements`로 통일 + ② gateway를 settlement-service(8085)로 재매핑 필요 |

### 6) Admin

| 프론트 API | 백엔드/게이트웨이 | 상태 | 메모 |
|---|---|---|---|
| `/admin/seller-applications`, `/admin/seller-applications/{id}` | AdminSellerController는 `/api/admin/**`로 정합 | 🟢 구현 | 정상 |
| `/admin/dashboard`, `/admin/events`, `/admin/users`, `/admin/settlements`, `/admin/settlements/run` | 다수 controller가 `/admin/**`( `/api` 누락). gateway는 `/api/admin/**`만 라우팅 | 🩷 구현 필요 | admin 모듈 전체 prefix 일관화 필요 |
| `/admin/events/{id}/force-cancel` | endpoint는 있으나 메서드 body 비어 있음 | 🩷 구현 필요 | 실제 취소 로직/응답 정의 필요 |
| `/admin/settlements/run` | endpoint는 있으나 메서드 body 비어 있음 | 🩷 구현 필요 | 배치 트리거 로직 필요 |
| `/admin/events`, `/admin/settlements` | Mock 데이터 반환 | 🩷 구현 필요 | 실제 서비스/저장소 연동 필요 |
| `/admin/users/{userId}` | 백엔드 상세조회 endpoint 없음 | 🔴 미구현 | 상세조회 API 추가 필요 |
| `/admin/fee-policies`, `/admin/fee-policies/{id}` | 관련 controller 없음 | 🔴 미구현 | 수수료 정책 API 구현 필요 |

---

## 우선순위별 바로 할 일 (Quick Action)

1. **게이트웨이 라우팅 정합화 (최우선)**
   - `/api/seller/settlements/**`를 settlement-service로 이동.
   - `/api/seller/events/**`를 실제 담당 서비스(event/commerce)로 재설계.
2. **경로 prefix 통일**
   - Admin, Settlement, SellerTicket controller의 `/api` prefix 일관 적용.
3. **프론트/백엔드 계약(Contract) 재정의**
   - seller 이벤트 상세/수정/통계, 환불(PG/Wallet) endpoint 규격 확정.
4. **미구현 API 보강**
   - `GET /api/admin/users/{userId}`
   - fee policy API
   - seller event cancel/list API
5. **Mock/TODO 제거**
   - admin 이벤트/정산 조회 및 실행 API 실서비스 연동.
