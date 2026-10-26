"""Deterministic providers for tests and for checking the wiring without a model.

They need no network, GPU, or model download, and always give the same output for the same input.
The fake model does not understand anything: it quotes the retrieved course text it was given, so its
answers are still grounded in the sources. Never use it for real students.
"""

from __future__ import annotations

import hashlib
import json
import math
import re
from collections.abc import Sequence

from app.providers.base import ContextChunk, EmbeddingProvider, GenerationRequest, ModelProvider

WORD = re.compile(r"[a-z0-9]{2,}")
STOP_WORDS = frozenset(
    "the and for are was were with that this from what which when where who how why does did can into "
    "about your you its their there these those then than have has had not but all any our out use used "
    "using also each per via is it of to in on as an or be by at do if so no".split()
)
DIMENSION = 256


def words(text: str) -> list[str]:
    return [word for word in WORD.findall(text.lower()) if word not in STOP_WORDS]


class FakeEmbeddingProvider(EmbeddingProvider):
    """A hashed bag of words: texts sharing words get similar vectors, unrelated texts do not."""

    @property
    def model_id(self) -> str:
        return "fake:hashed-words-256"

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        return [self._vector(text) for text in texts]

    @staticmethod
    def _vector(text: str) -> list[float]:
        vector = [0.0] * DIMENSION
        for word in words(text):
            digest = hashlib.sha256(word.encode("utf-8")).digest()
            vector[int.from_bytes(digest[:4], "big") % DIMENSION] += 1.0
        norm = math.sqrt(sum(value * value for value in vector))
        return [value / norm for value in vector] if norm else vector


def _first_sentence(text: str, limit: int = 240) -> str:
    text = " ".join(text.split())
    match = re.search(r"(.+?[.!?])(\s|$)", text)
    sentence = match.group(1) if match else text
    return sentence[:limit]


class FakeModelProvider(ModelProvider):
    """Builds valid JSON for each task straight from the retrieved chunks."""

    def generate_json(self, request: GenerationRequest) -> str:
        context = request.context
        builder = getattr(self, f"_{request.task}")
        return json.dumps(builder(context, request.parameters))

    @staticmethod
    def _ask(context: tuple[ContextChunk, ...], parameters: dict) -> dict:
        question_words = set(words(parameters.get("question", "")))
        supporting = [chunk for chunk in context if question_words & set(words(chunk.text + " " + chunk.title))]
        if not supporting:
            return {"insufficientContext": True, "answer": "", "sourceIds": []}
        best = supporting[0]
        return {
            "insufficientContext": False,
            "answer": f"According to {best.title}: {_first_sentence(best.text, 400)}",
            "sourceIds": [chunk.ref for chunk in supporting[:2]],
        }

    @staticmethod
    def _summary(context: tuple[ContextChunk, ...], parameters: dict) -> dict:
        if not context:
            return {"insufficientContext": True, "summary": "", "keyPoints": [], "sourceIds": []}
        return {
            "insufficientContext": False,
            "summary": " ".join(_first_sentence(chunk.text) for chunk in context[:2]),
            "keyPoints": [_first_sentence(chunk.text, 160) for chunk in context[:3]],
            "sourceIds": [chunk.ref for chunk in context[:3]],
        }

    @staticmethod
    def _practice(context: tuple[ContextChunk, ...], parameters: dict) -> dict:
        count = int(parameters.get("questionCount", 5))
        questions = []
        for number in range(count):
            chunk = context[number % len(context)]
            questions.append({
                "question": f"Question {number + 1}: In {chunk.title}, what does the material say about "
                            f"\"{' '.join(words(chunk.text)[:4])}\"?",
                "suggestedAnswer": _first_sentence(chunk.text, 600),
                "sourceIds": [chunk.ref],
            })
        return {"questions": questions}

    @staticmethod
    def _assessment_draft(context: tuple[ContextChunk, ...], parameters: dict) -> dict:
        count = int(parameters.get("questionCount", 5))
        requested = parameters.get("questionType", "MIXED")
        cycle = ["MCQ", "SHORT_ANSWER", "LONG_ANSWER"]
        questions = []
        for number in range(count):
            chunk = context[number % len(context)]
            kind = requested if requested != "MIXED" else cycle[number % len(cycle)]
            sentence = _first_sentence(chunk.text, 300)
            questions.append({
                "questionType": kind,
                "prompt": f"Q{number + 1}. Explain this point from {chunk.title}: {sentence}",
                "options": [sentence, "None of the above"] if kind == "MCQ" else [],
                "suggestedAnswer": sentence,
                "suggestedMarks": {"MCQ": 1, "SHORT_ANSWER": 5, "LONG_ANSWER": 10}[kind],
                "sourceIds": [chunk.ref],
            })
        return {
            "titleSuggestion": f"Assessment on {context[0].title}",
            "instructionsSuggestion": "Answer every question. Use the course materials you have studied.",
            "questions": questions,
        }
