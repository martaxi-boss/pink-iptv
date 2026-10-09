import base64
import hashlib
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace

import jwt
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

from app.config import Settings
from app.main import create_app
from app.models import VpnInstallation
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
