import ipaddress
import ssl
from collections.abc import Iterable

import httpcore
import httpx

from app.url_safety import ResolvedOutboundTarget


class PinnedIPNetworkBackend(httpcore.NetworkBackend):
    def __init__(
        self,
        *,
        hostname: str,
        port: int,
        addresses: tuple[str, ...],
        backend: httpcore.NetworkBackend | None = None,
    ) -> None:
        if not addresses:
            raise ValueError("at least one validated address is required")
        normalized: list[str] = []
        for address in addresses:
            parsed = ipaddress.ip_address(address)
            if not parsed.is_global:
                raise ValueError("pinned address must be public")
            normalized.append(parsed.compressed)
        self._hostname = hostname.rstrip(".").casefold()
        self._port = port
        self._addresses = tuple(dict.fromkeys(normalized))
        self._backend = backend or httpcore.SyncBackend()

    def connect_tcp(
        self,
        host: str,
        port: int,
        timeout: float | None = None,
        local_address: str | None = None,
        socket_options: Iterable[tuple] | None = None,
    ) -> httpcore.NetworkStream:
        if host.rstrip(".").casefold() != self._hostname or port != self._port:
            raise httpcore.ConnectError("unexpected outbound destination")

        last_error: BaseException | None = None
        for address in self._addresses:
            try:
                stream = self._backend.connect_tcp(
                    host=address,
                    port=port,
                    timeout=timeout,
                    local_address=local_address,
                    socket_options=socket_options,
                )
            except (httpcore.ConnectError, httpcore.ConnectTimeout) as exc:
                last_error = exc
                continue

            peer = stream.get_extra_info("server_addr")
            if peer is None:
                stream.close()
                raise httpcore.ConnectError("connected peer address is unavailable")
            peer_address = str(peer[0]) if isinstance(peer, tuple) else str(peer)
            peer_port = peer[1] if isinstance(peer, tuple) and len(peer) > 1 else port
            try:
                parsed_peer = ipaddress.ip_address(peer_address)
            except ValueError:
                stream.close()
                raise httpcore.ConnectError("connected peer address is invalid") from None
            if not parsed_peer.is_global or parsed_peer.compressed != address or peer_port != port:
                stream.close()
                raise httpcore.ConnectError(
                    "connected peer did not match pinned public destination"
                )
            return stream

        if last_error is not None:
            raise last_error
        raise httpcore.ConnectError("unable to connect to pinned destination")

    def connect_unix_socket(
        self,
        path: str,
        timeout: float | None = None,
        socket_options: Iterable[tuple] | None = None,
    ) -> httpcore.NetworkStream:
        raise httpcore.ConnectError("unix sockets are not allowed")

    def sleep(self, seconds: float) -> None:
        self._backend.sleep(seconds)


class PinnedHTTPTransport(httpx.BaseTransport):
    def __init__(
        self,
        target: ResolvedOutboundTarget,
        *,
        network_backend: httpcore.NetworkBackend | None = None,
    ) -> None:
        ssl_context = httpx.create_ssl_context(verify=True, trust_env=False)
        if ssl_context.verify_mode == ssl.CERT_NONE or not ssl_context.check_hostname:
            raise RuntimeError("TLS verification must remain enabled")
        backend = PinnedIPNetworkBackend(
            hostname=target.hostname,
            port=target.port,
            addresses=target.addresses,
            backend=network_backend,
        )
        self._pool = httpcore.ConnectionPool(
            ssl_context=ssl_context,
            http1=True,
            http2=False,
            retries=0,
            network_backend=backend,
        )

    def handle_request(self, request: httpx.Request) -> httpx.Response:
        core_request = httpcore.Request(
            method=request.method,
            url=httpcore.URL(
                scheme=request.url.raw_scheme,
                host=request.url.raw_host,
                port=request.url.port,
                target=request.url.raw_path,
            ),
            headers=request.headers.raw,
            content=request.stream,
            extensions=request.extensions,
        )
        try:
            core_response = self._pool.handle_request(core_request)
            try:
                content = core_response.read()
            finally:
                core_response.close()
        except httpcore.TimeoutException as exc:
            raise httpx.TimeoutException(
                "upstream request timed out",
                request=request,
            ) from exc
        except (httpcore.NetworkError, httpcore.ProtocolError) as exc:
            raise httpx.RequestError(
                "upstream connection failed",
                request=request,
            ) from exc

        return httpx.Response(
            status_code=core_response.status,
            headers=core_response.headers,
            content=content,
            extensions=core_response.extensions,
            request=request,
        )

    def close(self) -> None:
        self._pool.close()
