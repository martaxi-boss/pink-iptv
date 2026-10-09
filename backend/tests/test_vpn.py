import base64
import hashlib
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace

import jwt
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

from app import vpn as vpn_module
from app.config import Settings
from app.main import create_app
from app.models import VpnAddressRelease, VpnInstallation
from app.security import issue_session_token
from app.vpn import GatewayUnavailable, LocalGateway


def key(number: int = 1) -> str:
    return base64.b64encode(bytes([number]) * 32).decode()


@pytest.fixture
def vpn(settings, session_factory, future_mapping):
    config = Settings(
        **(
            settings.model_dump()
            | {
                "vpn_enabled": True,
                "vpn_server_public_key": key(250),
                "vpn_endpoint": "93.184.216.34:51820",
            }
        )
    )
    app = create_app(config)
    app.state.session_factory = session_factory
    calls = []

    class Gateway:
        fail = False

        def apply(self, operation, peer):
            calls.append((operation, peer.public_key, peer.address))
            if self.fail:
                raise GatewayUnavailable

    gateway = Gateway()
    app.state.vpn_gateway_factory = lambda: gateway
    token, _ = issue_session_token(future_mapping.id, config)
    with TestClient(app) as client:
        yield SimpleNamespace(
            client=client,
            auth={"Authorization": "Bearer " + token},
            calls=calls,
            gateway=gateway,
            settings=config,
        )


def enroll(vpn, number=1, **extra):
    return vpn.client.post(
        "/v1/vpn/enroll", headers=vpn.auth, json={"public_key": key(number), **extra}
    )


def device_payload(body):
    return {"public_key": key(), "device_token": body["device_token"]}


def test_authenticated_enrollment_stores_no_private_key_or_password(vpn, db):
    response = enroll(vpn)
    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    body = response.json()
    peer = db.scalar(select(VpnInstallation))
    assert peer.address == "10.66.0.3"
    assert body["address"] == "10.66.0.3/32"
    assert body["address_v6"].endswith("::3/128")
    assert peer.token_sha256 == hashlib.sha256(body["device_token"].encode()).hexdigest()
    assert body["device_token"] not in repr(peer.__dict__)
    assert "private_key" not in body
    assert not {"password", "private_key"}.intersection(VpnInstallation.__table__.columns.keys())
    assert vpn.calls == [("upsert", key(), "10.66.0.3")]
    assert body["allowed_ips"] == ["0.0.0.0/0", "::/0"]


def test_unique_addresses_and_lost_enrollment_reply_recovery(vpn, db):
    first = enroll(vpn).json()
    repeated = enroll(vpn).json()  # Authenticated same account recovers a lost HTTP response.
    assert first["address"] == repeated["address"]
    assert first["device_token"] != repeated["device_token"]
    assert enroll(vpn, 2).json()["address"] != first["address"]
    assert len(db.scalars(select(VpnInstallation)).all()) == 2
    assert vpn.client.post("/v1/vpn/refresh", json=device_payload(first)).status_code == 403


@pytest.mark.parametrize("auth", [None, "Bearer garbage", "Basic invalid"])
def test_no_unauthenticated_peer(vpn, auth):
    headers = {} if auth is None else {"Authorization": auth}
    assert (
        vpn.client.post("/v1/vpn/enroll", headers=headers, json={"public_key": key()}).status_code
        == 401
    )
    assert not vpn.calls


def test_expired_and_wrong_issuer_session_cannot_enroll(vpn, future_mapping):
    for claims in [
        {"exp": datetime.now(UTC) - timedelta(seconds=1), "iss": "pink-iptv"},
        {"exp": datetime.now(UTC) + timedelta(minutes=1), "iss": "other"},
    ]:
        token = jwt.encode(
            {"sub": f"mapping:{future_mapping.id}", "iat": datetime.now(UTC), **claims},
            vpn.settings.session_signing_key.get_secret_value(),
            algorithm="HS256",
        )
        assert (
            vpn.client.post(
                "/v1/vpn/enroll",
                json={"public_key": key()},
                headers={"Authorization": "Bearer " + token},
            ).status_code
            == 401
        )
    assert not vpn.calls


@pytest.mark.parametrize(
    "bad",
    ["not-base64", base64.b64encode(bytes(32)).decode(), base64.b64encode(bytes(31)).decode()],
)
def test_invalid_public_keys_fail_closed(vpn, bad):
    assert (
        vpn.client.post("/v1/vpn/enroll", headers=vpn.auth, json={"public_key": bad}).status_code
        == 422
    )
    assert not vpn.calls


def test_refresh_does_not_extend_grant_and_needs_installation_credential(vpn):
    body = enroll(vpn).json()
    payload = device_payload(body)
    refreshed = vpn.client.post("/v1/vpn/refresh", json=payload)
    assert refreshed.status_code == 200
    assert refreshed.json()["expires_at"] == body["expires_at"]
    assert refreshed.json()["device_token"] is None
    payload["device_token"] = "x" * 43
    assert vpn.client.post("/v1/vpn/refresh", json=payload).status_code == 403
    payload["device_token"] = body["device_token"]
    payload["public_key"] = key(2)
    assert vpn.client.post("/v1/vpn/refresh", json=payload).status_code == 403


def test_revocation_is_idempotent_and_cannot_reenroll(vpn, db):
    body = enroll(vpn).json()
    for _ in range(2):
        assert vpn.client.post("/v1/vpn/revoke", json=device_payload(body)).status_code == 204
    assert vpn.client.post("/v1/vpn/refresh", json=device_payload(body)).status_code == 403
    assert enroll(vpn, device_token=body["device_token"]).status_code == 403
    assert db.scalar(select(VpnInstallation)).revoked_at is not None
    assert vpn.calls[-1][0] == "remove"


def test_expired_installation_or_account_removes_peer(vpn, db, future_mapping):
    body = enroll(vpn).json()
    peer = db.scalar(select(VpnInstallation))
    peer.expires_at = datetime.now(UTC) - timedelta(seconds=1)
    db.commit()
    assert vpn.client.post("/v1/vpn/refresh", json=device_payload(body)).status_code == 403
    assert vpn.calls[-1][0] == "remove"
    body = enroll(vpn).json()
    future_mapping.expiring_at = datetime.now(UTC) - timedelta(seconds=1)
    db.commit()
    assert vpn.client.post("/v1/vpn/refresh", json=device_payload(body)).status_code == 403
    assert vpn.calls[-1][0] == "remove"


def test_account_installation_cap(vpn):
    assert vpn.settings.vpn_max_installations_per_account == 10
    for number in range(1, 11):
        assert enroll(vpn, number).status_code == 200
    assert enroll(vpn, 11).status_code == 429
    # An existing installation is reusable even when all ten slots are occupied.
    assert enroll(vpn).status_code == 200


def test_legacy_five_installation_override_remains_enforced(vpn):
    vpn.settings.vpn_max_installations_per_account = 5
    for number in range(1, 6):
        assert enroll(vpn, number).status_code == 200
    assert enroll(vpn, 6).status_code == 429
    assert enroll(vpn).status_code == 200


def test_thirty_authenticated_reenrollments_reuse_one_installation(vpn, db):
    for _ in range(30):
        assert enroll(vpn).status_code == 200
    assert len(db.scalars(select(VpnInstallation)).all()) == 1
    for number in range(2, 11):
        assert enroll(vpn, number).status_code == 200
    assert enroll(vpn, 11).status_code == 429


def test_expired_installations_no_longer_reserve_a_slot(vpn, db):
    for number in range(1, 11):
        assert enroll(vpn, number).status_code == 200
    expired = db.scalar(select(VpnInstallation).where(VpnInstallation.public_key == key(2)))
    assert expired is not None
    expired.expires_at = datetime.now(UTC) - timedelta(seconds=1)
    db.commit()
    assert enroll(vpn, 11).status_code == 200


def test_gateway_failure_never_releases_configuration(vpn):
    vpn.gateway.fail = True
    response = enroll(vpn)
    assert response.status_code == 503
    assert "device_token" not in response.json()
    vpn.gateway.fail = False
    body = enroll(vpn).json()
    vpn.gateway.fail = True
    assert vpn.client.post("/v1/vpn/refresh", json=device_payload(body)).status_code == 503
    assert vpn.client.post("/v1/vpn/revoke", json=device_payload(body)).status_code == 503
    vpn.gateway.fail = False
    assert vpn.client.post("/v1/vpn/refresh", json=device_payload(body)).status_code == 403


def test_enrollment_disabled_by_default(settings, session_factory):
    app = create_app(settings)
    app.state.session_factory = session_factory
    assert TestClient(app).post("/v1/vpn/enroll", json={"public_key": key()}).status_code == 503


def test_no_secret_fields_or_validation_input_echo(vpn):
    response = vpn.client.post(
        "/v1/vpn/enroll",
        headers=vpn.auth,
        json={"public_key": key(), "private_key": "sensitive-fixture"},
    )
    assert response.status_code == 422
    assert "sensitive-fixture" not in response.text
    assert "private_key" not in response.text


def test_unavailable_local_socket_is_generic(tmp_path):
    peer = SimpleNamespace(public_key=key(), address="10.66.0.3", expires_at=datetime.now(UTC))
    with pytest.raises(GatewayUnavailable) as failure:
        LocalGateway(str(tmp_path / "absent")).apply("upsert", peer)
    assert str(failure.value) == ""


def test_revocation_releases_address_but_preserves_tombstone(vpn, db):
    first = enroll(vpn).json()
    response = vpn.client.post("/v1/vpn/revoke", json=device_payload(first))
    assert response.status_code == 204
    previous = db.scalar(select(VpnInstallation).where(VpnInstallation.public_key == key()))
    assert previous.revoked_at is not None
    assert previous.address is None
    audit = db.scalar(select(VpnAddressRelease))
    assert audit.installation_id == previous.id
    assert audit.address == "10.66.0.3"
    assert audit.cause == "revoked"
    assert enroll(vpn, device_token=first["device_token"]).status_code == 403
    assert vpn.client.post("/v1/vpn/refresh", json=device_payload(first)).status_code == 403
    assert enroll(vpn, 2).json()["address"] == "10.66.0.3/32"


def test_expired_lease_is_reclaimed_only_after_gateway_removal(vpn, db, monkeypatch):
    monkeypatch.setattr(vpn_module, "ADDRESS_POOL", ("10.66.0.3",))
    first = enroll(vpn).json()
    previous = db.scalar(select(VpnInstallation).where(VpnInstallation.public_key == key()))
    previous.expires_at = datetime.now(UTC) - timedelta(seconds=1)
    db.commit()
    next_lease = enroll(vpn, 2)
    assert next_lease.status_code == 200
    assert next_lease.json()["address"] == "10.66.0.3/32"
    db.refresh(previous)
    assert previous.address is None
    assert db.scalar(select(VpnAddressRelease)).cause == "expired"
    assert vpn.calls[-2:] == [
        ("remove", key(), "10.66.0.3"),
        ("upsert", key(2), "10.66.0.3"),
    ]
    assert vpn.client.post("/v1/vpn/refresh", json=device_payload(first)).status_code == 403
    # The stale device cannot take a route already assigned to another key.
    assert enroll(vpn).status_code == 503


def test_gateway_failure_never_recycles_address(vpn, db, monkeypatch):
    monkeypatch.setattr(vpn_module, "ADDRESS_POOL", ("10.66.0.3",))
    enroll(vpn)
    previous = db.scalar(select(VpnInstallation).where(VpnInstallation.public_key == key()))
    previous.expires_at = datetime.now(UTC) - timedelta(seconds=1)
    db.commit()
    vpn.gateway.fail = True
    denied = enroll(vpn, 2)
    assert denied.status_code == 503
    assert previous.address == "10.66.0.3"
    assert db.scalar(select(VpnAddressRelease)) is None
    assert len(db.scalars(select(VpnInstallation)).all()) == 1
    vpn.gateway.fail = False
    assert enroll(vpn, 2).status_code == 200


def test_current_offline_lease_is_not_evicted_to_make_space(vpn, db, monkeypatch):
    monkeypatch.setattr(vpn_module, "ADDRESS_POOL", ("10.66.0.3",))
    assert enroll(vpn).status_code == 200
    assert enroll(vpn, 2).status_code == 503
    assert db.scalar(select(VpnAddressRelease)) is None
    assert [operation for operation, _, _ in vpn.calls] == ["upsert"]


def test_same_installation_reauth_after_expiry_keeps_identity(vpn, db):
    first = enroll(vpn).json()
    previous = db.scalar(select(VpnInstallation).where(VpnInstallation.public_key == key()))
    previous.expires_at = datetime.now(UTC) - timedelta(seconds=1)
    db.commit()
    renewed = enroll(vpn)
    assert renewed.status_code == 200
    assert renewed.json()["address"] == first["address"]
    assert renewed.json()["device_token"] != first["device_token"]
    assert len(db.scalars(select(VpnInstallation)).all()) == 1
    assert vpn.client.post("/v1/vpn/refresh", json=device_payload(first)).status_code == 403


def test_subscription_expiry_address_is_reclaimed_by_another_account(
    vpn, db, future_mapping, monkeypatch
):
    monkeypatch.setattr(vpn_module, "ADDRESS_POOL", ("10.66.0.3",))
    assert enroll(vpn).status_code == 200
    previous = db.scalar(select(VpnInstallation).where(VpnInstallation.public_key == key()))
    future_mapping.expiring_at = datetime.now(UTC) - timedelta(seconds=1)
    db.commit()
    from app.models import SubscriptionMapping

    second_mapping = SubscriptionMapping(
        mega_subscription_id=122,
        username="other-fixture-user",
        dns_link="https://stream.example.net",
        expiring_at=datetime.now(UTC) + timedelta(days=10),
        last_synced_at=datetime.now(UTC),
    )
    db.add(second_mapping)
    db.commit()
    token, _ = issue_session_token(second_mapping.id, vpn.settings)
    other_auth = {"Authorization": "Bearer " + token}
    result = vpn.client.post("/v1/vpn/enroll", headers=other_auth, json={"public_key": key(2)})
    assert result.status_code == 200
    assert result.json()["address"] == "10.66.0.3/32"
    db.refresh(previous)
    assert previous.address is None
    assert db.scalar(select(VpnAddressRelease)).cause == "expired"


def test_account_can_list_and_release_only_its_own_opaque_installations(vpn, db):
    assert enroll(vpn).status_code == 200
    assert enroll(vpn, 2).status_code == 200
    current = vpn.client.get(
        "/v1/vpn/installations", headers=vpn.auth, params={"current_public_key": key()}
    )
    assert current.status_code == 200
    assert current.headers["cache-control"] == "no-store"
    devices = current.json()
    assert len(devices) == 2
    assert sum(d["is_current"] for d in devices) == 1
    assert all(len(d["installation_id"]) == 64 for d in devices)
    assert key() not in current.text and key(2) not in current.text
    assert "device_token" not in current.text and "private_key" not in current.text
    other = next(d for d in devices if not d["is_current"])
    released = vpn.client.post(
        "/v1/vpn/installations/release",
        headers=vpn.auth,
        json={"installation_id": other["installation_id"], "confirm": True},
    )
    assert released.status_code == 204
    assert (
        vpn.client.post(
            "/v1/vpn/installations/release",
            headers=vpn.auth,
            json={"installation_id": other["installation_id"], "confirm": True},
        ).status_code
        == 204
    )
    refreshed = vpn.client.get("/v1/vpn/installations", headers=vpn.auth)
    assert refreshed.status_code == 200
    assert len(refreshed.json()) == 1
    peer = db.scalar(select(VpnInstallation).where(VpnInstallation.public_key == key(2)))
    assert peer.revoked_at is not None and peer.address is None
    assert enroll(vpn, 3).json()["address"] == "10.66.0.4/32"


def test_installation_release_needs_valid_login_session_and_explicit_confirmation(vpn, db):
    enrolled = enroll(vpn)
    assert enrolled.status_code == 200
    handle = vpn.client.get("/v1/vpn/installations", headers=vpn.auth).json()[0]["installation_id"]
    assert vpn.client.get("/v1/vpn/installations").status_code == 401
    assert (
        vpn.client.post(
            "/v1/vpn/installations/release",
            json={"installation_id": handle, "confirm": True},
        ).status_code
        == 401
    )
    assert (
        vpn.client.post(
            "/v1/vpn/installations/release",
            headers=vpn.auth,
            json={"installation_id": handle, "confirm": False},
        ).status_code
        == 422
    )
    assert db.scalar(select(VpnInstallation)).revoked_at is None


def test_installation_reclaim_is_account_isolated(vpn, db):
    from app.models import SubscriptionMapping

    assert enroll(vpn).status_code == 200
    owned = vpn.client.get("/v1/vpn/installations", headers=vpn.auth).json()[0]["installation_id"]
    other_mapping = SubscriptionMapping(
        mega_subscription_id=123,
        username="different-owner-fixture",
        dns_link="https://other.example.com",
        expiring_at=datetime.now(UTC) + timedelta(days=30),
        last_synced_at=datetime.now(UTC),
    )
    db.add(other_mapping)
    db.commit()
    token, _ = issue_session_token(other_mapping.id, vpn.settings)
    other_auth = {"Authorization": "Bearer " + token}
    assert vpn.client.get("/v1/vpn/installations", headers=other_auth).json() == []
    denied = vpn.client.post(
        "/v1/vpn/installations/release",
        headers=other_auth,
        json={"installation_id": owned, "confirm": True},
    )
    assert denied.status_code == 404
    assert "public_key" not in denied.text and "device_token" not in denied.text
    assert db.scalar(select(VpnInstallation)).revoked_at is None


def test_reclaim_gateway_unavailable_keeps_address_reserved(vpn, db):
    assert enroll(vpn).status_code == 200
    handle = vpn.client.get("/v1/vpn/installations", headers=vpn.auth).json()[0][
        "installation_id"
    ]
    payload = {"installation_id": handle, "confirm": True}
    vpn.gateway.fail = True
    failed = vpn.client.post("/v1/vpn/installations/release", headers=vpn.auth, json=payload)
    assert failed.status_code == 503
    peer = db.scalar(select(VpnInstallation))
    assert peer.revoked_at is not None
    assert peer.address == "10.66.0.3"
    assert vpn.client.get("/v1/vpn/installations", headers=vpn.auth).json() == []
    vpn.gateway.fail = False
    assert (
        vpn.client.post("/v1/vpn/installations/release", headers=vpn.auth, json=payload).status_code
        == 204
    )
    db.refresh(peer)
    assert peer.address is None
