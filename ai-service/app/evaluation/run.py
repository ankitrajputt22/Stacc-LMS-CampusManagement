"""Runs the retrieval evaluation (v0.13.8) and reports hit rate, mean reciprocal rank, and false finds.

    python -m app.evaluation.run          # with the configured providers, such as Ollama
    AI_PROVIDER=fake python -m app.evaluation.run

It builds the evaluation course in a temporary index folder, asks every question, and prints one line per question
and the totals. Nothing leaves the machine except calls to the configured local model runtime.
"""

from __future__ import annotations

import sys
import tempfile
from dataclasses import dataclass, replace
from pathlib import Path

from app.config import Settings
from app.evaluation import dataset
from app.indexing.builder import IndexBuilder
from app.indexing.store import IndexStore
from app.models import schemas
from app.providers.base import EmbeddingProvider, ModelProvider
from app.providers.factory import create_providers
from app.retrieval.service import LearningService


@dataclass(frozen=True)
class QuestionResult:
    question: dataset.Question
    found: tuple[int, ...]  # topic IDs, best first

    @property
    def rank(self) -> int | None:
        """The rank of the first expected source, from 1, or None."""
        for position, topic_id in enumerate(self.found, start=1):
            if topic_id in self.question.expected:
                return position
        return None


@dataclass(frozen=True)
class Report:
    results: tuple[QuestionResult, ...]

    @property
    def answerable(self) -> list[QuestionResult]:
        return [result for result in self.results if result.question.expected]

    @property
    def hit_rate(self) -> float:
        """Share of answerable questions with an expected source among those found."""
        return sum(result.rank is not None for result in self.answerable) / max(1, len(self.answerable))

    @property
    def first_hit_rate(self) -> float:
        """Share of answerable questions whose best source is an expected one."""
        return sum(result.rank == 1 for result in self.answerable) / max(1, len(self.answerable))

    @property
    def mean_reciprocal_rank(self) -> float:
        return sum(1 / result.rank for result in self.answerable if result.rank) / max(1, len(self.answerable))

    @property
    def false_finds(self) -> int:
        """Unanswerable questions for which something was found anyway."""
        return sum(bool(result.found) for result in self.results if not result.question.expected)


def evaluate(settings: Settings, models: ModelProvider, embeddings: EmbeddingProvider) -> Report:
    store = IndexStore(settings.index_dir)
    IndexBuilder(settings, embeddings, store).replace(dataset.COURSE_KEY, schemas.ReplaceIndexRequest(
        contentFingerprint=dataset.FINGERPRINT, sources=dataset.sources()))
    learning = LearningService(settings, models, embeddings, store)
    request = schemas.AskRequest(indexFingerprint=dataset.FINGERPRINT, question="placeholder")
    results = []
    for question in dataset.QUESTIONS:
        chunks = learning.retrieve(dataset.COURSE_KEY, request, question.text)
        results.append(QuestionResult(question, tuple(dict.fromkeys(chunk.topicId for chunk in chunks))))
    return Report(tuple(results))


def main() -> int:
    settings = Settings.from_env()
    with tempfile.TemporaryDirectory() as folder:
        settings = replace(settings, index_dir=Path(folder))
        models, embeddings = create_providers(settings)
        report = evaluate(settings, models, embeddings)
    for result in report.results:
        expected = ",".join(map(str, sorted(result.question.expected))) or "-"
        print(f"{result.rank or '-':>2}  expected {expected:<5} found {list(result.found)}  {result.question.text}")
    print(f"hit rate {report.hit_rate:.2f}  first hit {report.first_hit_rate:.2f}  "
          f"MRR {report.mean_reciprocal_rank:.2f}  false finds {report.false_finds}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
