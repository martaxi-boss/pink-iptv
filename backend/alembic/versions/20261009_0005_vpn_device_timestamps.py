"""Add minimal per-installation timestamps for privacy-safe customer slot recovery.

Revision ID: 20261009_0005
Revises: 20261009_0004
"""

import sqlalchemy as sa

from alembic import op

revision = "20261009_0005"
down_revision = "20261009_0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "vpn_installations",
        sa.Column(
            "created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
        ),
    )
    op.add_column(
        "vpn_installations",
        sa.Column(
            "last_authenticated_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
    )


def downgrade() -> None:
    op.drop_column("vpn_installations", "last_authenticated_at")
    op.drop_column("vpn_installations", "created_at")
