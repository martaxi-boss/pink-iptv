from datetime import UTC, datetime, timedelta

import jwt

from app.config import Settings


def issue_session_token(
    mapping_id: int,
    settings: Settings,
) -> tuple[str, datetime]:
    now = datetime.now(UTC)
    expires_at = now + timedelta(seconds=settings.session_ttl_seconds)
    payload = {
        "sub": f"mapping:{mapping_id}",
        "iat": now,
        "exp": expires_at,
        "iss": "pink-iptv",
    }
    token = jwt.encode(
        payload,
        settings.session_signing_key.get_secret_value(),
        algorithm="HS256",
    )
    return token, expires_at
