from functools import lru_cache
from urllib.parse import urlsplit

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=None, case_sensitive=False, extra="ignore")

    app_env: str = "development"
    database_url: str = Field(min_length=1)
    session_signing_key: SecretStr
    mega_ott_api_base: str = "https://megaott.net/api"
    mega_ott_api_token: SecretStr | None = None
    session_ttl_seconds: int = Field(default=300, ge=30, le=300)
    auth_rate_window_seconds: int = Field(default=60, ge=10, le=3600)
    auth_rate_per_username: int = Field(default=10, ge=1, le=100)
    auth_rate_global: int = Field(default=120, ge=10, le=10000)
    vpn_enabled: bool = False
    vpn_control_socket: str = "/run/pink-vpn/control.sock"
    vpn_server_public_key: str | None = None
    vpn_endpoint: str | None = None
    vpn_lease_seconds: int = Field(default=86400, ge=300, le=86400)
    vpn_max_installations_per_account: int = Field(default=5, ge=1, le=10)

    @field_validator("vpn_server_public_key")
    @classmethod
    def valid_vpn_public_key(cls, value: str | None) -> str | None:
        if value is not None:
            import base64

            raw = base64.b64decode(value, validate=True)
            if len(raw) != 32 or not any(raw):
                raise ValueError("Invalid VPN public key")
        return value

    @field_validator("vpn_endpoint")
    @classmethod
    def valid_vpn_endpoint(cls, value: str | None) -> str | None:
        if value is not None:
            import ipaddress

            host, port = value.rsplit(":", 1)
            address = ipaddress.ip_address(host)
            if address.version != 4 or not address.is_global or not 1 <= int(port) <= 65535:
                raise ValueError("Invalid VPN endpoint")
        return value

    @field_validator("mega_ott_api_base")
    @classmethod
    def mega_base_must_be_https(cls, value: str) -> str:
        parsed = urlsplit(value)
        if parsed.scheme.lower() != "https" or not parsed.hostname:
            raise ValueError("MEGA_OTT_API_BASE must be an HTTPS URL with a hostname")
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError("MEGA_OTT_API_BASE must not contain credentials, query, or fragment")
        return value.rstrip("/")

    @model_validator(mode="after")
    def reject_unsafe_non_test_defaults(self) -> "Settings":
        if self.app_env.casefold() != "test":
            signing_key = self.session_signing_key.get_secret_value().strip()
            if not signing_key or signing_key == "change-me":
                raise ValueError("SESSION_SIGNING_KEY must be set to a non-default value")
            if len(signing_key.encode()) < 32:
                raise ValueError("SESSION_SIGNING_KEY must be at least 32 bytes")
        return self

    def require_mega_token(self) -> str:
        if self.mega_ott_api_token is None:
            raise RuntimeError("Mega integration requires MEGA_OTT_API_TOKEN")
        token = self.mega_ott_api_token.get_secret_value().strip()
        if not token or token == "change-me":
            raise RuntimeError("Mega integration requires a non-default MEGA_OTT_API_TOKEN")
        return token


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]
