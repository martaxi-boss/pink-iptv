from datetime import UTC, datetime, timedelta
from types import SimpleNamespace

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from sqlalchemy import select

from app.auth_rate_limit import charge_auth_attempt
from app.main import create_app
from app.models import AuthRateWindow


class UnknownMega:
    def __init__(self, calls):
        self.calls = calls

    def find_subscription_id_by_username(self, username):
        self.calls.append(username)
        return None

    def close(self):
        pass


class InvalidXtream:
    def __init__(self, calls):
        self.calls = calls

    def authenticate(self, **_kwargs):
        self.calls.append("auth")
        return SimpleNamespace(code="INVALID_CREDENTIALS", account_expires_at=None)

    def close(self):
        pass


def make_client(settings, session_factory, *, per_user=2, global_limit=8):
    limited = settings.model_copy(
        update={
            "auth_rate_per_username": per_user,
            "auth_rate_global": global_limit,
        }
    )
    app = create_app(limited)
    app.state.session_factory = session_factory
    mega_calls = []
    xtream_calls = []
    app.state.mega_client_factory = lambda: UnknownMega(mega_calls)
    app.state.xtream_client_factory = lambda: InvalidXtream(xtream_calls)
    return TestClient(app), mega_calls, xtream_calls


def post_login(client, username):
    return client.post(
        "/v1/session/resolve",
        json={"username": username, "password": "redacted-fixture"},
    )


def test_repeated_username_is_stopped_before_upstream_calls(settings, session_factory):
    client, mega_calls, xtream_calls = make_client(settings, session_factory)
    for _ in range(2):
        response = post_login(client, "customer-unguessed")
        assert response.status_code == 200
        assert response.json()["code"] == "INVALID_CREDENTIALS"

    limited = post_login(client, "customer-unguessed")
    assert limited.status_code == 429
    assert limited.headers["cache-control"] == "no-store"
    assert 1 <= int(limited.headers["retry-after"]) <= 60
    assert limited.json() == {"detail": "PINK authentication temporarily unavailable"}
    assert mega_calls == ["customer-unguessed", "customer-unguessed"]
    assert xtream_calls == []

    with session_factory() as session:
        buckets = session.scalars(select(AuthRateWindow)).all()
        assert len(buckets) == 2
        assert all("customer-unguessed" not in bucket.bucket_key for bucket in buckets)
        assert all("redacted-fixture" not in bucket.bucket_key for bucket in buckets)


def test_same_limit_applies_to_known_and_unknown_account(
    settings, session_factory, future_mapping
):
    client, mega_calls, xtream_calls = make_client(
        settings, session_factory, per_user=1, global_limit=8
    )
    known = post_login(client, future_mapping.username)
    unknown = post_login(client, "not-provisioned")
    assert known.status_code == unknown.status_code == 200
    assert known.json()["code"] == unknown.json()["code"] == "INVALID_CREDENTIALS"

    known_limited = post_login(client, future_mapping.username)
    unknown_limited = post_login(client, "not-provisioned")
    assert known_limited.status_code == unknown_limited.status_code == 429
    assert known_limited.json() == unknown_limited.json()
    assert mega_calls == ["not-provisioned"]
    assert xtream_calls == ["auth"]


def test_global_limit_stops_rotating_usernames(settings, session_factory):
    client, mega_calls, _ = make_client(
        settings, session_factory, per_user=10, global_limit=2
    )
    assert post_login(client, "a").status_code == 200
    assert post_login(client, "b").status_code == 200
    assert post_login(client, "c").status_code == 429
    # New unknown usernames cannot flood database buckets once global is full.
    assert post_login(client, "d").status_code == 429
    assert mega_calls == ["a", "b"]
    with session_factory() as session:
        buckets = session.scalars(select(AuthRateWindow)).all()
        assert len(buckets) == 3


def test_expired_window_is_reset_and_old_digest_buckets_removed(db, settings):
    limited = settings.model_copy(
        update={"auth_rate_per_username": 1, "auth_rate_global": 3}
    )
    first = datetime(2026, 10, 8, 12, 0, 5, tzinfo=UTC)
    charge_auth_attempt(db, limited, "private-user", now=first)
    with pytest.raises(HTTPException) as blocked:
        charge_auth_attempt(db, limited, "private-user", now=first)
    assert blocked.value.status_code == 429

    charge_auth_attempt(db, limited, "another-user", now=first + timedelta(minutes=1))
    windows = db.scalars(select(AuthRateWindow)).all()
    assert len(windows) == 2
    assert {bucket.window_number for bucket in windows} == {
        int((first + timedelta(minutes=1)).timestamp()) // 60
    }


def test_no_unsafe_sqlite_fallback_outside_tests(db, settings):
    deployed = settings.model_copy(update={"app_env": "staging"})
    with pytest.raises(HTTPException) as denied:
        charge_auth_attempt(db, deployed, "u")
    assert denied.value.status_code == 503
