import ipaddress
import re
import socket
from collections.abc import Callable, Iterable
from urllib.parse import SplitResult, urlsplit, urlunsplit


class UnsafeOutboundURL(ValueError):
    pass


class DNSResolutionFailure(RuntimeError):
    pass


Resolver = Callable[[str, int], Iterable[str]]
_HOST_LABEL = re.compile(r"^(?!-)[A-Za-z0-9-]{1,63}(?<!-)$")


def system_resolver(hostname: str, port: int) -> list[str]:
    try:
        results = socket.getaddrinfo(hostname, port, type=socket.SOCK_STREAM)
    except socket.gaierror as exc:
        raise DNSResolutionFailure("DNS resolution failed") from exc
    return sorted({item[4][0] for item in results})


def _is_public_address(address: str) -> bool:
    return bool(ipaddress.ip_address(address).is_global)


def _validate_hostname_syntax(hostname: str) -> None:
    candidate = hostname.rstrip(".")
    if not candidate or len(candidate) > 253:
        raise UnsafeOutboundURL("invalid hostname")
    lowered = candidate.casefold()
    if lowered == "localhost" or lowered.endswith((".localhost", ".local", ".internal", ".lan")):
        raise UnsafeOutboundURL("local hostname is not allowed")
    try:
        ipaddress.ip_address(candidate)
        return
    except ValueError:
        pass
    if any(not _HOST_LABEL.fullmatch(label) for label in candidate.split(".")):
        raise UnsafeOutboundURL("invalid hostname")


def validate_dns_link(
    dns_link: str,
    *,
    resolver: Resolver | None = None,
) -> str:
    try:
        parsed: SplitResult = urlsplit(dns_link)
    except ValueError as exc:
        raise UnsafeOutboundURL("malformed URL") from exc

    if parsed.scheme.casefold() not in {"http", "https"}:
        raise UnsafeOutboundURL("unsupported URL scheme")
    if not parsed.hostname:
        raise UnsafeOutboundURL("hostname is required")
    if parsed.username is not None or parsed.password is not None:
        raise UnsafeOutboundURL("embedded credentials are not allowed")
    if parsed.query or parsed.fragment:
        raise UnsafeOutboundURL("query and fragment are not allowed")

    hostname = parsed.hostname.rstrip(".")
    _validate_hostname_syntax(hostname)

    try:
        literal_ip = ipaddress.ip_address(hostname)
    except ValueError:
        literal_ip = None
    if literal_ip is not None and not literal_ip.is_global:
        raise UnsafeOutboundURL("non-public destination is not allowed")

    try:
        port = parsed.port or (443 if parsed.scheme.casefold() == "https" else 80)
    except ValueError as exc:
        raise UnsafeOutboundURL("invalid port") from exc

    if resolver is not None:
        addresses = list(resolver(hostname, port))
        if not addresses:
            raise DNSResolutionFailure("DNS resolution returned no addresses")
        if any(not _is_public_address(address) for address in addresses):
            raise UnsafeOutboundURL("resolved destination is not public")

    normalized = urlunsplit((parsed.scheme, parsed.netloc, parsed.path.rstrip("/"), "", ""))
    return normalized.rstrip("/")


def player_api_url(dns_link: str) -> str:
    return f"{dns_link.rstrip('/')}/player_api.php"
