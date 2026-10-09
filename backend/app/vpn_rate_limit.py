"""Shared PostgreSQL VPN-control request rate budgets, independent of login budgets."""

import hmac
from datetime import UTC, datetime

from fastapi import HTTPException
from sqlalchemy import delete, text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.config import Settings
from app.models import VpnRateWindow

_VPN_RATE_LOCK = 5066053
_GLOBAL = "global"
_HEADERS = {"Cache-Control": "no-store", "X-Pink-Vpn-Admission": "throttle"}


def charge_vpn_attempt(
    db: Session,
    settings: Settings,
    credential: str | None = None,
    *,
    now: datetime | None = None,
) -> None:
    """Global pre-parse admission if no credential; keyed admission after parsing.

    A short-lived PINK JWT or installation device token is hashed and never
    persisted or echoed. Backend failures deny rather than switch to local state.
    """

    current = now or datetime.now(UTC)
    if current.tzinfo is None or current.utcoffset() is None:
        raise ValueError("VPN budget requires an aware UTC clock")
    window_seconds = settings.vpn_rate_window_seconds
    timestamp = int(current.timestamp())
    window_number = timestamp // window_seconds
    retry_after = max(1, (window_number + 1) * window_seconds - timestamp)

    try:
        dialect = db.get_bind().dialect.name
        if dialect == "postgresql":
            db.execute(
                text("SELECT pg_advisory_xact_lock(:lock_id)"),
                {"lock_id": _VPN_RATE_LOCK},
            )
        elif settings.app_env.casefold() != "test":
            raise RuntimeError("Production VPN rate admission requires PostgreSQL")

        shared = db.get(VpnRateWindow, _GLOBAL)
        if shared is None:
            shared = VpnRateWindow(bucket_key=_GLOBAL, window_number=window_number, attempts=0)
            db.add(shared)
        elif shared.window_number != window_number:
            db.execute(delete(VpnRateWindow).where(VpnRateWindow.bucket_key != _GLOBAL))
            shared.window_number = window_number
            shared.attempts = 0

        if credential is None:
            blocked = shared.attempts >= settings.vpn_rate_global
            if not blocked:
                shared.attempts += 1
        else:
            digest = hmac.new(
                settings.session_signing_key.get_secret_value().encode(),
                credential.encode(),
                "sha256",
            ).hexdigest()
            bucket = db.get(VpnRateWindow, "c:" + digest)
            if bucket is None:
                bucket = VpnRateWindow(
                    bucket_key="c:" + digest, window_number=window_number, attempts=0
                )
                db.add(bucket)
            blocked = bucket.attempts >= settings.vpn_rate_per_credential
            if not blocked:
                bucket.attempts += 1
        db.commit()
    except (SQLAlchemyError, RuntimeError):
        db.rollback()
        raise HTTPException(
            status_code=503,
            detail="PINK connection temporarily unavailable",
            headers={"Cache-Control": "no-store"},
        ) from None

    if blocked:
        raise HTTPException(
            status_code=429,
            detail="PINK connection temporarily unavailable",
            headers={**_HEADERS, "Retry-After": str(retry_after)},
        )
