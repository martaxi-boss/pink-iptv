from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.clients.xtream import (
    XtreamClient,
    XtreamDNSUnreachable,
    XtreamUpstreamError,
)
from app.config import Settings
from app.models import SubscriptionMapping
from app.schemas import ResolveCode, ResolveResponse
from app.security import issue_session_token


class SessionResolver:
    def __init__(
        self,
        session: Session,
        xtream_client: XtreamClient,
        settings: Settings,
    ) -> None:
        self._session = session
        self._xtream_client = xtream_client
        self._settings = settings

    def resolve(self, *, username: str, password: str) -> ResolveResponse:
        mapping = self._session.scalar(
            select(SubscriptionMapping).where(
                SubscriptionMapping.username == username
            )
        )
        if mapping is None:
            return ResolveResponse(code=ResolveCode.INVALID_CREDENTIALS)

        if mapping.expiring_at is not None:
            expiry = mapping.expiring_at
            if expiry.tzinfo is None:
                expiry = expiry.replace(tzinfo=timezone.utc)
            if expiry <= datetime.now(timezone.utc):
                return ResolveResponse(code=ResolveCode.EXPIRED)

        try:
            result = self._xtream_client.authenticate(
                dns_link=mapping.dns_link,
                username=username,
                password=password,
            )
        except XtreamDNSUnreachable:
            return ResolveResponse(code=ResolveCode.DNS_UNREACHABLE)
        except XtreamUpstreamError:
            return ResolveResponse(code=ResolveCode.UPSTREAM_ERROR)

        if result.code != "SUCCESS":
            return ResolveResponse(code=ResolveCode(result.code))

        token, session_expires_at = issue_session_token(
            mapping.id,
            self._settings,
        )
        return ResolveResponse(
            code=ResolveCode.SUCCESS,
            session_token=token,
            session_expires_at=session_expires_at,
            xtream_base_url=mapping.dns_link,
            account_expires_at=result.account_expires_at,
        )
