from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class VpnInstallation(Base):
    __tablename__ = "vpn_installations"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    mapping_id: Mapped[int] = mapped_column(ForeignKey("subscription_mappings.id"), nullable=False)
    public_key: Mapped[str] = mapped_column(Text, unique=True, nullable=False)
    # NULL only after the root gateway confirms removal of the old peer.
    address: Mapped[str | None] = mapped_column(Text, unique=True, nullable=True)
    token_sha256: Mapped[str] = mapped_column(Text, nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class VpnAddressRelease(Base):
    """Append-only history of acknowledged gateway peer removals."""

    __tablename__ = "vpn_address_releases"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    installation_id: Mapped[int] = mapped_column(
        ForeignKey("vpn_installations.id"), nullable=False
    )
    address: Mapped[str] = mapped_column(Text, nullable=False)
    released_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    cause: Mapped[str] = mapped_column(String(16), nullable=False)


class AuthRateWindow(Base):
    """Shared PostgreSQL-backed login budgets; no plaintext identifiers."""

    __tablename__ = "auth_rate_windows"

    bucket_key: Mapped[str] = mapped_column(String(80), primary_key=True)
    window_number: Mapped[int] = mapped_column(BigInteger, nullable=False)
    attempts: Mapped[int] = mapped_column(Integer, nullable=False)


class SubscriptionMapping(Base):
    __tablename__ = "subscription_mappings"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    mega_subscription_id: Mapped[int] = mapped_column(
        BigInteger,
        unique=True,
        nullable=False,
    )
    username: Mapped[str] = mapped_column(Text, unique=True, nullable=False)
    dns_link: Mapped[str] = mapped_column(Text, nullable=False)
    dns_link_samsung_lg: Mapped[str | None] = mapped_column(Text, nullable=True)
    expiring_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    last_synced_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
        onupdate=func.now(),
    )
