"""The two provider abstractions. The rest of the service depends only on these, never on Ollama.

A ``ModelProvider`` turns a prepared prompt into JSON text that should match a schema. An
``EmbeddingProvider`` turns texts into vectors. Both raise ``ProviderUnavailable`` when the
provider cannot be reached; nothing else about the provider leaks to callers.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import Sequence
from dataclasses import dataclass, field


class ProviderUnavailable(Exception):
    """The provider could not be reached or refused the request."""


@dataclass(frozen=True)
class ContextChunk:
    """A retrieved chunk as the prompt shows it: a short reference label and its text."""

    ref: str
    title: str
    text: str


@dataclass(frozen=True)
class GenerationRequest:
    """Everything a model needs for one structured generation.

    ``task``, ``context``, and ``parameters`` are structured copies of what the prompt says. A real
    provider sends only the prompts and schema; the deterministic test provider reads these instead.
    """

    task: str
    system_prompt: str
    user_prompt: str
    json_schema: dict
    temperature: float
    max_tokens: int
    context: tuple[ContextChunk, ...] = ()
    parameters: dict = field(default_factory=dict)


class ModelProvider(ABC):
    @abstractmethod
    def generate_json(self, request: GenerationRequest) -> str:
        """Returns the model's raw JSON text. It is validated by the caller, never trusted."""


class EmbeddingProvider(ABC):
    @property
    @abstractmethod
    def model_id(self) -> str:
        """Identifies the embedding model, so an index built with another model is never queried."""

    @abstractmethod
    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        """One vector per text, in order."""
