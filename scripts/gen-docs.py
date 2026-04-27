#!/usr/bin/env python3
"""
docs/api-summary.*, docs/dto-summary.*, docs/service-status.* 자동 재생성 스크립트.

원 저자: HyWChoi (PR #323, Codex task — ephemeral)
본 스크립트는 PR #323의 출력 포맷을 재현하여 커밋 가능한 형태로 복원한 재작성본입니다.
Commerce 모듈 누락 해소 및 향후 모듈 추가 대응이 목적.

사용:
    python scripts/gen-docs.py              # 전체 재생성
    python scripts/gen-docs.py --check      # diff만 stdout 출력 (쓰기 안 함)

스캔 규칙:
  - API     : `**/presentation/controller/*Controller.java`
              `@Operation(summary="...")` + `@GetMapping/@PostMapping/...`
              클래스 레벨 `@RequestMapping` prefix 결합
  - DTO     : `**/presentation/dto/**/*.java`
              `public record` 또는 `public class` 의 필드 추출
  - Service : `**/application/service/*.java`
              `public` 메서드 시그니처 추출, 생성자 제외

한계 / 주의:
  - 정규식 기반 파서라 일반적이지 않은 Java 문법에 취약 (중첩 제네릭, 람다 등은
    가능한 범위에서 방어적으로 처리).
  - @Operation summary 누락 시 methodName 의 camelCase 를 공백 분리해 fallback.
  - frontend 모듈은 TS/React 라 본 스크립트 범위 외.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

REPO_ROOT = Path(__file__).resolve().parent.parent
DOCS_DIR = REPO_ROOT / "docs"

MODULES: list[str] = [
    "admin",
    "ai",
    "apigateway",
    "commerce",
    "event",
    "member",
    "payment",
    "settlement",
]

HTTP_ANNOTATIONS = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PatchMapping": "PATCH",
    "DeleteMapping": "DELETE",
    "PutMapping": "PUT",
}


# ---------------------------------------------------------------------------
# Java 파서 유틸
# ---------------------------------------------------------------------------

_STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')


def mask_strings(src: str) -> str:
    """Java 문자열 리터럴을 중립 placeholder 로 치환.

    리터럴 안에 괄호·콤마·따옴표 이스케이프 등이 있어도 annotation/field
    파싱을 방해하지 않도록 `""` 형태로만 남김. 원본 값이 필요 없는 스캔
    단계에서만 사용."""
    return _STRING_RE.sub('""', src)


def strip_comments(src: str) -> str:
    """Java 주석 (//..., /* ... */) 제거."""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.DOTALL)
    src = re.sub(r"//[^\n]*", "", src)
    return src


def camel_to_words(name: str) -> str:
    """camelCase / PascalCase → 공백 분리 소문자."""
    spaced = re.sub(r"(?<!^)(?=[A-Z])", " ", name)
    return spaced.lower()


def rel_source(path: Path) -> str:
    """REPO_ROOT 기준 상대 경로 (forward slash)."""
    return str(path.relative_to(REPO_ROOT)).replace("\\", "/")


def split_top_level(s: str, sep: str = ",") -> list[str]:
    """중첩 괄호/제네릭을 고려해 최상위 구분자 기준으로 분할."""
    parts: list[str] = []
    depth = 0
    buf: list[str] = []
    for ch in s:
        if ch in "<([{":
            depth += 1
            buf.append(ch)
        elif ch in ">)]}":
            depth -= 1
            buf.append(ch)
        elif ch == sep and depth == 0:
            parts.append("".join(buf))
            buf = []
        else:
            buf.append(ch)
    if buf:
        parts.append("".join(buf))
    return parts


# ---------------------------------------------------------------------------
# Controller 스캔
# ---------------------------------------------------------------------------

@dataclass
class ApiEntry:
    module: str
    controller: str
    method: str
    httpMethod: str
    path: str
    summary: str
    source: str
    order: int = 0  # 원본 파일 내 선언 순서 (정렬용)


def _extract_annotation_value(anno_body: str) -> str | None:
    """`@GetMapping("/x")` 또는 `@GetMapping(value = "/x")` 등에서 path 추출."""
    m = re.search(r'"([^"]*)"', anno_body)
    return m.group(1) if m else None


def _neutralize_string_literals(src: str) -> str:
    """문자열 리터럴 내부의 모든 문자를 공백으로 치환 (길이 보존).

    `@Tag(name = "SELLER(판매자)")` 처럼 문자열 안의 ')' 가 annotation 본문
    파싱을 깨뜨리는 문제를 차단하면서, `mask_strings` 와 달리 raw 와의 위치
    인덱스 1:1 대응을 유지한다."""
    out = []
    i = 0
    while i < len(src):
        c = src[i]
        if c == '"':
            out.append('"')
            i += 1
            while i < len(src):
                ch = src[i]
                if ch == '\\' and i + 1 < len(src):
                    out.append(' ')
                    out.append(' ')
                    i += 2
                    continue
                if ch == '"':
                    out.append('"')
                    i += 1
                    break
                out.append(' ' if ch != '\n' else '\n')
                i += 1
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def _find_class_base_path(content: str) -> str:
    # 위치 기반 탐색:
    #   1) 클래스/인터페이스 선언 직전(가장 가까운) `@RequestMapping(...)` 찾기.
    #   2) 그 annotation body 의 raw 문자열에서 path 리터럴 추출.
    # 정규식만으로 처리하면 `@Tag(name = "SELLER(판매자)")` 처럼 string literal
    # 안에 ')' 가 포함된 다른 annotation 이 사이에 끼어 있을 때 매칭이 실패한다.
    neutral = _neutralize_string_literals(content)
    cls_match = re.search(r'public\s+(?:class|interface)\s+\w+', neutral)
    if not cls_match:
        return ""
    cls_pos = cls_match.start()
    rm_iter = [m for m in re.finditer(r'@RequestMapping\s*\(', neutral)
               if m.start() < cls_pos]
    if not rm_iter:
        return ""
    last = rm_iter[-1]
    open_paren = last.end() - 1
    depth = 0
    end = -1
    for i in range(open_paren, len(neutral)):
        c = neutral[i]
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                end = i
                break
    if end < 0:
        return ""
    body = content[open_paren + 1:end]
    path = _extract_annotation_value(body)
    return path or ""


def _extract_summary(annotations: str) -> str | None:
    m = re.search(
        r'@Operation\s*\(([^)]*)\)',
        annotations,
        re.DOTALL,
    )
    if not m:
        return None
    body = m.group(1)
    s = re.search(r'summary\s*=\s*"([^"]*)"', body)
    if s:
        return s.group(1)
    return None


def scan_controllers(module: str) -> list[ApiEntry]:
    base = REPO_ROOT / module / "src" / "main" / "java"
    if not base.exists():
        return []

    results: list[ApiEntry] = []
    files = sorted(
        base.rglob("*Controller.java"),
        key=lambda p: (str(p.parent).lower(), p.name.lower()),
    )
    for f in files:
        # 테스트 / 내부 경로 필터 — presentation 또는 controller 디렉터리 포함
        parts = {p.lower() for p in f.parts}
        if "test" in parts:
            continue
        raw = f.read_text(encoding="utf-8")
        content = strip_comments(raw)

        class_match = re.search(r'public\s+(?:class|interface)\s+(\w+)', content)
        if not class_match:
            continue
        controller_name = class_match.group(1)

        base_path = _find_class_base_path(content)

        # 메서드 단위 추출:
        #   (연속 annotation 블록) + (HTTP mapping annotation) + (선택 annotation)
        #   + `public ReturnType methodName(`
        # 2단계 파싱:
        #   1) @(Get|Post|...)Mapping 위치 전부 찾기
        #   2) 각 위치에 대해 앞(annotation 블록)과 뒤(메서드 선언) 파싱
        mapping_re = re.compile(
            r'@(Get|Post|Patch|Delete|Put)Mapping\s*(?:\(([^)]*)\))?',
        )
        method_head_re = re.compile(
            r'\s*((?:@\w+(?:\s*\([^)]*\))?\s*)*)'
            r'public\s+'
            r'(?:(?:final|static|abstract|synchronized|default)\s+)*'
            r'[\w<>,\s?\[\]]+?\s+(\w+)\s*\(',
            re.DOTALL,
        )
        for hm in mapping_re.finditer(content):
            http_tag, mapping_body = hm.group(1), hm.group(2)
            http = HTTP_ANNOTATIONS[f"{http_tag}Mapping"]

            sub_path = _extract_annotation_value(mapping_body or "") or ""
            full_path = (base_path + sub_path) if sub_path else base_path
            if not full_path:
                full_path = "/"

            # 직전 annotation 블록 (같은 메서드 소속):
            # 현재 HTTP 매핑 앞쪽에서 가까운 `)` 또는 `;` 또는 `{`까지 거슬러 올라가며 annotation 수집.
            # 간단화: 현재 위치에서 이전 HTTP 매핑 끝 또는 이전 메서드 `}`/`;` 이후 텍스트를 pre_region 으로.
            start = 0
            for prev in mapping_re.finditer(content, 0, hm.start()):
                start = prev.end()
            # 이전 메서드 `}` 또는 `;`가 더 뒤에 있으면 그 이후로 경계 갱신
            brace = max(content.rfind("}", 0, hm.start()), content.rfind(";", 0, hm.start()))
            if brace > start:
                start = brace + 1

            pre_region = content[start:hm.start()]

            # 직후: 첫 번째 `public ReturnType methodName(`
            tail = content[hm.end():]
            mh = method_head_re.match(tail)
            if not mh:
                continue
            mid_anno, method_name = mh.group(1), mh.group(2)

            summary = _extract_summary(pre_region + mid_anno)
            if not summary:
                summary = camel_to_words(method_name)

            results.append(
                ApiEntry(
                    module=module,
                    controller=controller_name,
                    method=method_name,
                    httpMethod=http,
                    path=full_path,
                    summary=summary,
                    source=rel_source(f),
                    order=len(results),
                )
            )
    return results


# ---------------------------------------------------------------------------
# DTO 스캔
# ---------------------------------------------------------------------------

@dataclass
class DtoField:
    name: str
    type: str


@dataclass
class DtoEntry:
    module: str
    name: str
    kind: str  # "record" | "class"
    group: str  # "request" | "response"
    source: str
    fields: list[DtoField] = field(default_factory=list)


def _dto_group(source: str) -> str:
    parts = source.split("/")
    for seg in ("req", "request"):
        if seg in parts:
            return "request"
    for seg in ("res", "response"):
        if seg in parts:
            return "response"
    # 내부 DTO (internal 등) 도 response 쪽에 가까운 경향이 있으나 이름으로 최종 판정
    return "response" if "Response" in source else "request"


def _parse_record_fields(inner: str) -> list[DtoField]:
    # annotation 제거 (다중 매개변수에 걸친 @Xxx(...) 포함)
    cleaned = re.sub(r'@\w+(?:\s*\([^()]*(?:\([^()]*\)[^()]*)*\))?', '', inner, flags=re.DOTALL)
    fields: list[DtoField] = []
    for raw in split_top_level(cleaned):
        tok = raw.strip()
        if not tok:
            continue
        m = re.match(r'^([\w.\s<>,?\[\]]+?)\s+(\w+)\s*$', tok, re.DOTALL)
        if not m:
            continue
        type_str = " ".join(m.group(1).split())
        fields.append(DtoField(name=m.group(2), type=type_str))
    return fields


def _parse_class_fields(body: str) -> list[DtoField]:
    # `private Type name;` 또는 `public Type name;` 형태
    fields: list[DtoField] = []
    body = re.sub(r'@\w+(?:\s*\([^()]*(?:\([^()]*\)[^()]*)*\))?', '', body, flags=re.DOTALL)
    for m in re.finditer(
        r'(?:private|protected|public)\s+(?:final\s+)?([\w.<>,?\[\]\s]+?)\s+(\w+)\s*(?:=\s*[^;]+)?;',
        body,
    ):
        type_str = " ".join(m.group(1).split())
        # 메서드는 다음 토큰이 `(`이지만 `;`로 종료되는 필드만 매치하므로 메서드 제외됨
        fields.append(DtoField(name=m.group(2), type=type_str))
    return fields


def scan_dtos(module: str) -> list[DtoEntry]:
    base = REPO_ROOT / module / "src" / "main" / "java"
    if not base.exists():
        return []

    results: list[DtoEntry] = []
    files = sorted(
        base.rglob("*.java"),
        key=lambda p: p.name.lower(),
    )
    for f in files:
        parts = [p for p in f.parts]
        if "presentation" not in parts:
            continue
        # presentation/dto/... 경로만
        try:
            idx = parts.index("presentation")
        except ValueError:
            continue
        if idx + 1 >= len(parts) or parts[idx + 1] != "dto":
            continue
        if "test" in {p.lower() for p in parts}:
            continue

        raw = f.read_text(encoding="utf-8")
        content = mask_strings(strip_comments(raw))

        # record 우선
        rec = re.search(
            r'public\s+record\s+(\w+)\s*\((.*?)\)\s*(?:implements[^{]+)?\{',
            content,
            re.DOTALL,
        )
        if rec:
            src = rel_source(f)
            results.append(
                DtoEntry(
                    module=module,
                    name=rec.group(1),
                    kind="record",
                    group=_dto_group(src),
                    source=src,
                    fields=_parse_record_fields(rec.group(2)),
                )
            )
            continue

        cls = re.search(
            r'public\s+class\s+(\w+)[^{]*\{(.*)\}',
            content,
            re.DOTALL,
        )
        if cls:
            src = rel_source(f)
            results.append(
                DtoEntry(
                    module=module,
                    name=cls.group(1),
                    kind="class",
                    group=_dto_group(src),
                    source=src,
                    fields=_parse_class_fields(cls.group(2)),
                )
            )
    # scan 단계는 파일 발견 순서 유지. 최종 정렬은 렌더러에서 수행
    # (MD는 이름 오름차순, JSON은 그룹별 + 이름).
    return results


# ---------------------------------------------------------------------------
# Service 스캔
# ---------------------------------------------------------------------------

@dataclass
class ServiceEntry:
    module: str
    service: str
    method: str
    description: str
    source: str


def scan_services(module: str) -> list[ServiceEntry]:
    base = REPO_ROOT / module / "src" / "main" / "java"
    if not base.exists():
        return []

    results: list[ServiceEntry] = []
    files = sorted(
        base.rglob("*.java"),
        key=lambda p: p.name.lower(),
    )
    for f in files:
        parts = [p for p in f.parts]
        if "application" not in parts or "service" not in parts:
            continue
        if "test" in {p.lower() for p in parts}:
            continue

        raw = f.read_text(encoding="utf-8")
        content = mask_strings(strip_comments(raw))

        is_interface = bool(re.search(r'public\s+interface\s+\w+', content))
        class_match = re.search(r'public\s+(?:class|interface)\s+(\w+)', content)
        if not class_match:
            continue
        service_name = class_match.group(1)

        # class: `public ReturnType methodName(...)`
        # interface: 수식어 없이 `ReturnType methodName(...);`
        if is_interface:
            method_iter = re.finditer(
                r'(?:@\w+(?:\s*\([^)]*\))?\s*)*'
                r'(?:(?:public|default|static|abstract)\s+)*'
                r'([\w<>,\s?\[\]]+?)\s+(\w+)\s*\([^)]*\)\s*(?:throws\s+[\w.,\s]+)?\s*;',
                content,
            )
        else:
            method_iter = re.finditer(
                r'(?<!\.)\bpublic\s+'
                r'(?:(?:final|static|synchronized)\s+)*'
                r'(?!class\b|record\b|interface\b|enum\b)'
                r'([\w<>,\s?\[\]]+?)\s+(\w+)\s*\(',
                content,
            )

        seen: set[tuple[str, str]] = set()
        for m in method_iter:
            return_type = m.group(1).strip()
            method_name = m.group(2)
            if method_name == service_name:
                continue  # 생성자 제외
            if return_type in {"class", "record", "interface", "enum"}:
                continue
            if (service_name, method_name) in seen:
                continue
            seen.add((service_name, method_name))
            results.append(
                ServiceEntry(
                    module=module,
                    service=service_name,
                    method=method_name,
                    description=f"{camel_to_words(method_name)} 기능을 제공",
                    source=rel_source(f),
                )
            )
    return results


# ---------------------------------------------------------------------------
# 렌더러
# ---------------------------------------------------------------------------

def render_api_md(entries: list[ApiEntry]) -> str:
    lines: list[str] = [
        "# API 문서 요약",
        "",
        "자동 생성 기준: `*Controller.java`의 RequestMapping/메서드 매핑을 기반으로 정리했습니다.",
        "",
    ]
    by_module: dict[str, list[ApiEntry]] = {}
    for e in entries:
        by_module.setdefault(e.module, []).append(e)
    for mod in sorted(by_module.keys()):
        lines.append(f"## {mod}")
        lines.append("")
        lines.append("| HTTP | Path | Controller#Method | 설명 |")
        lines.append("|---|---|---|---|")
        # MD 는 path 알파벳 순 (원본 동작 재현)
        bucket = sorted(
            by_module[mod],
            key=lambda x: (x.path, x.httpMethod, x.controller, x.method),
        )
        for e in bucket:
            lines.append(
                f"| {e.httpMethod} | `{e.path}` | `{e.controller}#{e.method}` | {e.summary} |"
            )
        lines.append("")
    return "\n".join(lines) + "\n"


def render_api_json(entries: list[ApiEntry]) -> str:
    # JSON 은 모듈 → 컨트롤러 파일 알파벳 → 파일 내 선언 순서
    data = [
        {
            "module": e.module,
            "controller": e.controller,
            "method": e.method,
            "httpMethod": e.httpMethod,
            "path": e.path,
            "summary": e.summary,
            "source": e.source,
        }
        for e in sorted(entries, key=lambda x: (x.module, x.source, x.order))
    ]
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def render_dto_md(entries: list[DtoEntry]) -> str:
    lines: list[str] = [
        "# DTO 문서 요약",
        "",
        "자동 생성 기준: `presentation/dto` 하위 Java `record/class`를 기준으로 정리했습니다.",
        "",
    ]
    by_module: dict[str, list[DtoEntry]] = {}
    for e in entries:
        by_module.setdefault(e.module, []).append(e)
    for mod in sorted(by_module.keys()):
        lines.append(f"## {mod}")
        lines.append("")
        # MD 는 단순 이름 알파벳 순 (원본 Codex 스크립트 동작 재현)
        for dto in sorted(by_module[mod], key=lambda d: d.name.lower()):
            lines.append(f"### {dto.name} ({dto.kind})")
            lines.append(f"- source: `{dto.source}`")
            lines.append("| 필드명 | 타입 |")
            lines.append("|---|---|")
            for fd in dto.fields:
                lines.append(f"| `{fd.name}` | `{fd.type}` |")
            lines.append("")
    return "\n".join(lines) + "\n"


def render_dto_json(entries: list[DtoEntry]) -> str:
    # 원본 정렬: 모듈 → 파일의 parent 디렉터리 경로 → 이름
    # (admin 은 `dto/req` / `dto/res` 분리, event 는 `dto/` / `dto/internal/` 분리)
    def _key(e: DtoEntry) -> tuple:
        parent = e.source.rsplit("/", 1)[0] if "/" in e.source else ""
        return (e.module, parent, e.name.lower())

    data = [
        {
            "module": e.module,
            "name": e.name,
            "kind": e.kind,
            "fieldCount": len(e.fields),
            "fields": [{"name": f.name, "type": f.type} for f in e.fields],
            "source": e.source,
        }
        for e in sorted(entries, key=_key)
    ]
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def render_service_md(entries: list[ServiceEntry]) -> str:
    lines: list[str] = [
        "# 구현된 서비스 현황 (메서드별 1줄 요약)",
        "",
    ]
    by_pair: dict[tuple[str, str], list[ServiceEntry]] = {}
    for e in entries:
        by_pair.setdefault((e.module, e.service), []).append(e)
    for (mod, svc) in sorted(by_pair.keys()):
        lines.append(f"## {mod} / {svc}")
        lines.append("")
        for e in by_pair[(mod, svc)]:
            lines.append(f"- `{e.method}`: {e.description}.")
        lines.append("")
    return "\n".join(lines) + "\n"


def render_service_json(entries: list[ServiceEntry]) -> str:
    data = [
        {
            "module": e.module,
            "service": e.service,
            "method": e.method,
            "description": e.description,
            "source": e.source,
        }
        for e in sorted(entries, key=lambda x: (x.module, x.service, x.method))
    ]
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


# ---------------------------------------------------------------------------
# 메인
# ---------------------------------------------------------------------------

def gather() -> tuple[list[ApiEntry], list[DtoEntry], list[ServiceEntry]]:
    apis: list[ApiEntry] = []
    dtos: list[DtoEntry] = []
    services: list[ServiceEntry] = []
    for mod in MODULES:
        apis.extend(scan_controllers(mod))
        dtos.extend(scan_dtos(mod))
        services.extend(scan_services(mod))
    return apis, dtos, services


OUTPUTS = [
    ("api-summary.md", render_api_md, "apis"),
    ("api-summary.json", render_api_json, "apis"),
    ("dto-summary.md", render_dto_md, "dtos"),
    ("dto-summary.json", render_dto_json, "dtos"),
    ("service-status.md", render_service_md, "services"),
    ("service-status.json", render_service_json, "services"),
]


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="diff만 출력 (쓰기 안 함)")
    args = ap.parse_args(argv)

    apis, dtos, services = gather()
    sources = {"apis": apis, "dtos": dtos, "services": services}

    changed = 0
    for fname, renderer, key in OUTPUTS:
        new_content = renderer(sources[key])
        target = DOCS_DIR / fname
        old_content = target.read_text(encoding="utf-8") if target.exists() else ""
        if new_content == old_content:
            print(f"  = {fname}")
            continue
        changed += 1
        if args.check:
            print(f"  ! {fname} (would change, +/-{abs(len(new_content) - len(old_content))} chars)")
        else:
            target.write_text(new_content, encoding="utf-8")
            print(f"  * {fname} (written)")

    print(f"\n모듈 {len(MODULES)}개 스캔: controllers={len(apis)}, dtos={len(dtos)}, services={len(services)}")
    if args.check:
        return 1 if changed else 0
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
