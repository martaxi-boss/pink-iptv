from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, SecretStr


class ResolveCode(StrEnum):
    SUCCESS = "SUCCESS"
    INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    EXPIRED = "EXPIRED"
    DISABLED = "DISABLED"
    DNS_UNREACHABLE = "DNS_UNREACHABLE"
    UPSTREAM_ERROR = "UPSTREAM_ERROR"


class ResolveRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    username: str
    password: SecretStr


class ResolveResponse(BaseModel):
    code: ResolveCode
    session_token: str | None = None
    session_expires_at: datetime | None = None
    xtream_base_url: str | None = None
    account_expires_at: datetime | None = None
