import httpx
import pytest

from app.clients.xtream import XtreamClient, XtreamUpstreamError
from app.url_safety import UnsafeOutboundURL, validate_dns_link


def public_resolver(_hostname: str, _port: int):
    return ["93.184.216.34"]


def test_normal_public_hostname_allowed() -> None:
    assert (
        validate_dns_link(
            "https://stream.example.com",
            resolver=public_resolver,
        )
        == "https://stream.example.com"
    )


@pytest.mark.parametrize(
    "url",
    [
        "not a url",
        "ftp://stream.example.com",
        "http://localhost",
        "http://127.0.0.1",
        "http://10.0.0.1",
        "http://169.254.10.20",
        "http://user:pass@stream.example.com",  # pragma: allowlist secret
        "https://stream.example.com/?unexpected=1",
        "https://stream.example.com/#fragment",
    ],
)
def test_unsafe_urls_rejected(url: str) -> None:
    with pytest.raises(UnsafeOutboundURL):
        validate_dns_link(url, resolver=public_resolver)


def test_hostname_resolving_private_is_rejected() -> None:
    with pytest.raises(UnsafeOutboundURL):
        validate_dns_link(
            "https://stream.example.com",
            resolver=lambda _host, _port: ["192.168.1.10"],
        )


def test_redirect_is_not_followed() -> None:
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(str(request.url))
        return httpx.Response(
            302,
            headers={"Location": "https://other.example.com/player_api.php"},
        )

    client = XtreamClient(
        transport=httpx.MockTransport(handler),
        resolver=public_resolver,
    )
    password = "wrong"  # pragma: allowlist secret
    with pytest.raises(XtreamUpstreamError, match="redirect refused"):
        client.authenticate(
            dns_link="https://stream.example.com",
            username="user",
            password=password,
        )
    client.close()
    assert len(calls) == 1


@pytest.mark.parametrize(
    ("url", "expected"),
    [
        ("http://stream.example.com", "http://stream.example.com"),
        ("http://stream.example.com/", "http://stream.example.com"),
        ("https://stream.example.com:8443", "https://stream.example.com:8443"),
    ],
)
def test_dns_link_origin_path_contract_allows_only_origin(
    url: str,
    expected: str,
) -> None:
    assert validate_dns_link(url, resolver=public_resolver) == expected


@pytest.mark.parametrize(
    "url",
    [
        "https://stream.example.com/foo",
        "https://stream.example.com/player_api.php",
        "https://stream.example.com/anything/player_api.php",
    ],
)
def test_dns_link_origin_path_contract_rejects_non_root_paths(url: str) -> None:
    with pytest.raises(UnsafeOutboundURL, match="path"):
        validate_dns_link(url, resolver=public_resolver)
