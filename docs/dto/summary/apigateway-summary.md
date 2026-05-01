# apigateway DTO summary

> 본 문서는 `docs/dto/dto-overview.md §3 apigateway` 의 깊이 확장판.
> apigateway 는 라우팅 / filter chain 전용 — **DTO 0건**.

## DTO

**없음**.

apigateway 모듈은 비즈니스 로직 부재로 presentation/dto/ 디렉토리가 존재하지 않는다. 대신 다음 객체들로 동작:

- **JWT 토큰 페이로드**: 인증 정보(`X-User-Id` 등)는 JWT claims 로 다운스트림에 헤더 형태로 전달 — DTO 가 아님
- **OAuth 토큰 교환 페이로드**: Google OAuth 콜백에서 받는 인증코드 → 토큰 교환 — `member` 모듈로 위임 (`internalOAuthSignUpOrLogin`), DTO 정의는 member 측

## 관련 모듈

- 인증 흐름 DTO: `docs/dto/summary/member-summary.md` (Auth 섹션)
- API endpoint: `docs/api/summary/apigateway-summary.md` (health check 1건)
