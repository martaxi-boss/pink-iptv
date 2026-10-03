import importlib.util
import json
from pathlib import Path

import pytest

SCRIPT = Path(__file__).resolve().parents[2] / ".github/scripts/filter_scan_findings.py"
spec = importlib.util.spec_from_file_location("control_scan_filter", SCRIPT)
assert spec is not None and spec.loader is not None
scan_filter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scan_filter)


def write_record(root: Path, path: str, field: str, value: str) -> int:
    destination = root / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps({field: value}, indent=2) + "\n")
    return 2


@pytest.mark.parametrize("suffix", ["authorization", "result"])
@pytest.mark.parametrize("field", ["revision", "base_revision"])
@pytest.mark.parametrize("length", [40, 64])
def test_public_transition_sha_fields(tmp_path, suffix, field, length):
    path = f".project-leader/transitions/PROOF.{suffix}.json"
    line = write_record(tmp_path, path, field, "a" * length)
    assert scan_filter.is_control_sha_metadata(tmp_path, path, line, "Hex High Entropy String")


@pytest.mark.parametrize("field", ["password", "token", "private_key", "ciphertext"])
def test_transition_credentials_remain_blocked(tmp_path, field):
    path = ".project-leader/transitions/PROOF.result.json"
    line = write_record(tmp_path, path, field, "a" * 64)
    assert not scan_filter.is_control_sha_metadata(tmp_path, path, line, "Hex High Entropy String")


@pytest.mark.parametrize("finding_type", ["Secret Keyword", "Base64 High Entropy String"])
def test_other_detector_types_remain_blocked(tmp_path, finding_type):
    path = ".project-leader/transitions/PROOF.result.json"
    line = write_record(tmp_path, path, "revision", "a" * 40)
    assert not scan_filter.is_control_sha_metadata(tmp_path, path, line, finding_type)


@pytest.mark.parametrize("value", ["a" * 39, "a" * 41, "A" * 40, "g" * 40, "not-a-sha"])
def test_invalid_sha_formats_remain_blocked(tmp_path, value):
    path = ".project-leader/transitions/PROOF.result.json"
    line = write_record(tmp_path, path, "revision", value)
    assert not scan_filter.is_control_sha_metadata(tmp_path, path, line, "Hex High Entropy String")


@pytest.mark.parametrize("path", ["backend/source.json", ".project-leader/transitions/proof.txt"])
def test_product_and_non_json_paths_remain_blocked(tmp_path, path):
    line = write_record(tmp_path, path, "revision", "a" * 40)
    assert not scan_filter.is_control_sha_metadata(tmp_path, path, line, "Hex High Entropy String")


@pytest.mark.parametrize("line", [0, -1, 999, "2"])
def test_invalid_source_lines_remain_blocked(tmp_path, line):
    path = ".project-leader/transitions/PROOF.result.json"
    write_record(tmp_path, path, "revision", "a" * 40)
    assert not scan_filter.is_control_sha_metadata(tmp_path, path, line, "Hex High Entropy String")
