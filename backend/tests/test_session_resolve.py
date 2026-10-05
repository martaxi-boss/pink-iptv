from datetime import UTC, datetime, timedelta
from types import SimpleNamespace

import jwt
from fastapi.testclient import TestClient
from sqlalchemy import inspect, select

from app.clients.xtream import XtreamDNSUnreachable, XtreamUpstreamError
from app.main import create_app
from app.models import SubscriptionMapping
from app.schemas import ResolveCode
from app.services.session import SessionResolver


class FakeXtream:
    def __init__(self, result=None, error=None):
        self.result = result
        self.error = error
        self.calls = []

    def authenticate(self, **kwargs):
        self.calls.append(kwargs)
        if self.error:
            raise self.error
        return self.result

    def close(self):
        return None


class FakeMega:
    def __init__(self, *, username=None, subscription_id=121, dns_link="http://auto.example.com"):
        self.username = username
        self.subscription_id = subscription_id
        self.dns_link = dns_link
        self.find_calls = []
        self.get_calls = []
        self.closed = False

    def find_subscription_id_by_username(self, username):
        self.find_calls.append(username)
        if username == self.username:
            return self.subscription_id
        return None

    def get_subscription(self, mega_subscription_id):
        self.get_calls.append(mega_subscription_id)
        if mega_subscription_id != self.subscription_id or self.username is None:
            raise AssertionError("unexpected subscription lookup")
        return SimpleNamespace(
            id=self.subscription_id,
            username=self.username,
            dns_link=self.dns_link,
            dns_link_for_samsung_lg=None,
            expiring_at=datetime.now(UTC) + timedelta(days=30),
        )

    def close(self):
        self.closed = True


def test_success_returns_short_session_without_credentials(
    db,
    future_mapping,
    settings,
) -> None:
    fake = FakeXtream(SimpleNamespace(code="SUCCESS", account_expires_at=None))
    password = "customer-password"  # pragma: allowlist secret
    response = SessionResolver(db, fake, settings).resolve(
        username=future_mapping.username,
        password=password,
    )
    assert response.code == ResolveCode.SUCCESS
    assert response.xtream_base_url == future_mapping.dns_link
    assert response.session_token is not None
    claims = jwt.decode(
        response.session_token,
        settings.session_signing_key.get_secret_value(),
        algorithms=["HS256"],
        issuer="pink-iptv",
    )
    assert claims["sub"] == f"mapping:{future_mapping.id}"
    assert "username" not in claims
    assert "password" not in claims
    assert "mega_subscription_id" not in claims
    assert claims["exp"] - claims["iat"] <= 300
    assert fake.calls[0]["dns_link"] == future_mapping.dns_link
    assert fake.calls[0]["username"] == future_mapping.username
    assert fake.calls[0]["password"] == password


def test_unmapped_username_is_discovered_imported_and_authenticated(
    db,
    settings,
) -> None:
    username = "new-customer"
    password = "new-password"  # pragma: allowlist secret
    authoritative_dns = "http://customer-subdomain.example.com"
    fake_mega = FakeMega(username=username, subscription_id=9040240, dns_link=authoritative_dns)
    fake_xtream = FakeXtream(SimpleNamespace(code="SUCCESS", account_expires_at=None))

    result = SessionResolver(
        db,
        fake_xtream,
        settings,
        mega_client_factory=lambda: fake_mega,
    ).resolve(
        username=username,
        password=password,
    )

    assert result.code == ResolveCode.SUCCESS
    assert result.xtream_base_url == authoritative_dns
    assert fake_mega.find_calls == [username]
    assert fake_mega.get_calls == [9040240]
    assert fake_mega.closed is True
    assert fake_xtream.calls == [
        {
            "dns_link": authoritative_dns,
            "username": username,
            "password": password,
        }
    ]

    mapping = db.scalar(select(SubscriptionMapping).where(SubscriptionMapping.username == username))
    assert mapping is not None
    assert mapping.mega_subscription_id == 9040240
    assert mapping.dns_link == authoritative_dns
    assert "password" not in inspect(SubscriptionMapping).columns


def test_existing_mapping_never_scans_mega(
    db,
    future_mapping,
    settings,
) -> None:
    fake_mega = FakeMega(username=future_mapping.username)
    fake_xtream = FakeXtream(SimpleNamespace(code="SUCCESS", account_expires_at=None))

    result = SessionResolver(
        db,
        fake_xtream,
        settings,
        mega_client_factory=lambda: fake_mega,
    ).resolve(
        username=future_mapping.username,
        password="password",  # pragma: allowlist secret
    )

    assert result.code == ResolveCode.SUCCESS
    assert fake_mega.find_calls == []
    assert fake_mega.get_calls == []
    assert fake_mega.closed is False


def test_unknown_username_and_wrong_password_are_indistinguishable(
    db,
    future_mapping,
    settings,
) -> None:
    unknown_fake = FakeXtream(SimpleNamespace(code="SUCCESS", account_expires_at=None))
    unknown_mega = FakeMega(username=None)
    unknown_password = "anything"  # pragma: allowlist secret
    unknown = SessionResolver(
        db,
        unknown_fake,
        settings,
        mega_client_factory=lambda: unknown_mega,
    ).resolve(
        username="missing",
        password=unknown_password,
    )

    wrong_fake = FakeXtream(
        SimpleNamespace(
            code="INVALID_CREDENTIALS",
            account_expires_at=None,
        )
    )
    wrong_password = "wrong"  # pragma: allowlist secret
    wrong = SessionResolver(db, wrong_fake, settings).resolve(
        username=future_mapping.username,
        password=wrong_password,
    )
    assert (
        unknown.model_dump()
        == wrong.model_dump()
        == {
            "code": ResolveCode.INVALID_CREDENTIALS,
            "session_token": None,
            "session_expires_at": None,
            "xtream_base_url": None,
            "account_expires_at": None,
        }
    )
    assert unknown_fake.calls == []
    assert unknown_mega.find_calls == ["missing"]
    assert unknown_mega.closed is True


def test_local_authoritative_expiry_short_circuits_xtream(
    db,
    settings,
) -> None:
    mapping = SubscriptionMapping(
        mega_subscription_id=500,
        username="expired-user",
        dns_link="https://stream.example.com",
        expiring_at=datetime.now(UTC) - timedelta(seconds=1),
        last_synced_at=datetime.now(UTC),
    )
    db.add(mapping)
    db.commit()
    fake = FakeXtream(SimpleNamespace(code="SUCCESS", account_expires_at=None))
    password = "p"  # pragma: allowlist secret
    result = SessionResolver(db, fake, settings).resolve(
        username="expired-user",
        password=password,
    )
    assert result.code == ResolveCode.EXPIRED
    assert fake.calls == []


def test_explicit_disabled_passes_through(
    db,
    future_mapping,
    settings,
) -> None:
    fake = FakeXtream(SimpleNamespace(code="DISABLED", account_expires_at=None))
    password = "p"  # pragma: allowlist secret
    result = SessionResolver(db, fake, settings).resolve(
        username=future_mapping.username,
        password=password,
    )
    assert result.code == ResolveCode.DISABLED


def test_dns_unreachable_classified(db, future_mapping, settings) -> None:
    fake = FakeXtream(error=XtreamDNSUnreachable("dns failed"))
    password = "p"  # pragma: allowlist secret
    result = SessionResolver(db, fake, settings).resolve(
        username=future_mapping.username,
        password=password,
    )
    assert result.code == ResolveCode.DNS_UNREACHABLE


def test_upstream_error_classified(db, future_mapping, settings) -> None:
    fake = FakeXtream(error=XtreamUpstreamError("bad upstream"))
    password = "p"  # pragma: allowlist secret
    result = SessionResolver(db, fake, settings).resolve(
        username=future_mapping.username,
        password=password,
    )
    assert result.code == ResolveCode.UPSTREAM_ERROR


def test_endpoint_sets_no_store_and_rejects_extra_fields(
    settings,
    session_factory,
) -> None:
    app = create_app(settings)
    app.state.session_factory = session_factory
    app.state.xtream_client_factory = lambda: FakeXtream(
        SimpleNamespace(code="SUCCESS", account_expires_at=None)
    )
    app.state.mega_client_factory = lambda: FakeMega(username=None)
    client = TestClient(app)
    password = "wrong"  # pragma: allowlist secret
    response = client.post(
        "/v1/session/resolve",
        json={"username": "unknown", "password": password},
    )
    assert response.status_code == 200
    assert response.headers["Cache-Control"] == "no-store"
    assert response.json()["code"] == "INVALID_CREDENTIALS"

    rejected = client.post(
        "/v1/session/resolve",
        json={
            "username": "unknown",
            "password": password,
            "dns": "https://should-not-be-accepted.example.com",
        },
    )
    assert rejected.status_code == 422
