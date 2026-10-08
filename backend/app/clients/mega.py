import json
import logging
from datetime import datetime
from typing import Any
from urllib.parse import urlsplit

import httpx
from pydantic import BaseModel, ConfigDict, SecretStr, ValidationError, field_validator

logger = logging.getLogger(__name__)
_MAX_MEGA_JSON_BYTES = 2 * 1024 * 1024


class MegaClientError(RuntimeError):
    pass


class MegaUpstreamError(MegaClientError):
    pass


class MegaProtocolError(MegaClientError):
    pass


class MegaSubscriptionRef(BaseModel):
    model_config = ConfigDict(extra="ignore")

    id: int
    username: str

    @field_validator("username")
    @classmethod
    def require_non_empty_username(cls, value: str) -> str:
        if not value:
            raise ValueError("required field is empty")
        return value


class MegaSubscription(BaseModel):
    model_config = ConfigDict(extra="ignore")

    type: str
    id: int
    username: str
    password: SecretStr | None = None
    expiring_at: datetime | None = None
    dns_link: str
    dns_link_for_samsung_lg: str | None = None

    @field_validator("type")
    @classmethod
    def require_m3u(cls, value: str) -> str:
        if value.casefold() != "m3u":
            raise ValueError("subscription is not M3U/Xtream-compatible")
        return value

    @field_validator("username", "dns_link")
    @classmethod
    def require_non_empty(cls, value: str) -> str:
        if not value:
            raise ValueError("required field is empty")
        return value

    @field_validator("expiring_at", mode="before")
    @classmethod
    def parse_mega_timestamp(cls, value: Any) -> Any:
        if value in (None, ""):
            return None
        if isinstance(value, datetime):
            parsed = value
        elif isinstance(value, str):
            try:
                parsed = datetime.strptime(value, "%Y-%m-%d %H:%M:%S GMT%z")
            except ValueError:
                try:
                    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
                except ValueError as exc:
                    raise ValueError("invalid expiring_at timestamp") from exc
        else:
            return value
        if parsed.tzinfo is None or parsed.utcoffset() is None:
            raise ValueError("expiring_at must be timezone-aware")
        return parsed


class MegaOTTClient:
    def __init__(
        self,
        *,
        base_url: str,
        token: str,
        timeout_seconds: float = 10.0,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        parsed = urlsplit(base_url)
        if (
            parsed.scheme.casefold() != "https"
            or not parsed.hostname
            or parsed.username is not None
            or parsed.password is not None
            or parsed.query
            or parsed.fragment
        ):
            raise ValueError("Mega base URL must be HTTPS with no credentials, query, or fragment")
        self._base_url = base_url.rstrip("/")
        self._client = httpx.Client(
            headers={
                "Accept": "application/json",
                "Authorization": f"Bearer {token}",
            },
            timeout=httpx.Timeout(timeout_seconds),
            follow_redirects=False,
            transport=transport,
            trust_env=False,
        )

    def close(self) -> None:
        self._client.close()

    def __enter__(self) -> "MegaOTTClient":
        return self

    def __exit__(self, *_args: object) -> None:
        self.close()

    def _read_bounded_json(
        self,
        url: str,
        *,
        operation: str,
        params: dict[str, str] | None = None,
    ) -> Any:
        # Client.get() buffers unbounded bodies before application checks.
        try:
            with self._client.stream("GET", url, params=params) as response:
                if response.status_code != 200:
                    raise MegaUpstreamError(f"Mega subscription {operation} failed")
                data = bytearray()
                for chunk in response.iter_bytes():
                    if len(data) + len(chunk) > _MAX_MEGA_JSON_BYTES:
                        raise MegaProtocolError("Mega response exceeded size limit")
                    data.extend(chunk)
        except (httpx.TimeoutException, httpx.RequestError) as exc:
            raise MegaUpstreamError(f"Mega subscription {operation} failed") from exc
        try:
            return json.loads(data)
        except (ValueError, UnicodeDecodeError) as exc:
            raise MegaProtocolError("Mega returned invalid JSON") from exc

    def get_subscription(self, mega_subscription_id: int) -> MegaSubscription:
        url = f"{self._base_url}/v1/subscriptions/{mega_subscription_id}"
        payload = self._read_bounded_json(url, operation="retrieval")
        try:
            subscription = MegaSubscription.model_validate(payload)
        except ValidationError as exc:
            raise MegaProtocolError("Mega returned an invalid subscription schema") from exc
        if subscription.id != mega_subscription_id:
            raise MegaProtocolError("Mega subscription id mismatch")

        logger.info(
            "Mega subscription metadata retrieved for id=%s",
            mega_subscription_id,
        )
        return subscription

    def list_subscription_refs(
        self,
        *,
        page: int,
        per_page: int = 100,
    ) -> list[MegaSubscriptionRef]:
        if page < 1:
            raise ValueError("page must be positive")
        if per_page < 1 or per_page > 100:
            raise ValueError("per_page must be between 1 and 100")

        url = f"{self._base_url}/v1/subscriptions"
        payload = self._read_bounded_json(
            url,
            operation="listing",
            params={"page": str(page), "per_page": str(per_page)},
        )

        raw_items: Any
        if isinstance(payload, list):
            raw_items = payload
        elif isinstance(payload, dict):
            raw_items = next(
                (
                    payload[key]
                    for key in ("data", "items", "subscriptions", "results")
                    if isinstance(payload.get(key), list)
                ),
                None,
            )
        else:
            raw_items = None

        if raw_items is None:
            raise MegaProtocolError("Mega returned an unsupported subscription-list schema")
        if len(raw_items) > per_page:
            raise MegaProtocolError("Mega returned an oversized subscription-list page")

        try:
            return [MegaSubscriptionRef.model_validate(item) for item in raw_items]
        except ValidationError as exc:
            raise MegaProtocolError("Mega returned an invalid subscription-list item") from exc

    def find_subscription_id_by_username(
        self,
        username: str,
        *,
        max_pages: int = 100,
        per_page: int = 100,
    ) -> int | None:
        if not username:
            return None
        if max_pages < 1:
            raise ValueError("max_pages must be positive")

        seen_ids: set[int] = set()
        matched_id: int | None = None

        for page in range(1, max_pages + 1):
            refs = self.list_subscription_refs(page=page, per_page=per_page)
            new_ids = 0
            for ref in refs:
                if ref.id in seen_ids:
                    continue
                seen_ids.add(ref.id)
                new_ids += 1
                if ref.username == username:
                    if matched_id is not None and matched_id != ref.id:
                        raise MegaProtocolError("Mega username matched multiple subscriptions")
                    matched_id = ref.id

            if not refs or new_ids == 0 or len(refs) < per_page:
                break

        return matched_id
