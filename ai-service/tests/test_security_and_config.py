from __future__ import annotations

import pytest

from app.config import Settings, SettingsError
from helpers import COURSE, FINGERPRINT, HEADERS, TOKEN, make_settings, replace, topic


def test_health_is_open_and_reveals_nothing(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


@pytest.mark.parametrize("headers", [{}, {"X-Stacc-AI-Token": "wrong-token"}, {"X-Stacc-AI-Token": ""},
                                     {"Authorization": f"Bearer {TOKEN}"}])
def test_internal_routes_need_the_internal_token(client, headers):
    for path, body in [
        (f"/internal/indexes/{COURSE}/replace", {"contentFingerprint": FINGERPRINT, "sources": [topic(1, "T", "text")]}),
        (f"/internal/indexes/{COURSE}/ask", {"indexFingerprint": FINGERPRINT, "question": "What is a key?"}),
        (f"/internal/indexes/{COURSE}/summary", {"indexFingerprint": FINGERPRINT, "topicId": 1}),
        (f"/internal/indexes/{COURSE}/practice", {"indexFingerprint": FINGERPRINT}),
        (f"/internal/indexes/{COURSE}/assessment-draft",
         {"indexFingerprint": FINGERPRINT, "questionCount": 1, "difficulty": "EASY", "questionType": "MCQ"}),
    ]:
        response = client.post(path, headers=headers, json=body)
        assert response.status_code == 401, path
        assert response.json()["code"] == "UNAUTHORIZED"
    assert client.get("/internal/health", headers=headers).status_code == 401


def test_internal_health_with_token(client):
    assert client.get("/internal/health", headers=HEADERS).json() == {"status": "ok", "provider": "fake"}


@pytest.mark.parametrize("key", ["..", "ABCDEF0123456789ABCDEF0123456789", "0123", "0123456789abcdef0123456789abcdeg",
                                 "..%2F..%2Fetc", "0123456789abcdef0123456789abcdef0", "%2e%2e"])
def test_unsafe_course_keys_are_refused(client, key):
    response = client.post(f"/internal/indexes/{key}/replace", headers=HEADERS,
                           json={"contentFingerprint": FINGERPRINT, "sources": [topic(1, "T", "Some text.")]})
    assert response.status_code in (400, 404)
    assert "traceback" not in response.text.lower()


def test_validation_errors_do_not_echo_input(client):
    response = client.post(f"/internal/indexes/{COURSE}/ask", headers=HEADERS,
                           json={"indexFingerprint": FINGERPRINT, "question": "x", "secret": "do-not-echo"})
    assert response.status_code == 400
    assert response.json() == {"code": "INVALID_REQUEST", "message": "The request is not valid."}


def test_oversized_requests_are_refused(tmp_path):
    from fastapi.testclient import TestClient

    from app.main import create_app
    from app.providers.fake import FakeEmbeddingProvider, FakeModelProvider

    small = TestClient(create_app(make_settings(tmp_path, AI_MAX_REQUEST_BYTES="2048"),
                                  FakeModelProvider(), FakeEmbeddingProvider()))
    response = replace(small, [topic(1, "Big", "x" * 5000)])
    assert response.status_code == 413


def test_settings_require_a_long_token():
    with pytest.raises(SettingsError, match="STACC_AI_INTERNAL_TOKEN"):
        Settings.from_env({"AI_PROVIDER": "fake", "STACC_AI_INTERNAL_TOKEN": "short"})


def test_settings_require_model_names_for_ollama():
    with pytest.raises(SettingsError, match="AI_CHAT_MODEL"):
        Settings.from_env({"AI_PROVIDER": "ollama", "STACC_AI_INTERNAL_TOKEN": TOKEN})


def test_settings_refuse_unknown_providers():
    with pytest.raises(SettingsError, match="AI_PROVIDER"):
        Settings.from_env({"AI_PROVIDER": "openai", "STACC_AI_INTERNAL_TOKEN": TOKEN})


def test_settings_never_print_the_token(tmp_path):
    assert TOKEN not in repr(make_settings(tmp_path))
    assert TOKEN not in str(make_settings(tmp_path))
