"""The Ollama provider, against a mocked HTTP transport: no Ollama server or model is needed."""

from __future__ import annotations

import json

import httpx
import pytest

from app.providers.base import GenerationRequest, ProviderUnavailable
from app.providers.ollama import OllamaEmbeddingProvider, OllamaModelProvider


def request() -> GenerationRequest:
    return GenerationRequest(task="ask", system_prompt="rules", user_prompt="sources and question",
                             json_schema={"type": "object"}, temperature=0.1, max_tokens=256)


def test_chat_request_shape_and_content():
    seen = {}

    def handler(http_request: httpx.Request) -> httpx.Response:
        seen["path"] = http_request.url.path
        seen["body"] = json.loads(http_request.content)
        return httpx.Response(200, json={"message": {"role": "assistant", "content": '{"ok": true}',
                                                     "thinking": "hidden reasoning"}})

    provider = OllamaModelProvider("http://ollama:11434", "example-model", 30, httpx.MockTransport(handler))
    assert provider.generate_json(request()) == '{"ok": true}'
    assert seen["path"] == "/api/chat"
    body = seen["body"]
    assert body["model"] == "example-model"
    assert body["stream"] is False
    assert body["format"] == {"type": "object"}
    assert body["options"] == {"temperature": 0.1, "num_predict": 256}
    assert [m["role"] for m in body["messages"]] == ["system", "user"]


def test_embeddings():
    def handler(http_request: httpx.Request) -> httpx.Response:
        texts = json.loads(http_request.content)["input"]
        return httpx.Response(200, json={"embeddings": [[1.0, 0.0] for _ in texts]})

    provider = OllamaEmbeddingProvider("http://ollama:11434", "embed-model", 30, httpx.MockTransport(handler))
    assert provider.embed(["a", "b"]) == [[1.0, 0.0], [1.0, 0.0]]
    assert provider.model_id == "ollama:embed-model"


@pytest.mark.parametrize("response", [
    httpx.Response(500, json={"error": "boom"}),
    httpx.Response(404, json={"error": "model not found"}),
    httpx.Response(200, text="not json"),
    httpx.Response(200, json={"message": {}}),
])
def test_bad_responses_mean_unavailable(response):
    provider = OllamaModelProvider("http://ollama:11434", "m", 30, httpx.MockTransport(lambda _: response))
    with pytest.raises(ProviderUnavailable):
        provider.generate_json(request())


def test_connection_errors_mean_unavailable():
    def handler(_: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("refused")

    with pytest.raises(ProviderUnavailable):
        OllamaEmbeddingProvider("http://ollama:11434", "m", 30, httpx.MockTransport(handler)).embed(["a"])


def test_wrong_embedding_count_means_unavailable():
    transport = httpx.MockTransport(lambda _: httpx.Response(200, json={"embeddings": [[1.0]]}))
    with pytest.raises(ProviderUnavailable):
        OllamaEmbeddingProvider("http://ollama:11434", "m", 30, transport).embed(["a", "b"])
