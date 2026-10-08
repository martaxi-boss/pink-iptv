"""Cross-worker admission budget before any Mega or Xtream network work.

The PostgreSQL advisory transaction lock serializes requests across API workers.
Bucket identifiers contain a keyed username digest, never the account name.
"""

import hmac
from datetime import UTC, datetime

from fastapi import HTTPException
from sqlalchemy import delete, text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.config import Settings
from app.models import AuthRateWindow

_AUTH_LOCK = 5066052
_DENIAL_HEADERS = {"Cache-Control": "no-store"}
_GLOBAL = "global"


def charge_auth_attempt(
    db: Session,
    settings: Settings,
    username: str,
    *,
    now: datetime | None = None,
) -> None:
    """Limit both repeated identities and distributed username rotation."""

    current = now or datetime.now(UTC)
    if current.tzinfo is None or current.utcoffset() is None:
        raise ValueError("rate limit time must be timezone aware")
    seconds = settings.auth_rate_window_seconds
    timestamp = int(current.timestamp())
    window_number = timestamp // seconds
    retry_after = max(1, (window_number + 1) * seconds - timestamp)
    user_hash = hmac.new(
        settings.session_signing_key.get_secret_value().encode(),
        username.encode(),
        "sha256",
    ).hexdigest()
    user_bucket = "u:" + user_hash

    try:
        dialect = db.get_bind().dialect.name
        if dialect == "postgresql":
            db.execute(text("SELECT pg_advisory_xact_lock(:lock_id)"), {"lock_id": _AUTH_LOCK})
        elif settings.app_env.casefold() != "test":
            # A per-process in-memory fallback would silently bypass this protection.
            raise RuntimeError("Production authentication budget requires PostgreSQL")

        shared = db.get(AuthRateWindow, _GLOBAL)
        if shared is None:
            shared = AuthRateWindow(bucket_key=_GLOBAL, window_number=window_number, attempts=0)
            db.add(shared)
        elif shared.window_number != window_number:
            # At most the previous window's bounded rows survive until rollover.
            db.execute(delete(AuthRateWindow).where(AuthRateWindow.bucket_key != _GLOBAL))
            shared.window_number = window_number
            shared.attempts = 0

        # A saturated global budget must not allocate a row for each attacker-
        # supplied username. Otherwise the limiter itself permits table flooding.
        blocked = shared.attempts >= settings.auth_rate_global
        if not blocked:
            individual = db.get(AuthRateWindow, user_bucket)
            if individual is None:
                individual = AuthRateWindow(
                    bucket_key=user_bucket, window_number=window_number, attempts=0
                )
                db.add(individual)
            blocked = individual.attempts >= settings.auth_rate_per_username
            if not blocked:
                shared.attempts += 1
                individual.attempts += 1
        db.commit()
    except (SQLAlchemyError, RuntimeError):
        db.rollback()
        raise HTTPException(
            status_code=503,
            detail="PINK authentication temporarily unavailable",
            headers=_DENIAL_HEADERS,
        ) from None

    if blocked:
        raise HTTPException(
            status_code=429,
            detail="PINK authentication temporarily unavailable",
            headers={**_DENIAL_HEADERS, "Retry-After": str(retry_after)},
        )
