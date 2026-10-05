import logging

import httpx
import pytest

from app.clients.mega import MegaOTTClient, MegaProtocolError, MegaUpstreamError
from app.logging_config import configure_logging


def test_retrieve_by_id_applies_bearer_without_logging_secret(
    mega_payload,
    caplog,
) -> None:
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["authorization"] = request.headers.get("Authorization")
        seen["url"] = str(request.url)
        return httpx.Response(200, json=mega_payload)

    token = "test-token"  # pragma: allowlist secret
    configure_logging()
    with caplog.at_level(logging.INFO):
        with MegaOTTClient(
            base_url="https://megaott.net/api",
            token=token,
            transport=httpx.MockTransport(handler),
        ) as client:
            subscription = client.get_subscription(121)

    assert seen["authorization"] == f"Bearer {token}"
    assert seen["url"].endswith("/v1/subscriptions/121")
    assert subscription.id == 121
    assert subscription.username == "authorized-user"
    assert token not in caplog.text
    assert mega_payload["password"] not in caplog.text


def test_username_discovery_scans_pages_without_secret_output() -> None:
    seen = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(str(request.url))
        page = int(request.url.params["page"])
        if page == 1:
            return httpx.Response(
                200,
                json={
                    "data": [
                        {"id": 10, "username": "other-a", "password": "secret-a"},
                        {"id": 11, "username": "other-b", "password": "secret-b"},
                    ]
                },
            )
        if page == 2:
            return httpx.Response(
                200,
                json={
                    "data": [
                        {"id": 12, "username": "target-user", "password": "secret-c"},
                    ]
                },
            )
        return httpx.Response(200, json={"data": []})

    with MegaOTTClient(
        base_url="https://megaott.net/api",
        token="test-token",  # pragma: allowlist secret
        transport=httpx.MockTransport(handler),
    ) as client:
        subscription_id = client.find_subscription_id_by_username(
            "target-user",
            per_page=2,
        )

    assert subscription_id == 12
    assert len(seen) == 2
    assert all("target-user" not in url for url in seen)


def test_username_discovery_rejects_duplicate_matches() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        page = int(request.url.params["page"])
        payload = (
            [{"id": 10, "username": "same"}, {"id": 11, "username": "same"}]
            if page == 1
            else []
        )
        return httpx.Response(200, json=payload)

    with MegaOTTClient(
        base_url="https://megaott.net/api",
        token="test-token",  # pragma: allowlist secret
        transport=httpx.MockTransport(handler),
    ) as client:
        with pytest.raises(MegaProtocolError, match="multiple"):
            client.find_subscription_id_by_username("same", per_page=2)


def test_client_rejects_non_https_base() -> None:
    token = "test-token"  # pragma: allowlist secret
    with pytest.raises(ValueError, match="must be HTTPS"):
        MegaOTTClient(
            base_url="http://mega.example.com/api",
            token=token,
        )


def test_malformed_response_rejected(mega_payload) -> None:
    bad = dict(mega_payload)
    bad.pop("dns_link")
    transport = httpx.MockTransport(lambda _request: httpx.Response(200, json=bad))
    token = "test-token"  # pragma: allowlist secret
    with MegaOTTClient(
        base_url="https://megaott.net/api",
        token=token,
        transport=transport,
    ) as client:
        with pytest.raises(MegaProtocolError):
            client.get_subscription(121)


@pytest.mark.parametrize("status", [400, 404, 500, 503])
def test_upstream_http_errors_are_sanitized(status) -> None:
    transport = httpx.MockTransport(
        lambda _request: httpx.Response(
            status,
            json={"error": "secret"},
        )
    )
    token = "test-token"  # pragma: allowlist secret
    with MegaOTTClient(
        base_url="https://megaott.net/api",
        token=token,
        transport=transport,
    ) as client:
        with pytest.raises(
            MegaUpstreamError,
            match="retrieval failed",
        ) as exc_info:
            client.get_subscription(121)
    assert "secret" not in str(exc_info.value)


def test_timeout_is_sanitized() -> None:
    def timeout(_request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("sensitive upstream detail")

    token = "test-token"  # pragma: allowlist secret
    with MegaOTTClient(
        base_url="https://megaott.net/api",
        token=token,
        transport=httpx.MockTransport(timeout),
    ) as client:
        with pytest.raises(
            MegaUpstreamError,
            match="retrieval failed",
        ) as exc_info:
            client.get_subscription(121)
    assert "sensitive upstream detail" not in str(exc_info.value)


def test_id_mismatch_fails_closed(mega_payload) -> None:
    payload = dict(mega_payload)
    payload["id"] = 999
    transport = httpx.MockTransport(lambda _request: httpx.Response(200, json=payload))
    token = "test-token"  # pragma: allowlist secret
    with MegaOTTClient(
        base_url="https://megaott.net/api",
        token=token,
        transport=transport,
    ) as client:
        with pytest.raises(MegaProtocolError, match="id mismatch"):
            client.get_subscription(121)


def test_non_m3u_subscription_rejected(mega_payload) -> None:
    payload = dict(mega_payload)
    payload["type"] = "MAG"
    transport = httpx.MockTransport(lambda _request: httpx.Response(200, json=payload))
    token = "test-token"  # pragma: allowlist secret
    with MegaOTTClient(
        base_url="https://megaott.net/api",
        token=token,
        transport=transport,
    ) as client:
        with pytest.raises(MegaProtocolError):
            client.get_subscription(121)
