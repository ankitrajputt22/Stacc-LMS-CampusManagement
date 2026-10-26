"""The local, self-hosted provider: an Ollama server reached over HTTP.

Only two Ollama routes are used: ``/api/chat`` with a JSON schema as the output format, and
``/api/embed``. No model is pulled from here; the operator pulls models once (see docs/ai-learning.md).
"""

from __future__ import annotations

import logging
from collections.abc import Sequence

import httpx

from app.providers.base import EmbeddingProvider, GenerationRequest, ModelProvider, ProviderUnavailable

log = logging.getLogger(__name__)


class _OllamaClient:
    def __init__(self, base_url: str, timeout_seconds: float, transport: httpx.BaseTransport | None = None) -> None:
        self._client = httpx.Client(
            base_url=base_url,
            timeout=httpx.Timeout(timeout_seconds, connect=5.0),
            transport=transport,
            follow_redirects=False,
        )

    def post(self, path: str, payload: dict) -> dict:
        try:
            response = self._client.post(path, json=payload)
        except httpx.HTTPError as error:
            log.warning("Ollama request failed: %s", type(error).__name__)
            raise ProviderUnavailable() from error
        if response.status_code != 200:
            log.warning("Ollama answered %s on %s", response.status_code, path)
            raise ProviderUnavailable()
        try:
            body = response.json()
        except ValueError as error:
            raise ProviderUnavailable() from error
        if not isinstance(body, dict):
            raise ProviderUnavailable()
        return body


class OllamaModelProvider(ModelProvider):
    def __init__(self, base_url: str, model: str, timeout_seconds: float,
                 transport: httpx.BaseTransport | None = None) -> None:
        self._client = _OllamaClient(base_url, timeout_seconds, transport)
        self._model = model

    def generate_json(self, request: GenerationRequest) -> str:
        body = self._client.post("/api/chat", {
            "model": self._model,
            "stream": False,
            # Ollama constrains the output to this JSON schema. The caller still validates it.
            "format": request.json_schema,
            "messages": [
                {"role": "system", "content": request.system_prompt},
                {"role": "user", "content": request.user_prompt},
            ],
            "options": {"temperature": request.temperature, "num_predict": request.max_tokens},
        })
        # Only the final content is read. A reasoning model's separate "thinking" text is ignored and never passed on.
        message = body.get("message")
        content = message.get("content") if isinstance(message, dict) else None
        if not isinstance(content, str):
            raise ProviderUnavailable()
        return content


class OllamaEmbeddingProvider(EmbeddingProvider):
    def __init__(self, base_url: str, model: str, timeout_seconds: float,
                 transport: httpx.BaseTransport | None = None) -> None:
        self._client = _OllamaClient(base_url, timeout_seconds, transport)
        self._model = model

    @property
    def model_id(self) -> str:
        return f"ollama:{self._model}"

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        body = self._client.post("/api/embed", {"model": self._model, "input": list(texts)})
        vectors = body.get("embeddings")
        if not isinstance(vectors, list) or len(vectors) != len(texts):
            raise ProviderUnavailable()
        return vectors
