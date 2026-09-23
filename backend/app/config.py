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

    @field_validator("mega_ott_api_base")
    @classmethod
    def mega_base_must_be_https(cls, value: str) -> str:
        parsed = urlsplit(value)
        if parsed.scheme.lower() != "https" or not parsed.hostname:
            raise ValueError("MEGA_OTT_API_BASE must be an HTTPS URL with a hostname")
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError(
                "MEGA_OTT_API_BASE must not contain credentials, query, or fragment"
            )
        return value.rstrip("/")

    @model_validator(mode="after")
    def reject_unsafe_non_test_defaults(self) -> "Settings":
        if self.app_env.casefold() != "test":
            signing_key = self.session_signing_key.get_secret_value().strip()
            if not signing_key or signing_key == "change-me":
                raise ValueError(
                    "SESSION_SIGNING_KEY must be set to a non-default value"
                )
            if len(signing_key.encode()) < 32:
                raise ValueError("SESSION_SIGNING_KEY must be at least 32 bytes")
        return self

    def require_mega_token(self) -> str:
        if self.mega_ott_api_token is None:
            raise RuntimeError("Mega integration requires MEGA_OTT_API_TOKEN")
        token = self.mega_ott_api_token.get_secret_value().strip()
        if not token or token == "change-me":
            raise RuntimeError(
                "Mega integration requires a non-default MEGA_OTT_API_TOKEN"
            )
        return token


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]
