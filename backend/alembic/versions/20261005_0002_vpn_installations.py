"""Add public-key-only installation leases; leave IPTV mapping schema unchanged."""

import sqlalchemy as sa

from alembic import op

revision = "20261005_0002"
down_revision = "20260923_0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "vpn_installations",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column(
            "mapping_id", sa.Integer(), sa.ForeignKey("subscription_mappings.id"), nullable=False
        ),
        sa.Column("public_key", sa.Text(), nullable=False, unique=True),
        sa.Column("address", sa.Text(), nullable=False, unique=True),
        sa.Column("token_sha256", sa.Text(), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    op.drop_table("vpn_installations")
