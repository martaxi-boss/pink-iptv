from datetime import datetime

from sqlalchemy import BigInteger, DateTime, Integer, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


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
