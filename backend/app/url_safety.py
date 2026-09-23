import ipaddress
import re
import socket
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from urllib.parse import SplitResult, urlsplit, urlunsplit


class UnsafeOutboundURL(ValueError):
    pass


class DNSResolutionFailure(RuntimeError):
    pass


Resolver = Callable[[str, int], Iterable[str]]
_HOST_LABEL = re.compile(r"^(?!-)[A-Za-z0-9-]{1,63}(?<!-)$")


@dataclass(frozen=True)
class ResolvedOutboundTarget:
    origin: str
    hostname: str
    port: int
    addresses: tuple[str, ...]


def system_resolver(hostname: str, port: int) -> list[str]:
    try:
        results = socket.getaddrinfo(hostname, port, type=socket.SOCK_STREAM)
    except socket.gaierror as exc:
        raise DNSResolutionFailure("DNS resolution failed") from exc
    return sorted({item[4][0] for item in results})


def _is_public_address(address: str) -> bool:
    try:
        return bool(ipaddress.ip_address(address).is_global)
    except ValueError:
        return False


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


def _port_for(parsed: SplitResult) -> int:
    try:
        return parsed.port or (443 if parsed.scheme.casefold() == "https" else 80)
    except ValueError as exc:
        raise UnsafeOutboundURL("invalid port") from exc


def _validated_addresses(addresses: Iterable[str]) -> tuple[str, ...]:
    normalized: list[str] = []
    for address in addresses:
        try:
            parsed = ipaddress.ip_address(address)
        except ValueError as exc:
            raise UnsafeOutboundURL("resolver returned an invalid address") from exc
        if not parsed.is_global:
            raise UnsafeOutboundURL("resolved destination is not public")
        canonical = parsed.compressed
        if canonical not in normalized:
            normalized.append(canonical)
    if not normalized:
        raise DNSResolutionFailure("DNS resolution returned no addresses")
    return tuple(normalized)


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
    if parsed.path not in {"", "/"}:
        raise UnsafeOutboundURL("dns_link path is not allowed")

    hostname = parsed.hostname.rstrip(".")
    _validate_hostname_syntax(hostname)

    try:
        literal_ip = ipaddress.ip_address(hostname)
    except ValueError:
        literal_ip = None
    if literal_ip is not None and not literal_ip.is_global:
        raise UnsafeOutboundURL("non-public destination is not allowed")

    port = _port_for(parsed)
    if resolver is not None:
        _validated_addresses(resolver(hostname, port))

    return urlunsplit((parsed.scheme, parsed.netloc, "", "", ""))


def resolve_dns_link_target(
    dns_link: str,
    *,
    resolver: Resolver = system_resolver,
) -> ResolvedOutboundTarget:
    origin = validate_dns_link(dns_link)
    parsed = urlsplit(origin)
    hostname = parsed.hostname
    if hostname is None:  # pragma: no cover
        raise UnsafeOutboundURL("hostname is required")
    port = _port_for(parsed)

    try:
        literal_ip = ipaddress.ip_address(hostname)
    except ValueError:
        addresses = _validated_addresses(resolver(hostname, port))
    else:
        addresses = _validated_addresses([literal_ip.compressed])

    return ResolvedOutboundTarget(
        origin=origin,
        hostname=hostname.rstrip("."),
        port=port,
        addresses=addresses,
    )


def player_api_url(dns_link: str) -> str:
    return f"{dns_link.rstrip('/')}/player_api.php"
