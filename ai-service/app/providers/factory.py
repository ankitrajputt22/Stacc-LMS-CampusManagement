"""Chooses the providers named by the settings. Commercial AI APIs are deliberately not offered."""

from __future__ import annotations

import logging

from app.config import Settings
from app.providers.base import EmbeddingProvider, ModelProvider
from app.providers.fake import FakeEmbeddingProvider, FakeModelProvider
from app.providers.ollama import OllamaEmbeddingProvider, OllamaModelProvider

log = logging.getLogger(__name__)


def create_providers(settings: Settings) -> tuple[ModelProvider, EmbeddingProvider]:
    if settings.provider == "fake":
        log.warning("AI_PROVIDER=fake: answers only quote course text. Use it for testing, never for students.")
        return FakeModelProvider(), FakeEmbeddingProvider()
    return (
        OllamaModelProvider(settings.ollama_base_url, settings.chat_model, settings.provider_timeout_seconds),
        OllamaEmbeddingProvider(settings.ollama_base_url, settings.embedding_model, settings.provider_timeout_seconds),
    )
