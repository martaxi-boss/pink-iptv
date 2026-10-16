"""Read-only final closeout inventory of expirable GitHub Actions artifacts."""
import hashlib
import json
import os
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

REPO = "martaxi-boss/pink-iptv"
FINAL_SHA = "d48126cb8b95da3ea76646fbb8bf3de033216167"
FINAL_QA5_ARTIFACT = 12014905310
KEEP_RUNS = {38822702488, 37828004831, 38831345086, 38831345060, 38833171334, 38890437279}
CUTOFF = "2026-10-15T00:00:00Z"
EXPECTED_CANDIDATE_HASH = "9d8ced884e3924dffd5a5e498ae3f830ae9b71b19faa7f3561716b6f2796d774"
AUTH_FILE = ".project-leader/transitions/PINK-IPTV-ARTIFACT-HYGIENE-20261016.authorization.json"
ARCHIVE = "archive/legacy-branches-20261016"

def api(path, method="GET"):
    req = Request("https://api.github.com/repos/" + REPO + "/" + path,
                  method=method, headers={"Authorization": "Bearer " + os.environ["GH_TOKEN"],
                           "Accept": "application/vnd.github+json",
                           "X-GitHub-Api-Version": "2022-11-28"})
    with urlopen(req, timeout=30) as response:
        return None if response.status == 204 else json.load(response)

def run():
    assert api("git/ref/heads/main")["object"]["sha"] == FINAL_SHA, "Final main changed"
    all_items = []
    total_reported = None
    for page in range(1, 31):
        data = api(f"actions/artifacts?per_page=100&page={page}")
        if total_reported is None:
            total_reported = data["total_count"]
        items = data["artifacts"]
        all_items.extend(items)
        if len(items) < 100:
            break
    else:
        raise RuntimeError("Artifact pagination exceeds bounded 3000-item audit")
    assert len({x["id"] for x in all_items}) == len(all_items)
    assert any(x["id"] == FINAL_QA5_ARTIFACT and not x["expired"] for x in all_items), "Owner QA5 artifact missing"
    candidates, preserved = [], []
    for item in all_items:
        info = {
            "id": item["id"], "name": item["name"], "size_in_bytes": item["size_in_bytes"],
            "created_at": item["created_at"], "run_id": (item.get("workflow_run") or {}).get("id")
        }
        if item["expired"] or item["created_at"] >= CUTOFF or info["run_id"] in KEEP_RUNS:
            preserved.append(info)
        else:
            candidates.append(info)
    result = {
        "mode": "read_only",
        "repo": REPO,
        "final_main": FINAL_SHA,
        "total_reported": total_reported,
        "total_listed": len(all_items),
        "candidate_count": len(candidates),
        "candidate_size_bytes": sum(x["size_in_bytes"] for x in candidates),
        "preserved_count": len(preserved),
        "preserved_size_bytes": sum(x["size_in_bytes"] for x in preserved),
        "qa5_artifact_preserved": FINAL_QA5_ARTIFACT,
        "original_working_apk_run_preserved": 37828004831,
        "cutoff_utc": CUTOFF,
        "candidates": candidates
    }
    Path("pink-artifact-inventory.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({k:v for k,v in result.items() if k != "candidates"}, indent=2))
    return result


def execute():
    """Delete only hash-authorized obsolete artifact IDs from this one repository."""
    assert os.environ.get("GITHUB_REF") == "refs/heads/" + ARCHIVE
    assert api("git/ref/heads/" + ARCHIVE)["object"]["sha"] == os.environ.get("GITHUB_SHA")
    auth = json.loads(Path(AUTH_FILE).read_text())
    assert auth["repository"] == REPO
    assert auth["action"] == "delete_exact_archived_legacy_test_artifacts"
    assert auth["authority"]["source"] == "CURRENT_OWNER_INSTRUCTION"
    assert auth["authority"]["binding_mode"] == "EXACT_REVISION_BOUND"
    target = auth["target"]
    assert target["final_main_sha"] == FINAL_SHA
    assert target["qa5_artifact_id_preserved"] == FINAL_QA5_ARTIFACT
    assert target["cutoff_utc"] == CUTOFF
    assert sorted(target["keep_run_ids"]) == sorted(KEEP_RUNS)
    assert target["candidate_count"] == 271
    assert target["candidate_size_bytes"] == 16442821416
    assert target["candidates_sha256"] == EXPECTED_CANDIDATE_HASH

    evidence = run()
    candidates = sorted(evidence["candidates"], key=lambda x: x["id"])
    digest = hashlib.sha256(json.dumps(candidates, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    assert digest == EXPECTED_CANDIDATE_HASH
    assert evidence["candidate_count"] == 271
    assert evidence["candidate_size_bytes"] == 16442821416
    assert all(x["created_at"] < CUTOFF and x["run_id"] not in KEEP_RUNS
               and x["name"].startswith("PINK-") and x["id"] != FINAL_QA5_ARTIFACT
               for x in candidates), "Out-of-scope artifact in exact manifest"
    assert api("actions/artifacts/" + str(FINAL_QA5_ARTIFACT))["expired"] is False

    result = {
        "mode": "exact_owner_authorized_cleanup", "repository": REPO,
        "main": FINAL_SHA, "manifest_sha256": digest, "expected_count": len(candidates),
        "expected_size_bytes": evidence["candidate_size_bytes"],
        "deleted_ids": [], "deleted_size_bytes": 0, "already_absent_ids": []
    }
    output = Path("pink-artifact-cleanup-result.json")
    def save():
        output.write_text(json.dumps(result, indent=2) + "\n")
    save()
    for index, item in enumerate(candidates):
        if index % 50 == 0:
            assert api("git/ref/heads/main")["object"]["sha"] == FINAL_SHA
            assert api("git/ref/heads/" + ARCHIVE)["object"]["sha"] == os.environ["GITHUB_SHA"]
        path = "actions/artifacts/" + str(item["id"])
        try:
            detail = api(path)
        except HTTPError as error:
            if error.code == 404:
                result["already_absent_ids"].append(item["id"])
                save()
                continue
            raise
        assert detail["id"] == item["id"] and detail["name"] == item["name"]
        assert detail["size_in_bytes"] == item["size_in_bytes"]
        assert detail["created_at"] == item["created_at"]
        assert (detail.get("workflow_run") or {}).get("id") == item["run_id"]
        assert not detail["expired"]
        # A 204 from the scoped GitHub API is authoritative deletion confirmation.
        api(path, "DELETE")
        result["deleted_ids"].append(item["id"])
        result["deleted_size_bytes"] += item["size_in_bytes"]
        save()
    assert api("git/ref/heads/main")["object"]["sha"] == FINAL_SHA
    qa5 = api("actions/artifacts/" + str(FINAL_QA5_ARTIFACT))
    assert qa5["id"] == FINAL_QA5_ARTIFACT and not qa5["expired"]
    result["qa5_preserved"] = True
    result["final_state"] = "CLEANED" if len(result["deleted_ids"]) + len(result["already_absent_ids"]) == len(candidates) else "INCOMPLETE"
    save()
    print(json.dumps({k:v for k,v in result.items() if k != "deleted_ids"}, indent=2))
    assert result["final_state"] == "CLEANED"


if __name__ == "__main__":
    execute() if os.environ.get("PINK_ARTIFACT_DELETE") == "1" else run()
