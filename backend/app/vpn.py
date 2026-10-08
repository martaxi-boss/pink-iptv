"""Authenticated installation leases. Device private keys never cross this boundary."""

import base64
import hashlib
import hmac
import ipaddress
import json
import math
import secrets
import socket
from datetime import UTC, datetime, timedelta
from typing import Literal

import jwt
from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel, ConfigDict, Field, SecretStr, field_validator
from sqlalchemy import select, text
from sqlalchemy.orm import Session

from app.db import get_db
from app.models import SubscriptionMapping, VpnInstallation

router = APIRouter(prefix="/v1/vpn")


def public_key(value: str) -> str:
    try:
        raw = base64.b64decode(value, validate=True)
    except (ValueError, TypeError) as error:
        raise ValueError("Invalid public key") from error
    if len(raw) != 32 or not any(raw) or base64.b64encode(raw).decode() != value:
        raise ValueError("Invalid public key")
    return value


class EnrollRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    public_key: str
    device_token: SecretStr | None = None

    _key = field_validator("public_key")(public_key)


class LeaseResponse(BaseModel):
    device_token: str | None = None
    expires_at: datetime
    address: str
    address_v6: str
    server_public_key: str
    endpoint: str
    allowed_ips: list[str] = ["0.0.0.0/0", "::/0"]
    dns: list[str] = ["1.1.1.1"]
    mtu: int = 1380
    probe_url: Literal["http://10.66.0.1:51821/health"] = "http://10.66.0.1:51821/health"


class DeviceRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    public_key: str
    device_token: SecretStr = Field(min_length=40, max_length=128)

    _key = field_validator("public_key")(public_key)


class GatewayUnavailable(Exception):
    """Deliberately carries no socket payload, credentials or upstream exception."""


class LocalGateway:
    """Unprivileged backend -> root-owned, restricted Unix socket; no shell invocation."""

    def __init__(self, path: str) -> None:
        self.path = path

    def apply(self, operation: str, peer: VpnInstallation) -> None:
        message = {
            "operation": operation,
            "public_key": peer.public_key,
            "address": peer.address,
            "expires_at": min(
                aware(peer.expires_at).timestamp(), datetime.now(UTC).timestamp() + 300
            ),
        }
        try:
            with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
                connection.settimeout(5)
                connection.connect(self.path)
                connection.sendall(json.dumps(message).encode() + b"\n")
                raw = connection.makefile("rb").readline(1025)
                if len(raw) > 1024 or json.loads(raw) != {"ok": True}:
                    raise GatewayUnavailable
        except (OSError, ValueError, GatewayUnavailable):
            raise GatewayUnavailable from None


def aware(value: datetime) -> datetime:
    return value.replace(tzinfo=UTC) if value.tzinfo is None else value.astimezone(UTC)


def deny(code: int = 403) -> None:
    raise HTTPException(code, "PINK connection unavailable", headers={"Cache-Control": "no-store"})


def enabled(request: Request, response: Response):
    response.headers["Cache-Control"] = "no-store"
    settings = request.app.state.settings
    if not settings.vpn_enabled:
        deny(503)
    if not settings.vpn_server_public_key or not settings.vpn_endpoint:
        deny(503)
    return settings


def mapping_for_session(request: Request, db: Session) -> SubscriptionMapping:
    authorization = request.headers.get("authorization", "")
    if not authorization.startswith("Bearer ") or len(authorization) > 2048:
        deny(401)
    try:
        claims = jwt.decode(
            authorization[7:],
            request.app.state.settings.session_signing_key.get_secret_value(),
            algorithms=["HS256"],
            issuer="pink-iptv",
            options={"require": ["sub", "iat", "exp", "iss"]},
        )
        subject = claims["sub"]
        if not isinstance(subject, str) or not subject.startswith("mapping:"):
            deny(401)
        mapping = db.get(SubscriptionMapping, int(subject[8:]))
    except (jwt.PyJWTError, ValueError, TypeError, KeyError):
        deny(401)
    if mapping is None or expired_mapping(mapping):
        deny()
    return mapping


def expired_mapping(mapping: SubscriptionMapping) -> bool:
    return mapping.expiring_at is not None and aware(mapping.expiring_at) <= datetime.now(UTC)


def lock_allocations(db: Session) -> None:
    if db.get_bind().dialect.name == "postgresql":
        db.execute(text("SELECT pg_advisory_xact_lock(5066051)"))


def gateway(request: Request):
    factory = getattr(request.app.state, "vpn_gateway_factory", None)
    return factory() if factory else LocalGateway(request.app.state.settings.vpn_control_socket)


def config(peer: VpnInstallation, settings, device_token: str | None = None) -> LeaseResponse:
    host = int(ipaddress.ip_address(peer.address)) & 255
    return LeaseResponse(
        device_token=device_token,
        expires_at=aware(peer.expires_at),
        address=peer.address + "/32",
        address_v6=f"fd66:7069:6e6b::{host:x}/128",
        server_public_key=settings.vpn_server_public_key,
        endpoint=settings.vpn_endpoint,
    )


def token_matches(peer: VpnInstallation, token: str) -> bool:
    return hmac.compare_digest(peer.token_sha256, hashlib.sha256(token.encode()).hexdigest())


@router.post("/enroll", response_model=LeaseResponse)
def enroll(
    payload: EnrollRequest, request: Request, response: Response, db: Session = Depends(get_db)
) -> LeaseResponse:
    settings = enabled(request, response)
    mapping = mapping_for_session(request, db)
    lock_allocations(db)
    peer = db.scalar(
        select(VpnInstallation).where(VpnInstallation.public_key == payload.public_key)
    )
    if peer is None or peer.mapping_id != mapping.id:
        active = db.scalars(
            select(VpnInstallation).where(
                VpnInstallation.mapping_id == mapping.id,
                VpnInstallation.revoked_at.is_(None),
                VpnInstallation.expires_at > datetime.now(UTC),
            )
        ).all()
        if len(active) >= settings.vpn_max_installations_per_account:
            # Capacity is based on unexpired per-installation leases, not the
            # current number of connected peers. Tell the authenticated client
            # when its next slot expires, without identifying other devices.
            earliest = min(aware(installation.expires_at) for installation in active)
            seconds = max(1, min(86400, math.ceil((earliest - datetime.now(UTC)).total_seconds())))
            raise HTTPException(
                429,
                "PINK connection unavailable",
                headers={"Cache-Control": "no-store", "Retry-After": str(seconds)},
            )
    if peer is not None:
        if peer.mapping_id != mapping.id and (
            payload.device_token is None
            or not token_matches(peer, payload.device_token.get_secret_value())
        ):
            deny()
        if peer.revoked_at is not None:
            deny()
    else:
        used = set(db.scalars(select(VpnInstallation.address)).all())
        address = next((f"10.66.0.{n}" for n in range(3, 255) if f"10.66.0.{n}" not in used), None)
        if address is None:
            deny(503)
        peer = VpnInstallation(
            public_key=payload.public_key, address=address, mapping_id=mapping.id
        )
        db.add(peer)
    # A new authenticated PINK session renews the installation lease; refresh never extends it.
    peer.mapping_id = mapping.id
    expires = datetime.now(UTC) + timedelta(seconds=settings.vpn_lease_seconds)
    peer.expires_at = min(expires, aware(mapping.expiring_at)) if mapping.expiring_at else expires
    token = secrets.token_urlsafe(32)
    peer.token_sha256 = hashlib.sha256(token.encode()).hexdigest()
    # Commit ownership first. A lost HTTP reply is recoverable with the same authenticated
    # account/key; a gateway failure returns no usable lease and can be retried safely.
    try:
        db.commit()
    except Exception:
        db.rollback()
        deny(503)
    try:
        gateway(request).apply("upsert", peer)
    except GatewayUnavailable:
        deny(503)
    return config(peer, settings, token)


def installation(payload: DeviceRequest, db: Session) -> VpnInstallation:
    peer = db.scalar(
        select(VpnInstallation).where(VpnInstallation.public_key == payload.public_key)
    )
    if peer is None or not token_matches(peer, payload.device_token.get_secret_value()):
        deny()
    return peer


@router.post("/refresh", response_model=LeaseResponse)
def refresh(
    payload: DeviceRequest, request: Request, response: Response, db: Session = Depends(get_db)
) -> LeaseResponse:
    settings = enabled(request, response)
    lock_allocations(db)
    peer = installation(payload, db)
    mapping = db.get(SubscriptionMapping, peer.mapping_id)
    if (
        peer.revoked_at is not None
        or aware(peer.expires_at) <= datetime.now(UTC)
        or mapping is None
        or expired_mapping(mapping)
    ):
        try:
            gateway(request).apply("remove", peer)
        except GatewayUnavailable:
            deny(503)
        deny()
    try:
        gateway(request).apply("upsert", peer)
    except GatewayUnavailable:
        deny(503)
    return config(peer, settings)


@router.post("/revoke", status_code=204)
def revoke(
    payload: DeviceRequest, request: Request, response: Response, db: Session = Depends(get_db)
) -> None:
    enabled(request, response)
    lock_allocations(db)
    peer = installation(payload, db)
    # Persist revocation first: gateway failure cannot turn this into a renewable grant.
    peer.revoked_at = datetime.now(UTC)
    db.commit()
    try:
        gateway(request).apply("remove", peer)
    except GatewayUnavailable:
        deny(503)
