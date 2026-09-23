import jwt

from app.security import issue_session_token


def test_session_token_contains_no_credentials(settings) -> None:
    token, expires_at = issue_session_token(42, settings)
    claims = jwt.decode(
        token,
        settings.session_signing_key.get_secret_value(),
        algorithms=["HS256"],
        issuer="pink-iptv",
    )
    assert claims["sub"] == "mapping:42"
    assert claims["exp"] - claims["iat"] <= 300
    assert expires_at is not None
    forbidden = {
        "username",
        "password",
        "mega_subscription_id",
        "mega_token",
        "dns_link",
    }
    assert forbidden.isdisjoint(claims)
