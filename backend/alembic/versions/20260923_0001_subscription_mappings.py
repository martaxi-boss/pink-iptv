"""create subscription mappings

Revision ID: 20260923_0001
Revises:
Create Date: 2026-09-23
"""

from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa

revision: str = "20260923_0001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "subscription_mappings",
        sa.Column("id", sa.Integer(), primary_key=True, nullable=False),
        sa.Column("mega_subscription_id", sa.BigInteger(), nullable=False),
        sa.Column("username", sa.Text(), nullable=False),
        sa.Column("dns_link", sa.Text(), nullable=False),
        sa.Column("dns_link_samsung_lg", sa.Text(), nullable=True),
        sa.Column("expiring_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("last_synced_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("CURRENT_TIMESTAMP"),
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("CURRENT_TIMESTAMP"),
        ),
        sa.UniqueConstraint(
            "mega_subscription_id",
            name="uq_subscription_mappings_mega_id",
        ),
        sa.UniqueConstraint("username", name="uq_subscription_mappings_username"),
    )


def downgrade() -> None:
    op.drop_table("subscription_mappings")
