import os
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime

import pytest
from fastapi import HTTPException
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import Session

from app.auth_rate_limit import charge_auth_attempt
from app.vpn_rate_limit import charge_vpn_attempt


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
        "created_at",
        "last_authenticated_at",
    }
    assert not {"password", "private_key", "device_token"}.intersection(vpn_columns)
    vpn_column_details = {
        column["name"]: column for column in inspect(engine).get_columns("vpn_installations")
    }
    assert vpn_column_details["address"]["nullable"] is True
    assert vpn_column_details["last_authenticated_at"]["type"].timezone is True
    release_columns = {
        column["name"] for column in inspect(engine).get_columns("vpn_address_releases")
    }
    assert release_columns == {"id", "installation_id", "address", "released_at", "cause"}
    vpn_unique = {
        tuple(constraint["column_names"])
        for constraint in inspect(engine).get_unique_constraints("vpn_installations")
    }
    assert ("public_key",) in vpn_unique
    assert ("address",) in vpn_unique

    auth_columns = {column["name"] for column in inspect(engine).get_columns("auth_rate_windows")}
    assert auth_columns == {"bucket_key", "window_number", "attempts"}
    assert not {"username", "password", "ip_address"}.intersection(auth_columns)

    vpn_rate_columns = {
        column["name"] for column in inspect(engine).get_columns("vpn_rate_windows")
    }
    assert vpn_rate_columns == {"bucket_key", "window_number", "attempts"}
    assert not {"token", "password", "public_key", "ip_address"}.intersection(vpn_rate_columns)


@pytest.mark.postgres
def test_authentication_budget_serializes_postgres_connections(settings) -> None:
    if os.environ.get("PINK_POSTGRES_TEST") != "1":
        pytest.skip("cross-connection PostgreSQL proof runs only in CI")
    database_url = os.environ["DATABASE_URL"]
    assert database_url.rsplit("/", 1)[-1] == "pink_test"
    engine = create_engine(database_url)
    limited = settings.model_copy(update={"auth_rate_per_username": 2, "auth_rate_global": 100})
    fixed = datetime(2026, 10, 8, 12, 0, tzinfo=UTC)

    with engine.begin() as connection:
        connection.execute(text("DELETE FROM auth_rate_windows"))

    def attempt(_number: int) -> int:
        with Session(engine) as session:
            try:
                charge_auth_attempt(session, limited, "concurrent-pseudonym", now=fixed)
            except HTTPException as blocked:
                return blocked.status_code
            return 200

    with ThreadPoolExecutor(max_workers=6) as workers:
        results = list(workers.map(attempt, range(6)))
    assert results.count(200) == 2
    assert results.count(429) == 4

    with engine.connect() as connection:
        rows = connection.execute(text("SELECT bucket_key, attempts FROM auth_rate_windows")).all()
    assert len(rows) == 2
    assert {attempts for _, attempts in rows} == {2}
    assert all("concurrent-pseudonym" not in key for key, _ in rows)

    with engine.begin() as connection:
        connection.execute(text("DELETE FROM auth_rate_windows"))
    engine.dispose()


@pytest.mark.postgres
def test_vpn_budget_serializes_postgres_connections(settings) -> None:
    if os.environ.get("PINK_POSTGRES_TEST") != "1":
        pytest.skip("cross-connection PostgreSQL VPN proof runs only in CI")
    database_url = os.environ["DATABASE_URL"]
    assert database_url.rsplit("/", 1)[-1] == "pink_test"
    engine = create_engine(database_url)
    limited = settings.model_copy(update={"vpn_rate_per_credential": 2, "vpn_rate_global": 100})
    fixed = datetime(2026, 10, 9, 12, 0, tzinfo=UTC)

    with engine.begin() as connection:
        connection.execute(text("DELETE FROM vpn_rate_windows"))

    def attempt(_number: int) -> int:
        with Session(engine) as session:
            try:
                charge_vpn_attempt(session, limited, "fixture-token-never-persist", now=fixed)
            except HTTPException as blocked:
                return blocked.status_code
            return 200

    with ThreadPoolExecutor(max_workers=6) as workers:
        results = list(workers.map(attempt, range(6)))
    assert results.count(200) == 2
    assert results.count(429) == 4
    with engine.connect() as connection:
        rows = connection.execute(text("SELECT bucket_key, attempts FROM vpn_rate_windows")).all()
    assert len(rows) == 2
    assert {attempts for _, attempts in rows} == {0, 2}
    assert any(key == "global" for key, _ in rows)
    assert all("fixture-token-never-persist" not in key for key, _ in rows)
    with engine.begin() as connection:
        connection.execute(text("DELETE FROM vpn_rate_windows"))
    engine.dispose()
