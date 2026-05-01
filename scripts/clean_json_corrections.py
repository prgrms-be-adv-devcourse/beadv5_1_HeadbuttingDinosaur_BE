"""
Remove `corrections` arrays and 정정/이력-related notes from JSON assets.
"""
import json
from pathlib import Path

ROOT = Path(__file__).parent.parent

JSON_FILES = [
    ROOT / "docs/api/api-overview.json",
    ROOT / "docs/dto/dto-overview.json",
] + list((ROOT / "docs/api/summary").glob("*.json")) + list((ROOT / "docs/dto/summary").glob("*.json"))

DROP_NOTE_KEYWORDS = [
    "정정", "이력", "이전 자동", "회귀", "drift", "Drift", "DRIFT",
    "잡음", "폐기", "deprecated", "재정정",
]

for path in JSON_FILES:
    if not path.exists():
        continue
    data = json.loads(path.read_text(encoding="utf-8"))
    changed = False
    if isinstance(data, dict):
        if "corrections" in data:
            del data["corrections"]
            changed = True
        if "notes" in data:
            kept = [n for n in data["notes"] if not any(k in n for k in DROP_NOTE_KEYWORDS)]
            if len(kept) != len(data["notes"]):
                data["notes"] = kept
                changed = True
    if changed:
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"  cleaned: {path.relative_to(ROOT)}")
    else:
        print(f"  no change: {path.relative_to(ROOT)}")
