import os
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

os.environ.setdefault("APP_ENV", "test")
os.environ.setdefault("DATABASE_URL", "sqlite+pysqlite:///:memory:")
os.environ.setdefault(
    "SESSION_SIGNING_KEY",
    "test-signing-key-32-bytes-minimum-value",  # pragma: allowlist secret
)
os.environ.setdefault("MEGA_OTT_API_BASE", "https://megaott.net/api")
os.environ.setdefault("SESSION_TTL_SECONDS", "300")

from app.config import Settings  # noqa: E402
from app.db import Base  # noqa: E402
from app.models import SubscriptionMapping  # noqa: E402


@pytest.fixture
def settings() -> Settings:
    return Settings(
        app_env="test",
        database_url="sqlite+pysqlite:///:memory:",
        session_signing_key=(
            "test-signing-key-32-bytes-minimum-value"  # pragma: allowlist secret
        ),
        mega_ott_api_base="https://megaott.net/api",
        mega_ott_api_token="test-token",  # pragma: allowlist secret
        session_ttl_seconds=300,
    )


@pytest.fixture
def session_factory() -> sessionmaker[Session]:
    engine = create_engine(
        "sqlite+pysqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    return sessionmaker(
        bind=engine,
        autoflush=False,
        expire_on_commit=False,
    )


@pytest.fixture
def db(session_factory: sessionmaker[Session]):
    with session_factory() as session:
        yield session


@pytest.fixture
def future_mapping(db: Session) -> SubscriptionMapping:
    mapping = SubscriptionMapping(
        mega_subscription_id=121,
        username="authorized-user",
        dns_link="http://stream.example.com",
        dns_link_samsung_lg=None,
        expiring_at=datetime.now(UTC) + timedelta(days=30),
        last_synced_at=datetime.now(UTC),
    )
    db.add(mapping)
    db.commit()
    db.refresh(mapping)
    return mapping


@pytest.fixture
def public_resolver():
    return lambda _hostname, _port: ["93.184.216.34"]


@pytest.fixture
def mega_payload():
    return {
        "type": "M3U",
        "id": 121,
        "username": "authorized-user",
        "password": "provider-password",  # pragma: allowlist secret
        "expiring_at": "2030-02-08 09:02:08 GMT+0000",
        "dns_link": "http://stream.example.com",
        "dns_link_for_samsung_lg": "http://tv.example.com",
    }


@pytest.fixture
def fake_success_result():
    return SimpleNamespace(code="SUCCESS", account_expires_at=None)
