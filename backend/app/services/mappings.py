from dataclasses import dataclass
from datetime import UTC, datetime
from hashlib import sha256
from urllib.parse import urlsplit

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.clients.mega import MegaOTTClient
from app.models import SubscriptionMapping


class MappingConflictError(RuntimeError):
    pass


@dataclass(frozen=True)
class ImportEvidence:
    mega_subscription_id: int
    mapping_id: int
    username_sha256: str
    dns_link_sha256: str
    dns_scheme: str


def import_subscription(
    session: Session,
    mega_client: MegaOTTClient,
    mega_subscription_id: int,
) -> SubscriptionMapping:
    subscription = mega_client.get_subscription(mega_subscription_id)
    now = datetime.now(UTC)

    mapping = session.scalar(
        select(SubscriptionMapping).where(
            SubscriptionMapping.mega_subscription_id == mega_subscription_id
        )
    )
    username_owner = session.scalar(
        select(SubscriptionMapping).where(SubscriptionMapping.username == subscription.username)
    )
    if username_owner is not None and (mapping is None or username_owner.id != mapping.id):
        raise MappingConflictError("Mega username is already mapped to another subscription")

    if mapping is None:
        mapping = SubscriptionMapping(
            mega_subscription_id=subscription.id,
            username=subscription.username,
            dns_link=subscription.dns_link,
            dns_link_samsung_lg=subscription.dns_link_for_samsung_lg,
            expiring_at=subscription.expiring_at,
            last_synced_at=now,
        )
        session.add(mapping)
    else:
        mapping.username = subscription.username
        mapping.dns_link = subscription.dns_link
        mapping.dns_link_samsung_lg = subscription.dns_link_for_samsung_lg
        mapping.expiring_at = subscription.expiring_at
        mapping.last_synced_at = now

    try:
        session.commit()
    except IntegrityError as exc:
        session.rollback()
        raise MappingConflictError("Subscription mapping uniqueness conflict") from exc
    session.refresh(mapping)
    return mapping


def discover_and_import_subscription(
    session: Session,
    mega_client: MegaOTTClient,
    username: str,
) -> SubscriptionMapping | None:
    existing = session.scalar(
        select(SubscriptionMapping).where(SubscriptionMapping.username == username)
    )
    if existing is not None:
        return existing

    mega_subscription_id = mega_client.find_subscription_id_by_username(username)
    if mega_subscription_id is None:
        return None

    try:
        return import_subscription(session, mega_client, mega_subscription_id)
    except MappingConflictError:
        session.rollback()
        # Concurrent first-login discovery may have inserted the same username.
        # Accept only the exact resulting username mapping; never choose another line.
        existing = session.scalar(
            select(SubscriptionMapping).where(SubscriptionMapping.username == username)
        )
        if existing is not None:
            return existing
        raise


def build_import_evidence(mapping: SubscriptionMapping) -> ImportEvidence:
    return ImportEvidence(
        mega_subscription_id=mapping.mega_subscription_id,
        mapping_id=mapping.id,
        username_sha256=sha256(mapping.username.encode()).hexdigest(),
        dns_link_sha256=sha256(mapping.dns_link.encode()).hexdigest(),
        dns_scheme=urlsplit(mapping.dns_link).scheme.casefold(),
    )
