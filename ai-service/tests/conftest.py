"""Shared fixtures. Everything runs with the deterministic fake providers: no network, GPU, or model."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.providers.fake import FakeEmbeddingProvider, FakeModelProvider
from helpers import make_settings


@pytest.fixture
def settings(tmp_path) -> Settings:
    return make_settings(tmp_path)


@pytest.fixture
def client(settings) -> TestClient:
    return TestClient(create_app(settings, FakeModelProvider(), FakeEmbeddingProvider()))
