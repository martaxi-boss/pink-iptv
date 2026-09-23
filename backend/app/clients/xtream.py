import socket
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any

import httpx

from app.outbound_transport import PinnedHTTPTransport
from app.url_safety import (
    DNSResolutionFailure,
    Resolver,
    UnsafeOutboundURL,
    player_api_url,
    resolve_dns_link_target,
    system_resolver,
)

_XTREAM_HEADERS = {
    "Accept": "application/json",
    "User-Agent": "PINK-IPTV/0.1",
}


class XtreamError(RuntimeError):
    pass


class XtreamDNSUnreachable(XtreamError):
    pass


class XtreamUpstreamError(XtreamError):
    pass


@dataclass(frozen=True)
class XtreamAuthResult:
    code: str
    account_expires_at: datetime | None = None


def _looks_like_dns_error(exc: BaseException) -> bool:
    current: BaseException | None = exc
    seen: set[int] = set()
    while current is not None and id(current) not in seen:
        seen.add(id(current))
        if isinstance(current, socket.gaierror):
            return True
        message = str(current).casefold()
        if any(
            marker in message
            for marker in (
                "name or service not known",
                "temporary failure in name resolution",
                "nodename nor servname provided",
                "getaddrinfo failed",
            )
        ):
            return True
        current = current.__cause__ or current.__context__
    return False


def _parse_epoch(value: Any) -> datetime | None:
    if value in (None, "", "0", 0):
        return None
    try:
        return datetime.fromtimestamp(int(value), tz=UTC)
    except (TypeError, ValueError, OverflowError, OSError):
        return None


class XtreamClient:
    def __init__(
        self,
        *,
        timeout_seconds: float = 10.0,
        transport: httpx.BaseTransport | None = None,
        resolver: Resolver = system_resolver,
    ) -> None:
        self._resolver = resolver
        self._timeout = httpx.Timeout(timeout_seconds)
        self._client = (
            httpx.Client(
                headers=_XTREAM_HEADERS,
                timeout=self._timeout,
                follow_redirects=False,
                transport=transport,
                trust_env=False,
            )
            if transport is not None
            else None
        )

    def close(self) -> None:
        if self._client is not None:
            self._client.close()

    def _request(
        self,
        *,
        target,
        url: str,
        username: str,
        password: str,
    ) -> httpx.Response:
        client = self._client
        close_after = False
        if client is None:
            client = httpx.Client(
                headers=_XTREAM_HEADERS,
                timeout=self._timeout,
                follow_redirects=False,
                transport=PinnedHTTPTransport(target),
                trust_env=False,
            )
            close_after = True
        try:
            return client.get(
                url,
                params={"username": username, "password": password},
            )
        finally:
            if close_after:
                client.close()

    def authenticate(
        self,
        *,
        dns_link: str,
        username: str,
        password: str,
    ) -> XtreamAuthResult:
        try:
            target = resolve_dns_link_target(dns_link, resolver=self._resolver)
        except DNSResolutionFailure as exc:
            raise XtreamDNSUnreachable("Xtream host could not be resolved") from exc
        except UnsafeOutboundURL as exc:
            raise XtreamUpstreamError("Xtream destination failed safety validation") from exc

        url = player_api_url(target.origin)
        try:
            response = self._request(
                target=target,
                url=url,
                username=username,
                password=password,
            )
        except httpx.TimeoutException as exc:
            raise XtreamUpstreamError("Xtream request timed out") from exc
        except httpx.RequestError as exc:
            if _looks_like_dns_error(exc):
                raise XtreamDNSUnreachable("Xtream host could not be resolved") from exc
            raise XtreamUpstreamError("Xtream connection failed") from exc

        if 300 <= response.status_code < 400:
            raise XtreamUpstreamError("Xtream redirect refused")
        if response.status_code in {401, 403}:
            return XtreamAuthResult(code="INVALID_CREDENTIALS")
        if response.status_code >= 400:
            raise XtreamUpstreamError("Xtream returned an unexpected HTTP status")

        try:
            payload = response.json()
        except ValueError as exc:
            raise XtreamUpstreamError("Xtream returned invalid JSON") from exc
        if not isinstance(payload, dict) or not isinstance(payload.get("user_info"), dict):
            raise XtreamUpstreamError("Xtream response schema is invalid")

        user_info = payload["user_info"]
        status = str(user_info.get("status") or "").casefold()
        if status in {"expired", "expiration", "expired account"}:
            return XtreamAuthResult(
                code="EXPIRED",
                account_expires_at=_parse_epoch(user_info.get("exp_date")),
            )
        if status in {"disabled", "deactivated", "banned", "blocked"}:
            return XtreamAuthResult(
                code="DISABLED",
                account_expires_at=_parse_epoch(user_info.get("exp_date")),
            )

        auth = user_info.get("auth")
        if auth in (0, "0", False, None):
            return XtreamAuthResult(code="INVALID_CREDENTIALS")
        if auth not in (1, "1", True):
            raise XtreamUpstreamError("Xtream authentication field is invalid")

        account_expires_at = _parse_epoch(user_info.get("exp_date"))
        if account_expires_at is not None and account_expires_at <= datetime.now(UTC):
            return XtreamAuthResult(
                code="EXPIRED",
                account_expires_at=account_expires_at,
            )
        return XtreamAuthResult(
            code="SUCCESS",
            account_expires_at=account_expires_at,
        )
