import pytest
from pydantic import ValidationError

from app.config import Settings


def test_non_test_rejects_default_signing_key() -> None:
    with pytest.raises(ValidationError):
        Settings(
            app_env="development",
            database_url="postgresql+psycopg://example.invalid/db",
            session_signing_key="change-me",  # pragma: allowlist secret
            mega_ott_api_base="https://megaott.net/api",
        )


def test_session_ttl_cannot_exceed_five_minutes() -> None:
    with pytest.raises(ValidationError):
        Settings(
            app_env="test",
            database_url="sqlite+pysqlite:///:memory:",
            session_signing_key=(
                "test-signing-key-32-bytes-minimum-value"  # pragma: allowlist secret
            ),
            session_ttl_seconds=301,
        )


def test_mega_base_requires_https(settings: Settings) -> None:
    data = settings.model_dump()
    data["mega_ott_api_base"] = "http://mega.example.com/api"
    with pytest.raises(ValidationError):
        Settings(**data)


def test_mega_token_required_when_mega_functionality_is_used() -> None:
    settings = Settings(
        app_env="test",
        database_url="sqlite+pysqlite:///:memory:",
        session_signing_key=(
            "test-signing-key-32-bytes-minimum-value"  # pragma: allowlist secret
        ),
        mega_ott_api_token=None,
    )
    with pytest.raises(RuntimeError, match="requires MEGA_OTT_API_TOKEN"):
        settings.require_mega_token()
