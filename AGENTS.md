# PINK IPTV — Project Leader / Codex coordinated execution

This repository supports **one authenticated Codex session** with up to **two
parallel Codex subagents**, not two separate paid accounts. The principal
Project Leader controls scope, prioritization, validation and acceptance.

## Scheduling: one or two according to the task

- **Automatic mode (default):** use one agent for a small, serial, tightly
  coupled, or single-domain task. Only delegate to two when there are genuinely
  independent Android and backend subtasks with disjoint working paths.
- **Parallel on owner request:** delegate `pink_android` to clients/extreme/,
  android/ or design/, and `pink_backend` to backend/, concurrently. Send each
  a separate, bounded task and a specific expected result. The coordinator
  retains supervision, merges conclusions and runs final validation.
- **Serial on owner request:** run subagents in the order requested. Do not
  spawn a second merely because two slots are available.
- If both tasks require the same files, database, API contract, CI workflow or
  one result depends on the other, **serialize**. Shared files such as
  .github/, shared/, ops/, infra/, AGENTS.md and .codex/ belong solely to the
  coordinator. Subagents must not write them.

## Adaptive intelligence and token economy

- **LOW:** normal quick fixes, clear isolated changes, routine checks.
- **MEDIUM:** multi-step integration, contradictory evidence, or a nontrivial
  failed test with a concrete diagnostic hypothesis.
- **HIGH:** confirmed hard concurrency, security, migration or repeated
  regression cases where the added reasoning is needed.
- Choose the minimum supported `model_reasoning_effort` at spawn time.
  Explicitly raise only for a well-defined harder next step and lower it
  again for routine subsequent work. Never claim that a running model's
  reasoning level or account quota was changed unless the Codex runtime
  actually confirms it.
- Reuse the same checked-out baseline, send bounded prompts, return short
  evidence summaries, avoid duplicate scans and do not re-run an expensive
  CI job when successful exact-head evidence already exists.
- Token-account usage may not be exposed to these agents. If not observable,
  report it as `UNKNOWN`; do **not** invent token figures, cost savings or
  concurrency. Subagents consume additional tokens, so parallelism must buy
  independent progress.

## PINK IPTV safety and acceptance

- Protect the working owner-installed APK, signing identity and VPN key.
  The QA package `com.pinkiptv.extreme.qa` must remain separately installable.
- Live TV, Movies and Series keep their existing links, playlists and player
  behavior. The approved PINK IPTV idle artwork is visual only.
- The VPN production quota is 10 installations per account. Never delete or
  reset real leases to simulate a quota failure.
- Changes to production VPS, credentials, migrations, signing, publishing
  or destructive operations require the Project Leader's material transition
  controls; subagents never perform such operations autonomously.
- Do not claim PASS until the concrete affected tests and required GitHub
  checks pass on the exact implementation. Only the supervisor integrates
  contributions after checking no conflicts or unrelated changes.
