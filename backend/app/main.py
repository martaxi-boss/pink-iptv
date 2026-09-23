from fastapi import Depends, FastAPI, Request, Response
from sqlalchemy.orm import Session

from app.clients.xtream import XtreamClient
from app.config import Settings, get_settings
from app.db import build_session_factory, get_db
from app.logging_config import configure_logging
from app.schemas import ResolveRequest, ResolveResponse
from app.services.session import SessionResolver


def create_app(settings: Settings | None = None) -> FastAPI:
    runtime_settings = settings or get_settings()
    configure_logging()

    app = FastAPI(title="PINK IPTV Backend", version="0.1.0")
    app.state.settings = runtime_settings
    app.state.session_factory = build_session_factory(
        runtime_settings.database_url
    )
    app.state.xtream_client_factory = XtreamClient

    @app.post("/v1/session/resolve", response_model=ResolveResponse)
    def resolve_session(
        payload: ResolveRequest,
        response: Response,
        request: Request,
        session: Session = Depends(get_db),
    ) -> ResolveResponse:
        response.headers["Cache-Control"] = "no-store"
        xtream_client: XtreamClient = (
            request.app.state.xtream_client_factory()
        )
        try:
            return SessionResolver(
                session,
                xtream_client,
                runtime_settings,
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
