"""Add cross-worker anonymous authentication-budget windows.

Revision ID: 20261008_0003
Revises: 20261005_0002
"""

import sqlalchemy as sa

from alembic import op

revision = "20261008_0003"
down_revision = "20261005_0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "auth_rate_windows",
        sa.Column("bucket_key", sa.String(length=80), primary_key=True),
        sa.Column("window_number", sa.BigInteger(), nullable=False),
        sa.Column("attempts", sa.Integer(), nullable=False),
    )


def downgrade() -> None:
    op.drop_table("auth_rate_windows")
