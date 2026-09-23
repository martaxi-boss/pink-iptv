import argparse

from app.clients.mega import MegaOTTClient
from app.config import get_settings
from app.db import build_session_factory
from app.services.mappings import build_import_evidence, import_subscription


def _import_subscription(mega_subscription_id: int) -> int:
    settings = get_settings()
    token = settings.require_mega_token()
    session_factory = build_session_factory(settings.database_url)
    with MegaOTTClient(
        base_url=settings.mega_ott_api_base,
        token=token,
    ) as mega_client:
        with session_factory() as session:
            mapping = import_subscription(
                session,
                mega_client,
                mega_subscription_id,
            )
            evidence = build_import_evidence(mapping)
    print(
        "imported "
        f"subscription_id={evidence.mega_subscription_id} "
        f"mapping_id={evidence.mapping_id} "
        f"username_sha256={evidence.username_sha256} "
        f"dns_link_sha256={evidence.dns_link_sha256} "
        f"dns_scheme={evidence.dns_scheme}"
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(prog="pink-backend")
    subparsers = parser.add_subparsers(dest="command", required=True)
    import_parser = subparsers.add_parser("import-subscription")
    import_parser.add_argument(
        "--id",
        type=int,
        required=True,
        dest="mega_subscription_id",
    )
    args = parser.parse_args()
    if args.command == "import-subscription":
        return _import_subscription(args.mega_subscription_id)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
