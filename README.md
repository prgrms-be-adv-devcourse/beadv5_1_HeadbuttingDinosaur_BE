# DevTicket Frontend

개발자 이벤트·컨퍼런스 티케팅 플랫폼 프론트엔드

## 시작하기

```bash
npm install
npm run dev       # http://localhost:3000
npm run build
npm run preview
```

## 환경변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `VITE_API_BASE_URL` | 백엔드 게이트웨이 URL | `http://localhost:8080` |

## 구조

```
src/
├── api/               # 모듈별 API 함수 + 타입
│   ├── client.ts      # axios 인스턴스, 토큰 인터셉터
│   ├── types.ts       # 전체 DTO 타입 정의
│   ├── auth.api.ts    # /auth/*, /users/*
│   ├── events.api.ts  # /events/*, /seller/events/*
│   ├── cart.api.ts
│   ├── orders.api.ts
│   ├── tickets.api.ts
│   ├── payments.api.ts
│   ├── wallet.api.ts
│   ├── refunds.api.ts
│   ├── seller.api.ts
│   ├── admin.api.ts
│   └── index.ts       # 단일 barrel export
│
├── contexts/
│   ├── AuthContext.tsx  # 전역 인증 상태
│   └── ToastContext.tsx # 전역 토스트 알림
│
├── components/
│   ├── Layout.tsx        # GNB (일반 사용자)
│   ├── SellerLayout.tsx  # 판매자 사이드바
│   ├── AdminLayout.tsx   # 관리자 다크 사이드바
│   ├── EventCard.tsx
│   └── Pagination.tsx
│
├── pages/
│   ├── EventList.tsx       # 홈 / 이벤트 목록
│   ├── EventDetail.tsx     # 이벤트 상세
│   ├── Login.tsx
│   ├── Signup.tsx          # 2단계 회원가입
│   ├── SignupComplete.tsx
│   ├── Cart.tsx
│   ├── Payment.tsx
│   ├── PaymentComplete.tsx
│   ├── MyPage.tsx          # 탭: 티켓/주문/예치금/환불/설정
│   ├── SellerApply.tsx
│   ├── seller/
│   │   ├── SellerDashboard.tsx
│   │   ├── SellerEventCreate.tsx
│   │   ├── SellerEventEdit.tsx
│   │   ├── SellerEventDetail.tsx
│   │   └── SellerSettlement.tsx
│   └── admin/
│       ├── AdminDashboard.tsx
│       ├── AdminUsers.tsx
│       ├── AdminEvents.tsx
│       ├── AdminApplications.tsx
│       └── AdminSettlements.tsx
│
├── styles/globals.css  # 디자인 시스템 토큰 + 유틸리티
├── App.tsx             # 라우팅 (역할별 가드)
└── main.tsx

```

## 역할별 접근 경로

| 역할 | 경로 |
|------|------|
| c| |
| | |
| | |
| | |
| | |