# PINK Backend

The Order 001 text below describes the historical foundation. The current backend also
supports authenticated device VPN enrollment/refresh/revocation and bounded Mega
username discovery. The backend does not relay IPTV video. Public launch: NO.

## Stack

- Python 3.12
- FastAPI
- PostgreSQL
- SQLAlchemy 2.x
- Alembic
- HTTPX
- Pydantic settings
- pytest
- Ruff

Dependencies are pinned in `pyproject.toml`.

## Environment

Runtime configuration comes only from environment variables:

- `APP_ENV`
- `DATABASE_URL`
- `SESSION_SIGNING_KEY`
- `MEGA_OTT_API_BASE`
- `MEGA_OTT_API_TOKEN`
- `SESSION_TTL_SECONDS`

The application does not automatically load `.env` files. `SESSION_SIGNING_KEY=change-me` is rejected outside tests. Mega imports fail explicitly when the Mega token is absent/default.

## Database

Apply the PostgreSQL migration from `backend/`:

```bash
alembic upgrade head
```

The `subscription_mappings` table stores Mega subscription id, exact username, authoritative `dns_link`, optional Samsung/LG link, expiry, and timestamps. It never stores the customer password, Mega token, or VPN keys.

## Bootstrap an existing line

The only Order 001 bootstrap mechanism is retrieve-by-ID:

```bash
pink-backend import-subscription --id <mega_subscription_id>
```

The command fetches `GET /v1/subscriptions/{id}`, requires an M3U subscription, checks the returned id, and upserts the mapping. Output contains only the subscription id, internal mapping id, SHA-256 username evidence, SHA-256 dns-link evidence, and the `http`/`https` scheme. It never prints the Mega raw payload or password.

Current runtime behavior after Order 001: when a submitted username is not locally
mapped, the backend scans a bounded number (at most 100) of paged
`GET /v1/subscriptions` responses for the exact username, then fetches
`GET /v1/subscriptions/{id}` and imports the authoritative origin.
This is a current-code observation, not independent proof of official endpoint
availability for every Mega installation. It never invents a username-filter
endpoint or guesses a provider host.

## Session resolve

`POST /v1/session/resolve` accepts exactly:

```json
{"username":"...","password":"..."}
```

The local username mapping selects the exact stored `dns_link`. The customer password exists only for the request and is sent to that host's `player_api.php`. Responses use only:

- `SUCCESS`
- `INVALID_CREDENTIALS`
- `EXPIRED`
- `DISABLED`
- `DNS_UNREACHABLE`
- `UPSTREAM_ERROR`

Unknown usernames and wrong passwords both return `INVALID_CREDENTIALS`. Every response carries `Cache-Control: no-store`.

On success, a signed session token is issued for at most five minutes. Its subject references only the internal mapping id; it carries no Mega id, username, customer password, or provider token.

## Live proof

Live proof is deliberately separate from CI. With an Owner-authorized test line and secrets injected only through the execution environment:

1. Run the import command with the known Mega subscription id.
2. Compare the reported hashes/scheme with a secure independently computed evidence set.
3. Call `/v1/session/resolve` with the authorized username/password and require `SUCCESS`.
4. Repeat with a wrong password and require `INVALID_CREDENTIALS`.

Do not paste credentials into shell history, fixtures, GitHub Actions, issue/PR text, screenshots, or logs.

## Current login abuse controls (code pending integration)

The post-foundation backend uses PostgreSQL-backed global and keyed-per-username
fixed-window budgets before Mega/Xtream calls. All worker processes share an
advisory transaction lock and bucket table; raw usernames/passwords/IP addresses
are not retained there. Exhaustion yields a generic HTTP 429, `Retry-After`
and `Cache-Control: no-store` for mapped and unmapped usernames alike.
Environment settings: `AUTH_RATE_WINDOW_SECONDS` (60),
`AUTH_RATE_PER_USERNAME` (10) and `AUTH_RATE_GLOBAL` (120).
**Run `alembic upgrade head` before deploying this code**, with rollback
authorization/verification. No deployment is performed by the repository PR.
Additional endpoint-specific VPN/edge controls and measured production capacity
remain separate security requirements.
