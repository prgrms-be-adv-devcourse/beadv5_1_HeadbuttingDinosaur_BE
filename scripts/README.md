# scripts/

## gen-docs.py

`docs/api-summary.*`, `docs/dto-summary.*`, `docs/service-status.*` 6개 파일을
레포 소스(Controller / DTO / Service)로부터 자동 재생성합니다.

### 사용법

```bash
# 레포 루트(develop/docs)에서 실행
python scripts/gen-docs.py            # 6개 파일 덮어쓰기
python scripts/gen-docs.py --check    # diff 예상만 stdout, 쓰기 안 함
```

Python 3.9+ 표준 라이브러리만 사용 (외부 패키지 없음).

### 스캔 규칙

| 대상 | 입력 경로 패턴 | 추출 내용 |
|------|--------------|---------|
| API | `{module}/src/main/java/**/*Controller.java` | `@(Get\|Post\|Patch\|Delete\|Put)Mapping` + 클래스 `@RequestMapping` prefix + `@Operation(summary=...)` |
| DTO | `{module}/src/main/java/**/presentation/dto/**/*.java` | `public record` / `public class` 의 필드 (annotation 제거 후 `타입 이름` 쌍) |
| Service | `{module}/src/main/java/**/application/service/*.java` | `public` 메서드 + `interface` 의 추상 메서드 |

대상 모듈: `admin`, `apigateway`, `commerce`, `event`, `member`, `payment`, `settlement`
(frontend 는 TS/React 라 범위 외)

### 원본과의 차이

PR #323 (HyWChoi, 2026-04-07) 에서 Codex task 로 1회 생성된 출력을 기반으로 포맷을
재현했습니다. 단, 다음 두 가지 방향에서 출력이 개선되었습니다.

1. **Commerce 모듈 포함** — 원본 생성 시점에 누락
2. **정확도 향상 (의도된 개선)**
   - 클래스 레벨 `@RequestMapping` prefix 결합 (event `/` → `/api/events` 등)
   - 메서드–summary 매칭 교정 (원본의 행 섞임 수정)
   - interface 추상 메서드 포함
   - `@Schema(example="(...")` 등 미종결 문자열 리터럴에 의한 필드 파싱 실패 복구

원본을 byte 단위로 재현하지는 않습니다.

### 한계

- 정규식 기반 파서라 극단적 Java 문법(중첩 제네릭 + 람다 혼합 등)에 취약할 수 있습니다.
  파싱 실패는 해당 항목 스킵으로 처리되며 빌드는 중단되지 않습니다.
- `@Operation(summary=...)` 가 없으면 메서드명의 camelCase 를 공백으로 분리해 fallback.
- record/class 외의 DTO 타입(enum, sealed interface 등)은 대상 외.
