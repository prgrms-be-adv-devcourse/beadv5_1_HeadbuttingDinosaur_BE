# ⎡ DevTicket ⎦
# ci trig
개발자 이벤트 티켓 커머스 플랫폼

---

## 프로젝트 소개

개발자 밋업, 컨퍼런스 등 기술 이벤트를 탐색하고 티켓을 구매할 수 있는 커머스 플랫폼입니다.

**일반 사용자**는 관심 기술 스택 기반으로 이벤트를 탐색하고 티켓을 구매합니다.
**판매자**는 이벤트를 등록하고 티켓을 판매하며 정산을 받습니다.
**어드민**은 회원 관리, 판매자 승인, 이벤트 관리, 정산 관리를 담당합니다.

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Backend | JDK 21, Spring Boot, Spring Security, JPA |
| Database | MySQL |
| Messaging | Apache Kafka |
| Auth | JWT (Access Token + Refresh Token), Google OAuth2.0 |
| Infra | AWS EC2, Docker, GitHub Actions |
| Docs | Swagger (springdoc-openapi) |

---

## 아키텍처

MSA 구조 — 서비스별 독립 배포

```
Client → API Gateway (JWT 검증 + 라우팅)
              ├→ Member Service    (8081)
              ├→ Event Service     (8082)
              ├→ Commerce Service  (8083)
              ├→ Payment Service   (8084)
              ├→ Settlement Service(8085)
              ├→ Log Service       (8086)
              └→ Admin Service     (8087)

서비스 간 동기 통신: Internal API (REST)
서비스 간 비동기 통신: Kafka
```

---

## 서비스 구조

| 서비스 | 포트 | 역할 |
|--------|------|------|
| Gateway | 8080 | JWT 검증, 권한별 라우팅 |
| Member | 8081 | 회원가입, 로그인, 프로필, 판매자 전환 |
| Event | 8082 | 이벤트 CRUD, 검색, 필터링 |
| Commerce | 8083 | 장바구니, 주문, 티켓 |
| Payment | 8084 | 결제, 예치금, 환불 |
| Settlement | 8085 | 정산 |
| Log | 8086 | 행동 로그 수집 (Kafka Consumer) |
| Admin | 8087 | 회원/판매자/이벤트/정산 관리 |

---

## 브랜치 전략

서비스별 독립 브랜치 운영 (모노레포 + 서비스별 Git Flow)

```
devticket-{서비스}     ← 서비스 main (배포 기준)
└── develop/{서비스}   ← 서비스 개발 통합
    └── feat/{서비스}-{기능}  ← 기능 개발
```

---

## 로컬 개발 환경

```bash
# 인프라 실행 (MySQL + Kafka)
docker-compose up -d

# 각 서비스 실행 (IntelliJ에서 개별 실행)
```

---

## 팀원

| 이름 | 역할 |
|------|------|
| | |
| | |
| | |
| | |
| | |
