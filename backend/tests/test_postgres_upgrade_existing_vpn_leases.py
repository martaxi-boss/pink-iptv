"""Upgrade a real PostgreSQL 0002 database with existing VPN leases to 0006.

This test touches only the isolated CI pink_test database, never production.
"""

import base64
import os
import subprocess
from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy import create_engine, text


@pytest.mark.postgres
def test_existing_vpn_installation_survives_additive_0002_to_0006_migrations():
    if os.environ.get("PINK_POSTGRES_TEST") != "1":
        pytest.skip("in-place migration proof requires isolated CI PostgreSQL")

    database_url = os.environ["DATABASE_URL"]
    assert database_url.rsplit("/", 1)[-1] == "pink_test"
    engine = create_engine(database_url)
    key = base64.b64encode(bytes(range(32))).decode()
    expected_address = "10.66.0.170"
    expected_token_hash = "a" * 64  # pragma: allowlist secret -- synthetic one-way fixture
    now = datetime.now(UTC)
    fixture_mapping_id = None

    try:
        with engine.connect() as connection:
            assert connection.execute(
                text("SELECT version_num FROM alembic_version")
            ).scalar_one() == "20261009_0006"
            assert connection.execute(
                text("SELECT COUNT(*) FROM vpn_installations")
            ).scalar_one() == 0

        subprocess.run(["alembic", "downgrade", "20261005_0002"], check=True)
        with engine.begin() as connection:
            fixture_mapping_id = connection.execute(
                text(
                    "INSERT INTO subscription_mappings "
                    "(mega_subscription_id, username, dns_link, expiring_at, last_synced_at) "
                    "VALUES (:provider_id, :username, :dns_link, :expires_at, :synced_at) "
                    "RETURNING id"
                ),
                {
                    "provider_id": 900080,
                    "username": "fixture-upgrade-080",
                    "dns_link": "https://fixture.example.invalid",
                    "expires_at": now + timedelta(days=30),
                    "synced_at": now,
                },
            ).scalar_one()
            connection.execute(
                text(
                    "INSERT INTO vpn_installations "
                    "(mapping_id, public_key, address, token_sha256, expires_at, revoked_at) "
                    "VALUES (:mapping_id, :public_key, :address, :token_hash, :expires_at, NULL)"
                ),
                {
                    "mapping_id": fixture_mapping_id,
                    "public_key": key,
                    "address": expected_address,
                    "token_hash": expected_token_hash,
                    "expires_at": now + timedelta(hours=12),
                },
            )
        subprocess.run(["alembic", "upgrade", "head"], check=True)

        with engine.connect() as connection:
            assert connection.execute(
                text("SELECT version_num FROM alembic_version")
            ).scalar_one() == "20261009_0006"
            row = connection.execute(
                text(
                    "SELECT mapping_id, public_key, address, token_sha256, revoked_at, "
                    "created_at, last_authenticated_at FROM vpn_installations "
                    "WHERE public_key=:key"
                ),
                {"key": key},
            ).one()
            assert row.mapping_id == fixture_mapping_id
            assert row.public_key == key
            assert row.address == expected_address
            assert row.token_sha256 == expected_token_hash
            assert row.revoked_at is None
            assert row.created_at is not None and row.last_authenticated_at is not None
            assert connection.execute(
                text("SELECT COUNT(*) FROM vpn_address_releases")
            ).scalar_one() == 0
            assert connection.execute(
                text("SELECT COUNT(*) FROM auth_rate_windows")
            ).scalar_one() == 0
            assert connection.execute(
                text("SELECT COUNT(*) FROM vpn_rate_windows")
            ).scalar_one() == 0
            assert connection.execute(
                text(
                    "SELECT is_nullable FROM information_schema.columns "
                    "WHERE table_name='vpn_installations' AND column_name='address'"
                )
            ).scalar_one() == "YES"
    finally:
        # Always return CI to head, even if an assertion fails after downgrade.
        command.upgrade(config, "head")
        if fixture_mapping_id is not None:
            with engine.begin() as connection:
                connection.execute(
                    text("DELETE FROM vpn_installations WHERE mapping_id=:id"),
                    {"id": fixture_mapping_id},
                )
                connection.execute(
                    text("DELETE FROM subscription_mappings WHERE id=:id"),
                    {"id": fixture_mapping_id},
                )
        engine.dispose()
