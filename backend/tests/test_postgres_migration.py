import os

import pytest
from sqlalchemy import create_engine, inspect


@pytest.mark.postgres
def test_alembic_schema_on_real_postgres() -> None:
    if os.environ.get("PINK_POSTGRES_TEST") != "1":
        pytest.skip("real PostgreSQL migration assertion runs in CI")
    database_url = os.environ["DATABASE_URL"]
    engine = create_engine(database_url)
    columns = {
        column["name"]: column for column in inspect(engine).get_columns("subscription_mappings")
    }
    assert set(columns) == {
        "id",
        "mega_subscription_id",
        "username",
        "dns_link",
        "dns_link_samsung_lg",
        "expiring_at",
        "last_synced_at",
        "created_at",
        "updated_at",
    }
    assert "password" not in columns
    assert columns["mega_subscription_id"]["nullable"] is False
    assert columns["username"]["nullable"] is False
    assert columns["dns_link"]["nullable"] is False
    assert columns["expiring_at"]["type"].timezone is True

    unique_sets = {
        tuple(sorted(constraint["column_names"]))
        for constraint in inspect(engine).get_unique_constraints("subscription_mappings")
    }
    assert ("mega_subscription_id",) in unique_sets
    assert ("username",) in unique_sets

    vpn_columns = {column["name"] for column in inspect(engine).get_columns("vpn_installations")}
    assert vpn_columns == {
        "id",
        "mapping_id",
        "public_key",
        "address",
        "token_sha256",
        "expires_at",
        "revoked_at",
    }
    assert not {"password", "private_key", "device_token"}.intersection(vpn_columns)
    vpn_unique = {
        tuple(constraint["column_names"])
        for constraint in inspect(engine).get_unique_constraints("vpn_installations")
    }
    assert ("public_key",) in vpn_unique
    assert ("address",) in vpn_unique

    auth_columns = {column["name"] for column in inspect(engine).get_columns("auth_rate_windows")}
    assert auth_columns == {"bucket_key", "window_number", "attempts"}
    assert not {"username", "password", "ip_address"}.intersection(auth_columns)
