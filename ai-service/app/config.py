"""Settings for the AI service, read once from environment variables.

Every limit has a default sized for a college mini-project. Model names have no default: the
operator chooses them, and the service refuses to start with the Ollama provider until they are set.
"""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path

PROVIDERS = ("ollama", "fake")
MIN_TOKEN_LENGTH = 32


class SettingsError(ValueError):
    """A setting is missing or invalid. The message names the variable, never its value."""


def _text(env: Mapping[str, str], name: str, default: str = "") -> str:
    return env.get(name, default).strip()


def _int(env: Mapping[str, str], name: str, default: int, low: int, high: int) -> int:
    raw = _text(env, name)
    try:
        value = int(raw) if raw else default
    except ValueError as error:
        raise SettingsError(f"{name} must be a whole number") from error
    if not low <= value <= high:
        raise SettingsError(f"{name} must be between {low} and {high}")
    return value


def _float(env: Mapping[str, str], name: str, default: float, low: float, high: float) -> float:
    raw = _text(env, name)
    try:
        value = float(raw) if raw else default
    except ValueError as error:
        raise SettingsError(f"{name} must be a number") from error
    if not low <= value <= high:
        raise SettingsError(f"{name} must be between {low} and {high}")
    return value


@dataclass(frozen=True)
class Settings:
    provider: str
    ollama_base_url: str
    chat_model: str
    embedding_model: str
    index_dir: Path
    internal_token: str
    provider_timeout_seconds: float
    # Request and extraction limits.
    max_request_bytes: int
    max_sources: int
    max_source_bytes: int
    max_chars_per_source: int
    max_total_chars: int
    max_chunks: int
    # Chunking and retrieval.
    chunk_size: int
    chunk_overlap: int
    embedding_batch_size: int
    top_k: int
    min_relevance: float
    max_context_chunks: int
    # Generation.
    answer_temperature: float
    draft_temperature: float
    max_output_tokens: int

    @classmethod
    def from_env(cls, env: Mapping[str, str] | None = None) -> Settings:
        env = os.environ if env is None else env
        provider = _text(env, "AI_PROVIDER", "ollama").lower()
        if provider not in PROVIDERS:
            raise SettingsError("AI_PROVIDER must be one of: " + ", ".join(PROVIDERS))
        token = _text(env, "STACC_AI_INTERNAL_TOKEN")
        if len(token) < MIN_TOKEN_LENGTH:
            raise SettingsError(f"STACC_AI_INTERNAL_TOKEN must be set to at least {MIN_TOKEN_LENGTH} characters")
        chat_model = _text(env, "AI_CHAT_MODEL")
        embedding_model = _text(env, "AI_EMBEDDING_MODEL")
        if provider == "ollama" and (not chat_model or not embedding_model):
            raise SettingsError("AI_CHAT_MODEL and AI_EMBEDDING_MODEL must be set for the ollama provider")
        chunk_size = _int(env, "AI_CHUNK_SIZE", 1200, 300, 4000)
        chunk_overlap = _int(env, "AI_CHUNK_OVERLAP", 200, 0, 1000)
        if chunk_overlap >= chunk_size // 2:
            raise SettingsError("AI_CHUNK_OVERLAP must be less than half of AI_CHUNK_SIZE")
        index_dir = _text(env, "AI_INDEX_DIR", "./data/indexes")
        return cls(
            provider=provider,
            ollama_base_url=_text(env, "OLLAMA_BASE_URL", "http://localhost:11434").rstrip("/"),
            chat_model=chat_model or "fake-chat",
            embedding_model=embedding_model or "fake-embedding",
            index_dir=Path(index_dir),
            internal_token=token,
            provider_timeout_seconds=_float(env, "AI_PROVIDER_TIMEOUT_SECONDS", 120.0, 1.0, 900.0),
            max_request_bytes=_int(env, "AI_MAX_REQUEST_BYTES", 72 * 1024 * 1024, 1024, 512 * 1024 * 1024),
            max_sources=_int(env, "AI_MAX_SOURCES", 200, 1, 2000),
            max_source_bytes=_int(env, "AI_MAX_SOURCE_BYTES", 25 * 1024 * 1024, 1024, 256 * 1024 * 1024),
            max_chars_per_source=_int(env, "AI_MAX_CHARS_PER_SOURCE", 200_000, 1000, 5_000_000),
            max_total_chars=_int(env, "AI_MAX_TOTAL_CHARS", 2_000_000, 1000, 50_000_000),
            max_chunks=_int(env, "AI_MAX_CHUNKS", 4000, 10, 100_000),
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            embedding_batch_size=_int(env, "AI_EMBEDDING_BATCH_SIZE", 32, 1, 512),
            top_k=_int(env, "AI_TOP_K", 6, 1, 8),
            min_relevance=_float(env, "AI_MIN_RELEVANCE", 0.2, 0.0, 1.0),
            max_context_chunks=_int(env, "AI_MAX_CONTEXT_CHUNKS", 8, 1, 12),
            answer_temperature=_float(env, "AI_ANSWER_TEMPERATURE", 0.1, 0.0, 1.0),
            draft_temperature=_float(env, "AI_DRAFT_TEMPERATURE", 0.4, 0.0, 1.0),
            max_output_tokens=_int(env, "AI_MAX_OUTPUT_TOKENS", 4096, 128, 16384),
        )

    def __repr__(self) -> str:
        # The default form would print the internal token.
        return f"Settings(provider={self.provider!r}, chat_model={self.chat_model!r}, index_dir={str(self.index_dir)!r})"
