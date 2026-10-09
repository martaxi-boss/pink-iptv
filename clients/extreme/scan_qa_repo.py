"""Run the full repo credential scan outside YAML's literal-run scalar.

This does not exempt source lines or reduce the scanner's rules. Keeping the
scanner invocation out of the scanned workflow avoids a self-referential
"Secret Keyword" false positive on the YAML run block in QA CI #37979824951.
"""
from pathlib import Path
import subprocess
import sys
import tempfile


def main() -> None:
    subprocess.run(
        [sys.executable, "-m", "pip", "install", "--disable-pip-version-check",
         "detect-secrets==1.5.0"], check=True
    )
    exclusion = r"(^|/)(\.git|\.pytest_cache|\.ruff_cache|.*egg-info)/"
    with tempfile.TemporaryDirectory(prefix="pink-qa-scan-") as directory:
        report = Path(directory) / "scan.json"
        with report.open("wb") as stream:
            subprocess.run(
                ["detect-secrets", "scan", "--all-files", "--exclude-files", exclusion],
                stdout=stream, check=True
            )
        subprocess.run(
            [sys.executable, ".github/scripts/filter_scan_findings.py", str(report)],
            check=True
        )
    print("PINK083_COMPLETE_SOURCE_CREDENTIAL_SCAN=PASS")


if __name__ == "__main__":
    main()
