"""Bounded ref maintenance; public evidence only, never rewrite history."""
import json
import os
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen

REPO = "martaxi-boss/pink-iptv"
BASE = "c78800eaf5b57023f23910865232e589a09ee82c"  # SHA of verified 49-branch historical meta-merge
FINAL_MAIN = "d48126cb8b95da3ea76646fbb8bf3de033216167"
ARCHIVE = "archive/legacy-branches-20261016"
MANIFEST_BLOB = "b79014b601a52d9d9b6e8a48b392188e987dcd83"
AUTH_PATH = ".project-leader/transitions/PINK-IPTV-FINAL-20261016.authorization.json"
TASK = "PINK-IPTV-REPOSITORY-HYGIENE-057"


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
    return heads | {"main", ARCHIVE}




def archive_inventory():
    """Prove full Git ancestry and exactly approved branch names, before effects."""
    assert api("git/ref/heads/main")["object"]["sha"] == FINAL_MAIN, "Approved main moved"
    archive_sha = api("git/ref/heads/" + ARCHIVE)["object"]["sha"]
    # The exact 5 meta-merge commits preserve all original branch-tip histories.
    comparison = api("compare/" + BASE + "..." + archive_sha)
    assert comparison["merge_base_commit"]["sha"] == BASE, "Archive anchor not reachable"
    content = api("contents/PINK-IPTV-HISTORICO-20261016.json?ref=" + quote(ARCHIVE, safe=""))
    assert content["sha"] == MANIFEST_BLOB, "Historical manifest was replaced"
    manifest = json.loads(Path("PINK-IPTV-HISTORICO-20261016.json").read_text())
    assert manifest["approved_main_sha"] == FINAL_MAIN
    entries = manifest["entries"]
    assert len(entries) == 49 and len({b["branch"] for b in entries}) == 49
    assert all(b["branch"] not in ("main", ARCHIVE) and
               isinstance(b["sha"], str) and len(b["sha"]) == 40 for b in entries)

    chain = [
        "64f310d510bd389831bcc734b050149da6d521a3",
        "390b47ad4c03e9156084085816d8b399ce86cb72",
        "ff2dbd13dbcd6490ac58e7c3f3b6a922de78d957",
        "5c8e5bd4a28746a978e1513d2979c8d11331a376",
        BASE,
    ]
    unique_heads = list(dict.fromkeys(b["sha"] for b in entries))
    assert len(unique_heads) == 48, "Historical head count changed"
    parents_seen = []
    for i, sha in enumerate(chain):
        commit = api("git/commits/" + sha)
        parents = [p["sha"] for p in commit["parents"]]
        assert commit["tree"]["sha"] == "18bedd835a06951d1f90282360aee5e7f004627e"
        assert parents[0] == (FINAL_MAIN if i == 0 else chain[i-1])
        assert parents[1:] == unique_heads[i*10:(i+1)*10]
        parents_seen.extend(parents[1:])
    assert parents_seen == unique_heads, "Not all branch histories archived"
    return archive_sha, entries


def inventory():
    archive_sha, entries = archive_inventory()
    live = {b["name"]: b for b in api("branches?per_page=100")}
    assert len(live) < 100, "Branch list truncated"
    busy = protected()
    candidates, preserved = [], []
    for b in entries:
        name, sha = b["branch"], b["sha"]
        current = live.get(name)
        if not current:
            preserved.append({"branch": name, "reason": "already absent"})
        elif name in busy or current["protected"] or current["commit"]["sha"] != sha:
            preserved.append({"branch": name, "reason": "active, protected or moved"})
        else:
            candidates.append(b)
    evidence = {"approved_main": FINAL_MAIN, "archive_sha": archive_sha,
                "historical_heads_reachable": 49, "candidates": candidates, "preserved": preserved}
    Path("pink-hygiene-057-inventory.json").write_text(json.dumps(evidence, indent=2) + "\n")
    print(json.dumps(evidence, indent=2))


def execute():
    """Delete only exact old refs, not commits, after Owner-bound archive proof."""
    assert os.environ.get("GITHUB_REF") == "refs/heads/" + ARCHIVE
    archive_sha, entries = archive_inventory()
    assert os.environ.get("GITHUB_SHA") == archive_sha, "Archive advanced after dispatch"
    record = json.loads(Path(AUTH_PATH).read_text())
    assert record["repository"] == REPO
    assert record["action"] == "delete_exact_legacy_branch_refs_after_verified_archive"
    assert record["authority"]["source"] == "CURRENT_OWNER_INSTRUCTION"
    assert record["authority"]["binding_mode"] == "EXACT_REVISION_BOUND"
    assert record["target"]["final_main_sha"] == FINAL_MAIN
    assert record["target"]["archive_ancestor_sha"] == BASE
    assert record["target"]["branches"] == entries
    assert len(entries) == 49
    checked = api("branches?per_page=100")
    assert len(checked) < 100
    protected_at_start = {b["name"] for b in checked if b["protected"]}
    assert "main" in [b["name"] for b in checked] and ARCHIVE in [b["name"] for b in checked]

    result = {"approved_main": FINAL_MAIN, "archive_sha": archive_sha,
              "history_preserved_by_verified_parents": True,
              "authorized_ref_count": len(entries), "deleted": [], "already_absent": [],
              "preserved": []}
    result_path = Path("pink-hygiene-057-result.json")
    def save():
        result_path.write_text(json.dumps(result, indent=2) + "\n")
    save()
    for item in entries:
        name, sha = item["branch"], item["sha"]
        assert name not in ("main", ARCHIVE) and not name.startswith("refs/")
        assert api("git/ref/heads/main")["object"]["sha"] == FINAL_MAIN
        assert api("git/ref/heads/" + ARCHIVE)["object"]["sha"] == archive_sha
        ref_path = "git/ref/heads/" + quote(name, safe="/")
        try:
            ref = api(ref_path)
        except HTTPError as error:
            if error.code == 404:
                result["already_absent"].append(item)
                save()
                continue
            raise
        if name in protected_at_start or name in protected() or ref["object"]["sha"] != sha:
            result["preserved"].append({"branch": name, "reason": "protected, active or ref changed"})
            save()
            continue
        api("git/refs/heads/" + quote(name, safe="/"), "DELETE")
        try:
            api(ref_path)
        except HTTPError as error:
            assert error.code == 404
        else:
            raise AssertionError("Deleted branch ref is still present: " + name)
        result["deleted"].append(item)
        save()
    assert api("git/ref/heads/main")["object"]["sha"] == FINAL_MAIN
    assert api("git/ref/heads/" + ARCHIVE)["object"]["sha"] == archive_sha
    remaining = api("branches?per_page=100")
    assert len(remaining) < 100
    result["remaining_branch_names"] = [b["name"] for b in remaining]
    save()
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    execute() if os.environ.get("PINK_HYGIENE_EXECUTE") == "1" else inventory()
