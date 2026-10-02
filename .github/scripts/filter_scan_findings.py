#!/usr/bin/env python3
import json
import sys
from pathlib import Path

CONTROL_RECORD_PREFIXES = (
    ".project-leader/tasks/",
    ".project-leader/results/",
    ".project-leader/checkpoints/",
    ".project-leader/recovery-events/",
)
SHA_FIELDS = {
    "base_sha",
    "implementation_head_sha",
    "sha256",
    "previous_event_sha256",
}
HEX_CHARS = frozenset("0123456789abcdef")


def is_control_sha_metadata(
    repository_root: Path,
    path: str,
    line_number: object,
    finding_type: object,
) -> bool:
    if finding_type != "Hex High Entropy String":
        return False

    normalized = path.removeprefix("./")
    if not normalized.endswith(".json"):
        return False
    if not any(normalized.startswith(prefix) for prefix in CONTROL_RECORD_PREFIXES):
        return False
    if not isinstance(line_number, int) or line_number < 1:
        return False

    source_path = repository_root / normalized
    try:
        source_lines = source_path.read_text().splitlines()
    except (OSError, UnicodeError):
        return False
    if line_number > len(source_lines):
        return False

    source_line = source_lines[line_number - 1].strip()
    if source_line.endswith(","):
        source_line = source_line[:-1]
    if ":" not in source_line:
        return False

    raw_name, raw_value = source_line.split(":", 1)
    try:
        field_name = json.loads(raw_name.strip())
        value = json.loads(raw_value.strip())
    except (json.JSONDecodeError, TypeError):
        return False

    return (
        field_name in SHA_FIELDS
        and isinstance(value, str)
        and len(value) in {40, 64}
        and all(char in HEX_CHARS for char in value)
    )


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("usage: filter_scan_findings.py REPORT_JSON", file=sys.stderr)
        return 2

    report_path = Path(argv[1])
    report = json.loads(report_path.read_text())
    repository_root = Path.cwd()

    blocked: list[tuple[str, object, object]] = []
    allowed: list[tuple[str, object, object]] = []

    for finding_path, items in report.get("results", {}).items():
        for item in items:
            line = item.get("line_number")
            finding_type = item.get("type")
            finding = (finding_path, line, finding_type)
            if is_control_sha_metadata(
                repository_root,
                finding_path,
                line,
                finding_type,
            ):
                allowed.append(finding)
            else:
                blocked.append(finding)

    for finding_path, line, finding_type in allowed:
        print(
            f"allowed control metadata: "
            f"{finding_path}:{line} ({finding_type})"
        )

    if blocked:
        for finding_path, line, finding_type in blocked:
            print(f"potential finding: {finding_path}:{line} ({finding_type})")
        return 1

    print("scan report: no non-metadata findings")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
