"""The repository must not whitelist secrets just to certify public Git revisions."""

import importlib.util
import json
from pathlib import Path


def test_only_fixed_control_record_git_sha_fields_bypass_entropy_findings(tmp_path):
    script = Path(__file__).resolve().parents[2] / ".github/scripts/filter_scan_findings.py"
    spec = importlib.util.spec_from_file_location("pink_secret_scan_filter", script)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    folder = tmp_path / ".project-leader/transitions"
    folder.mkdir(parents=True)
    sha = "a" * 40  # pragma: allowlist secret -- synthetic public Git revision
    for key in ("exact_source_head", "code_implementation_sha", "main_merge_sha"):
        filename = f".project-leader/transitions/{key}.json"
        (tmp_path / filename).write_text(json.dumps({key: sha}, indent=2))
        assert module.is_control_sha_metadata(tmp_path, filename, 2, "Hex High Entropy String")
        assert not module.is_control_sha_metadata(tmp_path, filename, 2, "Secret Keyword")

    filename = ".project-leader/transitions/private.json"
    (tmp_path / filename).write_text(
        json.dumps({"private_key": sha, "session_token": sha}, indent=2)
    )
    assert not module.is_control_sha_metadata(tmp_path, filename, 2, "Hex High Entropy String")
    assert not module.is_control_sha_metadata(tmp_path, filename, 3, "Hex High Entropy String")

    bad = tmp_path / "backend/noncontrol.json"
    bad.parent.mkdir(parents=True)
    bad.write_text(json.dumps({"main_merge_sha": sha}, indent=2))
    assert not module.is_control_sha_metadata(
        tmp_path, "backend/noncontrol.json", 2, "Hex High Entropy String"
    )
