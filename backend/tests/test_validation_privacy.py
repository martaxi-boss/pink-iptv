import pytest
from fastapi.testclient import TestClient

from app.main import create_app

SENSITIVE_VALUE = "synthetic-request-value"


@pytest.mark.parametrize(
    "payload",
    [
        {"username": "synthetic-user", "password": {"raw": SENSITIVE_VALUE}},
        {"username": {"raw": SENSITIVE_VALUE}, "password": SENSITIVE_VALUE},
        {"username": "synthetic-user", "password": SENSITIVE_VALUE, "extra": SENSITIVE_VALUE},
        {"username": "synthetic-user", "password": SENSITIVE_VALUE, SENSITIVE_VALUE: "extra"},
        {"password": SENSITIVE_VALUE},
        [SENSITIVE_VALUE],
    ],
)
def test_invalid_request_does_not_echo_inputs_or_contact_provider(settings, payload, caplog):
    app = create_app(settings)

    def forbidden_provider():
        pytest.fail("Invalid requests must never construct an upstream client")

    app.state.xtream_client_factory = forbidden_provider
    response = TestClient(app).post("/v1/session/resolve", json=payload)
    assert response.status_code == 422
    assert response.headers["Cache-Control"] == "no-store"
    assert response.json() == {"detail": "Invalid request"}
    assert SENSITIVE_VALUE not in response.text
    assert SENSITIVE_VALUE not in caplog.text


def test_malformed_json_is_sanitized_and_not_cacheable(settings):
    response = TestClient(create_app(settings)).post(
        "/v1/session/resolve",
        content='{"password":"' + SENSITIVE_VALUE + '",',
        headers={"Content-Type": "application/json"},
    )
    assert response.status_code == 422
    assert response.headers["Cache-Control"] == "no-store"
    assert response.json() == {"detail": "Invalid request"}
    assert SENSITIVE_VALUE not in response.text
