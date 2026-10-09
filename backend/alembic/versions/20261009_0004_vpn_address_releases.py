"""Allow safe reuse of gateway-confirmed released VPN addresses.

Revision ID: 20261009_0004
Revises: 20261008_0003
"""

import sqlalchemy as sa

from alembic import op

revision = "20261009_0004"
down_revision = "20261008_0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Unique PostgreSQL/SQLite indexes accept multiple NULLs while still
    # protecting every currently assigned address against double allocation.
    op.alter_column("vpn_installations", "address", existing_type=sa.Text(), nullable=True)
    op.create_table(
        "vpn_address_releases",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column(
            "installation_id",
            sa.Integer(),
            sa.ForeignKey("vpn_installations.id"),
            nullable=False,
        ),
        sa.Column("address", sa.Text(), nullable=False),
        sa.Column("released_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("cause", sa.String(length=16), nullable=False),
    )


def downgrade() -> None:
    connection = op.get_bind()
    count = connection.execute(
        sa.text("SELECT COUNT(*) FROM vpn_installations WHERE address IS NULL")
    ).scalar_one()
    if count:
        raise RuntimeError(
            "Cannot downgrade released VPN addresses without a separate safe ownership migration"
        )
    op.drop_table("vpn_address_releases")
    op.alter_column("vpn_installations", "address", existing_type=sa.Text(), nullable=False)
