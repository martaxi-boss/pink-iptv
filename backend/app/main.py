from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from app.auth_rate_limit import charge_auth_attempt
from app.clients.mega import MegaOTTClient
from app.clients.xtream import XtreamClient
from app.config import Settings, get_settings
from app.db import build_session_factory, get_db
from app.logging_config import configure_logging
from app.schemas import ResolveRequest, ResolveResponse
from app.services.session import SessionResolver
from app.vpn import router as vpn_router
from app.vpn_rate_limit import charge_vpn_attempt


def create_app(settings: Settings | None = None) -> FastAPI:
    runtime_settings = settings or get_settings()
    configure_logging()

    app = FastAPI(title="PINK IPTV Backend", version="0.1.0")
    app.state.settings = runtime_settings
    app.include_router(vpn_router)
    app.state.session_factory = build_session_factory(runtime_settings.database_url)

    @app.middleware("http")
    async def vpn_global_admission(request: Request, call_next):
        if request.url.path.startswith("/v1/vpn/"):
            # This middleware runs before Pydantic parses the body, so even
            # malformed/unauthenticated VPN traffic charges the global budget.
            with app.state.session_factory() as session:
                try:
                    charge_vpn_attempt(session, runtime_settings)
                except HTTPException as failure:
                    return JSONResponse(
                        status_code=failure.status_code,
                        content={"detail": failure.detail},
                        headers=failure.headers,
                    )
        return await call_next(request)

    app.state.xtream_client_factory = XtreamClient
    app.state.mega_client_factory = lambda: MegaOTTClient(
        base_url=runtime_settings.mega_ott_api_base,
        token=runtime_settings.require_mega_token(),
    )

    @app.exception_handler(RequestValidationError)
    async def invalid_request(_request: Request, _error: RequestValidationError) -> JSONResponse:
        # Validation inputs and even extra field names can contain credentials.
        # Never serialize the error object or original request body.
        return JSONResponse(
            status_code=422,
            content={"detail": "Invalid request"},
            headers={"Cache-Control": "no-store"},
        )

    @app.post("/v1/session/resolve", response_model=ResolveResponse)
    def resolve_session(
        payload: ResolveRequest,
        response: Response,
        request: Request,
        session: Session = Depends(get_db),
    ) -> ResolveResponse:
        response.headers["Cache-Control"] = "no-store"
        charge_auth_attempt(session, runtime_settings, payload.username)
        xtream_client: XtreamClient = request.app.state.xtream_client_factory()
        try:
            return SessionResolver(
                session,
                xtream_client,
                runtime_settings,
                mega_client_factory=request.app.state.mega_client_factory,
            ).resolve(
                username=payload.username,
                password=payload.password.get_secret_value(),
            )
        finally:
            close = getattr(xtream_client, "close", None)
            if callable(close):
                close()

    return app


app = create_app()
