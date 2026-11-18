from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import Sequence
from dataclasses import dataclass, field


class ProviderUnavailable(Exception):
    pass


@dataclass(frozen=True)
class ContextChunk:

    ref: str
    title: str
    text: str


@dataclass(frozen=True)
class GenerationRequest:

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
        pass


class EmbeddingProvider(ABC):
    @property
    @abstractmethod
    def model_id(self) -> str:
        pass

    @abstractmethod
    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        pass
