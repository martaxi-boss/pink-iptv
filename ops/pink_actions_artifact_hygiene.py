"""Read-only final closeout inventory of expirable GitHub Actions artifacts."""
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen

REPO = "martaxi-boss/pink-iptv"
FINAL_SHA = "d48126cb8b95da3ea76646fbb8bf3de033216167"
FINAL_QA5_ARTIFACT = 12014905310
KEEP_RUNS = {38822702488, 37828004831, 38831345086, 38831345060, 38833171334, 38890437279}
CUTOFF = "2026-10-15T00:00:00Z"

def api(path):
    req = Request("https://api.github.com/repos/" + REPO + "/" + path,
                  headers={"Authorization": "Bearer " + os.environ["GH_TOKEN"],
                           "Accept": "application/vnd.github+json",
                           "X-GitHub-Api-Version": "2022-11-28"})
    with urlopen(req, timeout=30) as response:
        return json.load(response)

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

if __name__ == "__main__":
    run()
