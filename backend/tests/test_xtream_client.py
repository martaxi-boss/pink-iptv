import httpx
import pytest

from app.clients.xtream import (
    XtreamClient,
    XtreamDNSUnreachable,
    XtreamUpstreamError,
)
from app.url_safety import DNSResolutionFailure


def public_resolver(_hostname: str, _port: int):
    return ["93.184.216.34"]


def test_success_uses_exact_username_password_and_dns() -> None:
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["host"] = request.url.host
        seen["username"] = request.url.params.get("username")
        seen["password"] = request.url.params.get("password")
        return httpx.Response(
            200,
            json={"user_info": {"auth": 1, "status": "Active"}},
        )

    client = XtreamClient(
        transport=httpx.MockTransport(handler),
        resolver=public_resolver,
    )
    password = "exact-pass"  # pragma: allowlist secret
    result = client.authenticate(
        dns_link="http://exact.example.com",
        username="exact-user",
        password=password,
    )
    client.close()
    assert result.code == "SUCCESS"
    assert seen == {
        "host": "exact.example.com",
        "username": "exact-user",
        "password": password,
    }


def test_auth_zero_is_invalid_credentials() -> None:
    transport = httpx.MockTransport(
        lambda _request: httpx.Response(
            200,
            json={"user_info": {"auth": 0}},
        )
    )
    client = XtreamClient(transport=transport, resolver=public_resolver)
    password = "wrong"  # pragma: allowlist secret
    result = client.authenticate(
        dns_link="https://stream.example.com",
        username="u",
        password=password,
    )
    client.close()
    assert result.code == "INVALID_CREDENTIALS"


def test_explicit_expired() -> None:
    transport = httpx.MockTransport(
        lambda _request: httpx.Response(
            200,
            json={"user_info": {"auth": 0, "status": "Expired"}},
        )
    )
    client = XtreamClient(transport=transport, resolver=public_resolver)
    password = "wrong"  # pragma: allowlist secret
    result = client.authenticate(
        dns_link="https://stream.example.com",
        username="u",
        password=password,
    )
    client.close()
    assert result.code == "EXPIRED"


@pytest.mark.parametrize(
    "status",
    ["Disabled", "Deactivated", "Banned", "Blocked"],
)
def test_explicit_disabled(status: str) -> None:
    transport = httpx.MockTransport(
        lambda _request: httpx.Response(
            200,
            json={"user_info": {"auth": 0, "status": status}},
        )
    )
    client = XtreamClient(transport=transport, resolver=public_resolver)
    password = "wrong"  # pragma: allowlist secret
    result = client.authenticate(
        dns_link="https://stream.example.com",
        username="u",
        password=password,
    )
    client.close()
    assert result.code == "DISABLED"


def test_dns_resolution_failure_classified() -> None:
    def failed_resolver(_hostname: str, _port: int):
        raise DNSResolutionFailure("no dns")

    client = XtreamClient(
        transport=httpx.MockTransport(
            lambda _request: httpx.Response(200)
        ),
        resolver=failed_resolver,
    )
    password = "p"  # pragma: allowlist secret
    with pytest.raises(XtreamDNSUnreachable):
        client.authenticate(
            dns_link="https://missing.example.com",
            username="u",
            password=password,
        )
    client.close()


@pytest.mark.parametrize(
    "response",
    [httpx.Response(500), httpx.Response(200, content=b"not-json")],
)
def test_5xx_and_invalid_json_are_upstream_error(
    response: httpx.Response,
) -> None:
    client = XtreamClient(
        transport=httpx.MockTransport(lambda _request: response),
        resolver=public_resolver,
    )
    password = "p"  # pragma: allowlist secret
    with pytest.raises(XtreamUpstreamError):
        client.authenticate(
            dns_link="https://stream.example.com",
            username="u",
            password=password,
        )
    client.close()


def test_timeout_is_upstream_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("timeout", request=request)

    client = XtreamClient(
        transport=httpx.MockTransport(handler),
        resolver=public_resolver,
    )
    password = "p"  # pragma: allowlist secret
    with pytest.raises(XtreamUpstreamError, match="timed out"):
        client.authenticate(
            dns_link="https://stream.example.com",
            username="u",
            password=password,
        )
    client.close()
