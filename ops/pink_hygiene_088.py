"""Bounded ref maintenance; public evidence only, never rewrite history."""
import json
import os
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen

REPO = "martaxi-boss/pink-iptv"
BASE = "a6c5b7e73b2d7014b927acd47b9f4c2d633ad7c7"  # pragma: allowlist secret -- public Git commit identifier
TASK = "PINK-IPTV-REPOSITORY-HYGIENE-088"


def api(path, method="GET"):
    request = Request("https://api.github.com/repos/" + REPO + "/" + path,
                      method=method, headers={"Authorization": "Bearer " + os.environ["GH_TOKEN"],
                      "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"})
    with urlopen(request, timeout=30) as response:
        return json.load(response) if response.status != 204 else None


def protected():
    pulls = api("pulls?state=open&per_page=100")
    assert len(pulls) < 100, "Bounded inventory exceeded"
    heads = {p["head"]["ref"] for p in pulls if p["head"]["repo"]["full_name"] == REPO}
    for status in ("queued", "in_progress", "waiting"):
        runs = api("actions/runs?status=" + status + "&per_page=100")["workflow_runs"]
        assert len(runs) < 100, "Bounded inventory exceeded"
        heads.update(r["head_branch"] for r in runs)
    return heads | {"main", "builder/extreme-live-certification-055", "builder/repository-hygiene-057"}


def inventory():
    main = api("git/ref/heads/main")["object"]["sha"]
    assert main == BASE, "Main changed; reconstruct before cleanup"
    branches = api("branches?per_page=100")
    assert len(branches) < 100, "Bounded inventory exceeded"
    exclude = protected()
    candidates, preserved = [], []
    for branch in branches:
        name, sha = branch["name"], branch["commit"]["sha"]
        if name in exclude or name.startswith("control/"):
            preserved.append({"branch": name, "reason": "protected or active"})
            continue
        comparison = api("compare/" + sha + "..." + BASE)
        if comparison["merge_base_commit"]["sha"] == sha and comparison["status"] in ("ahead", "identical"):
            candidates.append({"branch": name, "sha": sha})
        else:
            preserved.append({"branch": name, "reason": "not proven reachable from main"})
    evidence = {"main_sha": main, "candidates": candidates, "preserved": preserved}
    Path("pink-hygiene-057-inventory.json").write_text(json.dumps(evidence, indent=2) + "\n")
    print(json.dumps(evidence, indent=2))


def execute():
    assert api("git/ref/heads/main")["object"]["sha"] == BASE
    exclude = protected()
    root = Path(".project-leader/transitions")
    records = sorted(root.glob(TASK + "-DELETE-*.authorization.json"))
    assert 0 < len(records) <= 60
    deleted = []
    for path in records:
        record = json.loads(path.read_text())
        assert record["repository"] == REPO and record["task_id"] == TASK
        assert record["action"] == "delete_fully_merged_non_main_branch_ref"
        assert record["authority"]["source"] == "CURRENT_OWNER_INSTRUCTION"
        assert record["authority"]["binding_mode"] == "EXACT_REVISION_BOUND"
        name, sha = record["target"]["identifier"], record["target"]["revision"]
        assert record["target"]["base_revision"] == BASE and name not in exclude and not name.startswith("control/")
        ref_path = "git/ref/heads/" + quote(name, safe="/")
        assert api(ref_path)["object"]["sha"] == sha, "Ref changed; preserve it"
        assert api("compare/" + sha + "..." + BASE)["merge_base_commit"]["sha"] == sha
        # Recheck active work immediately before the effect.
        assert name not in protected()
        api("git/refs/heads/" + quote(name, safe="/"), "DELETE")
        try:
            api(ref_path)
        except HTTPError as error:
            assert error.code == 404
        else:
            raise AssertionError("Deleted ref still exists")
        deleted.append({"transition_id": record["transition_id"], "branch": name, "sha": sha})
    assert api("git/ref/heads/main")["object"]["sha"] == BASE
    evidence = {"main_sha": BASE, "deleted": deleted, "commit_history_preserved": True}
    Path("pink-hygiene-057-result.json").write_text(json.dumps(evidence, indent=2) + "\n")
    print(json.dumps(evidence, indent=2))


if __name__ == "__main__":
    execute() if os.environ.get("PINK_HYGIENE_EXECUTE") == "1" else inventory()
