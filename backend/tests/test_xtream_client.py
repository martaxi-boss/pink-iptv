import ssl

import httpcore
import httpx
import pytest

from app.clients.xtream import (
    XtreamClient,
    XtreamDNSUnreachable,
    XtreamUpstreamError,
)
from app.outbound_transport import PinnedHTTPTransport
from app.url_safety import DNSResolutionFailure, resolve_dns_link_target


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
        transport=httpx.MockTransport(lambda _request: httpx.Response(200)),
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


class RecordingStream(httpcore.MockStream):
    def __init__(self, buffer: list[bytes], peer_address: str, peer_port: int) -> None:
        super().__init__(buffer)
        self.peer_address = peer_address
        self.peer_port = peer_port
        self.writes: list[bytes] = []
        self.tls_server_hostname: str | None = None
        self.tls_context: ssl.SSLContext | None = None

    def write(self, buffer: bytes, timeout: float | None = None) -> None:
        self.writes.append(buffer)

    def start_tls(
        self,
        ssl_context: ssl.SSLContext,
        server_hostname: str | None = None,
        timeout: float | None = None,
    ) -> httpcore.NetworkStream:
        self.tls_context = ssl_context
        self.tls_server_hostname = server_hostname
        return self

    def get_extra_info(self, info: str):
        if info == "server_addr":
            return (self.peer_address, self.peer_port)
        return super().get_extra_info(info)


class RecordingBackend(httpcore.NetworkBackend):
    def __init__(self, *, peer_address: str) -> None:
        self.peer_address = peer_address
        self.connected_hosts: list[str] = []
        self.streams: list[RecordingStream] = []

    def connect_tcp(
        self,
        host: str,
        port: int,
        timeout: float | None = None,
        local_address: str | None = None,
        socket_options=None,
    ) -> httpcore.NetworkStream:
        self.connected_hosts.append(host)
        stream = RecordingStream(
            [b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\n\r\n{}"],
            peer_address=self.peer_address,
            peer_port=port,
        )
        self.streams.append(stream)
        return stream

    def connect_unix_socket(
        self,
        path: str,
        timeout: float | None = None,
        socket_options=None,
    ) -> httpcore.NetworkStream:
        raise AssertionError("unix socket must not be used")

    def sleep(self, seconds: float) -> None:
        pass


def test_pinned_transport_dials_validated_ip_and_preserves_tls_identity() -> None:
    target = resolve_dns_link_target(
        "https://stream.example.com:8443",
        resolver=lambda _host, _port: ["93.184.216.34"],
    )
    backend = RecordingBackend(peer_address="93.184.216.34")
    transport = PinnedHTTPTransport(target, network_backend=backend)

    with httpx.Client(transport=transport, trust_env=False) as client:
        response = client.get(f"{target.origin}/player_api.php")

    assert response.status_code == 200
    assert backend.connected_hosts == ["93.184.216.34"]
    stream = backend.streams[0]
    assert stream.tls_server_hostname == "stream.example.com"
    assert stream.tls_context is not None
    assert stream.tls_context.verify_mode == ssl.CERT_REQUIRED
    assert stream.tls_context.check_hostname is True
    wire = b"".join(stream.writes).lower()
    assert b"host: stream.example.com:8443\r\n" in wire


def test_pinned_transport_blocks_peer_swap_before_request_bytes() -> None:
    target = resolve_dns_link_target(
        "https://stream.example.com",
        resolver=lambda _host, _port: ["93.184.216.34"],
    )
    backend = RecordingBackend(peer_address="127.0.0.1")
    transport = PinnedHTTPTransport(target, network_backend=backend)

    with httpx.Client(transport=transport, trust_env=False) as client:
        with pytest.raises(httpx.RequestError, match="upstream connection failed"):
            client.get(f"{target.origin}/player_api.php")

    assert backend.connected_hosts == ["93.184.216.34"]
    assert backend.streams[0].writes == []


def test_pinned_http_transport_preserves_original_host_header() -> None:
    target = resolve_dns_link_target(
        "http://stream.example.com:8080",
        resolver=lambda _host, _port: ["93.184.216.34"],
    )
    backend = RecordingBackend(peer_address="93.184.216.34")
    transport = PinnedHTTPTransport(target, network_backend=backend)

    with httpx.Client(transport=transport, trust_env=False) as client:
        response = client.get(f"{target.origin}/player_api.php")

    assert response.status_code == 200
    wire = b"".join(backend.streams[0].writes).lower()
    assert b"host: stream.example.com:8080\r\n" in wire


class MissingPeerStream(RecordingStream):
    def get_extra_info(self, info: str):
        if info == "server_addr":
            return None
        return super().get_extra_info(info)


class MissingPeerBackend(RecordingBackend):
    def connect_tcp(
        self,
        host: str,
        port: int,
        timeout: float | None = None,
        local_address: str | None = None,
        socket_options=None,
    ) -> httpcore.NetworkStream:
        self.connected_hosts.append(host)
        stream = MissingPeerStream(
            [b"HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}"],
            peer_address="93.184.216.34",
            peer_port=port,
        )
        self.streams.append(stream)
        return stream


def test_pinned_transport_rejects_unverifiable_peer_before_request_bytes() -> None:
    target = resolve_dns_link_target(
        "http://stream.example.com",
        resolver=lambda _host, _port: ["93.184.216.34"],
    )
    backend = MissingPeerBackend(peer_address="93.184.216.34")
    transport = PinnedHTTPTransport(target, network_backend=backend)

    with httpx.Client(transport=transport, trust_env=False) as client:
        with pytest.raises(httpx.RequestError, match="upstream connection failed"):
            client.get(f"{target.origin}/player_api.php")

    assert backend.connected_hosts == ["93.184.216.34"]
    assert backend.streams[0].writes == []
