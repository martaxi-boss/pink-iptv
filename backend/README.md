# PINK Backend

Order 001 establishes only the backend proof path. It does not implement Android, Windows, VPN, playback, deployment, or public administration.

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

There is no username-search Mega endpoint in this implementation.

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
