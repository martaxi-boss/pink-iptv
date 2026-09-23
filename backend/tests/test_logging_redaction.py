import logging

import httpx

from app.clients.mega import MegaOTTClient
from app.clients.xtream import XtreamClient
from app.logging_config import configure_logging


def public_resolver(_hostname: str, _port: int):
    return ["93.184.216.34"]


def test_customer_password_and_auth_query_never_appear_in_logs(
    caplog,
) -> None:
    password = "customer-password"  # pragma: allowlist secret
    configure_logging()
    transport = httpx.MockTransport(
        lambda _request: httpx.Response(
            200,
            json={"user_info": {"auth": 0}},
        )
    )
    with caplog.at_level(logging.DEBUG):
        client = XtreamClient(
            transport=transport,
            resolver=public_resolver,
        )
        client.authenticate(
            dns_link="https://stream.example.com",
            username="customer-user",
            password=password,
        )
        client.close()
    assert password not in caplog.text
    assert "customer-user" not in caplog.text
    assert "player_api.php?" not in caplog.text


def test_mega_token_and_raw_password_never_appear_in_logs(
    caplog,
    mega_payload,
) -> None:
    token = "mega-token"  # pragma: allowlist secret
    configure_logging()
    transport = httpx.MockTransport(
        lambda _request: httpx.Response(200, json=mega_payload)
    )
    with caplog.at_level(logging.DEBUG):
        with MegaOTTClient(
            base_url="https://megaott.net/api",
            token=token,
            transport=transport,
        ) as client:
            client.get_subscription(121)
    assert token not in caplog.text
    assert mega_payload["password"] not in caplog.text
    assert "Authorization" not in caplog.text
