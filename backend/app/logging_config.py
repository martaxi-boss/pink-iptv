import logging


def configure_logging() -> None:
    # httpx/httpcore INFO logs can include full request URLs. Xtream credentials are query params.
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("httpcore").setLevel(logging.WARNING)
