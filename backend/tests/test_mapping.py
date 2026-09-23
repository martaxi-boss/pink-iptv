from datetime import UTC, datetime

import httpx
import pytest
from sqlalchemy import inspect
from sqlalchemy.exc import IntegrityError

from app.clients.mega import MegaOTTClient
from app.models import SubscriptionMapping
from app.services.mappings import import_subscription


def _client(payload):
    token = "test-token"  # pragma: allowlist secret
    return MegaOTTClient(
        base_url="https://megaott.net/api",
        token=token,
        transport=httpx.MockTransport(lambda _request: httpx.Response(200, json=payload)),
    )


def test_valid_import_is_idempotent_and_never_persists_password(
    db,
    mega_payload,
) -> None:
    with _client(mega_payload) as client:
        first = import_subscription(db, client, 121)
    first_id = first.id
    first_synced = first.last_synced_at

    with _client(mega_payload) as client:
        second = import_subscription(db, client, 121)

    assert second.id == first_id
    assert second.username == mega_payload["username"]
    assert second.dns_link == mega_payload["dns_link"]
    assert second.last_synced_at >= first_synced
    assert "password" not in inspect(SubscriptionMapping).columns


def test_import_expiry_is_timezone_aware(db, mega_payload) -> None:
    with _client(mega_payload) as client:
        mapping = import_subscription(db, client, 121)
    assert mapping.expiring_at is not None

    with _client(mega_payload) as client:
        parsed = client.get_subscription(121)
    assert parsed.expiring_at is not None
    assert parsed.expiring_at.tzinfo is not None
    assert parsed.expiring_at.utcoffset() == UTC.utcoffset(parsed.expiring_at)
    assert SubscriptionMapping.__table__.c.expiring_at.type.timezone is True


def test_import_updates_dns_link(db, mega_payload) -> None:
    with _client(mega_payload) as client:
        import_subscription(db, client, 121)
    changed = dict(mega_payload)
    changed["dns_link"] = "https://new-stream.example.com"
    with _client(changed) as client:
        mapping = import_subscription(db, client, 121)
    assert mapping.dns_link == "https://new-stream.example.com"


def test_duplicate_subscription_id_protected(db) -> None:
    now = datetime.now(UTC)
    db.add(
        SubscriptionMapping(
            mega_subscription_id=121,
            username="first",
            dns_link="https://stream.example.com",
            last_synced_at=now,
        )
    )
    db.commit()
    db.add(
        SubscriptionMapping(
            mega_subscription_id=121,
            username="second",
            dns_link="https://stream.example.com",
            last_synced_at=now,
        )
    )
    with pytest.raises(IntegrityError):
        db.commit()
    db.rollback()


def test_duplicate_username_protected(db) -> None:
    now = datetime.now(UTC)
    db.add(
        SubscriptionMapping(
            mega_subscription_id=121,
            username="same-user",
            dns_link="https://one.example.com",
            last_synced_at=now,
        )
    )
    db.commit()
    db.add(
        SubscriptionMapping(
            mega_subscription_id=122,
            username="same-user",
            dns_link="https://two.example.com",
            last_synced_at=now,
        )
    )
    with pytest.raises(IntegrityError):
        db.commit()
    db.rollback()
