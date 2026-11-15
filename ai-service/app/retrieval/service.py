"""Grounded generation over one course index: answers, topic summaries, practice questions, and
assessment drafts.

Every reply is checked before it leaves: the model's JSON is validated against a schema (with one
repair attempt), and every source label it cites must be one of the chunks it was given. An answer
without a real citation is treated as insufficient context, never shown as fact.
"""

from __future__ import annotations

import copy
from collections.abc import Callable
from typing import TypeVar

import numpy as np
from pydantic import BaseModel, ValidationError

from app.config import Settings
from app.errors import index_mismatch, model_output_invalid
from app.indexing.store import CourseIndex, IndexStore, StoredChunk, unit_vectors
from app.models import schemas
from app.models.schemas import Citation, QuestionType
from app.providers.base import ContextChunk, EmbeddingProvider, GenerationRequest, ModelProvider
from app.retrieval import prompts
from app.security import checked_course_key

Output = TypeVar("Output", bound=BaseModel)
REPAIR_NOTE = ("\n\nYour previous reply could not be used: it was not valid JSON for the required schema. "
               "Reply again with only JSON that matches the schema.")


def inline_schema(model: type[BaseModel]) -> dict:
    """The model's JSON schema with every $ref resolved, which local model runtimes handle best."""
    schema = model.model_json_schema()
    definitions = schema.pop("$defs", {})

    def resolve(node):
        if isinstance(node, dict):
            if "$ref" in node:
                return resolve(copy.deepcopy(definitions[node["$ref"].split("/")[-1]]))
            return {key: resolve(value) for key, value in node.items()}
        if isinstance(node, list):
            return [resolve(item) for item in node]
        return node

    return resolve(schema)


def _citation(chunk: StoredChunk) -> Citation:
    return Citation(sourceType=chunk.sourceType, sourceId=chunk.sourceId, topicId=chunk.topicId, title=chunk.title,
                    fileName=chunk.fileName, pageNumber=chunk.pageNumber, slideNumber=chunk.slideNumber)


class _Context:
    """The chunks given to the model, labelled S1, S2, ... so citations can be checked."""

    def __init__(self, chunks: list[StoredChunk]) -> None:
        self.chunks = {f"S{number}": chunk for number, chunk in enumerate(chunks, start=1)}

    def for_prompt(self) -> tuple[ContextChunk, ...]:
        return tuple(ContextChunk(ref, chunk.title, chunk.text) for ref, chunk in self.chunks.items())

    def citations(self, refs: list[str]) -> list[Citation]:
        seen, result = set(), []
        for ref in refs:
            chunk = self.chunks.get(ref.strip().upper())
            if chunk is None:
                continue
            key = (chunk.sourceType, chunk.sourceId, chunk.pageNumber, chunk.slideNumber)
            if key not in seen:
                seen.add(key)
                result.append(_citation(chunk))
        return result


class LearningService:
    def __init__(self, settings: Settings, models: ModelProvider, embeddings: EmbeddingProvider, store: IndexStore) -> None:
        self._settings = settings
        self._models = models
        self._embeddings = embeddings
        self._store = store

    # --- Index access ------------------------------------------------------------------------

    def _index(self, course_key: str, fingerprint: str) -> CourseIndex:
        index = self._store.load(course_key)
        # The backend names the content it expects; an index of other content or another model is never used.
        if index.fingerprint != fingerprint or index.embedding_model != self._embeddings.model_id:
            raise index_mismatch()
        return index

    @staticmethod
    def _allowed(index: CourseIndex, allowed: list[schemas.SourceRef] | None) -> np.ndarray:
        """Which chunks of the index the request may use: those of the allowed sources, or all when no list is given."""
        if allowed is None:
            return np.ones(len(index.chunks), dtype=bool)
        keys = {(ref.sourceType.value, ref.sourceId) for ref in allowed}
        return np.array([(chunk.sourceType, chunk.sourceId) in keys for chunk in index.chunks], dtype=bool)

    def _scopes(self, course_key: str, request: schemas.IndexedRequest) -> list[tuple[CourseIndex, np.ndarray]]:
        """The course index and any additional ones, each checked against its fingerprint, with the allowed chunks."""
        course = self._index(course_key, request.indexFingerprint)
        scopes = [(course, self._allowed(course, request.allowedSources))]
        for extra in request.additionalIndexes:
            index = self._index(checked_course_key(extra.indexKey), extra.indexFingerprint)
            scopes.append((index, self._allowed(index, extra.allowedSources)))
        return scopes

    def _search(self, scopes: list[tuple[CourseIndex, np.ndarray]], question: str) -> list[StoredChunk]:
        """The best allowed chunks across every index, best first, above the relevance floor."""
        query = unit_vectors(self._embeddings.embed([question]))[0]
        found: list[tuple[float, int, int, StoredChunk]] = []
        for order, (index, allowed) in enumerate(scopes):
            for position, score in index.search(query, len(index.chunks)):
                if allowed[position] and score >= self._settings.min_relevance:
                    found.append((score, order, position, index.chunks[position]))
        found.sort(key=lambda item: (-item[0], item[1], item[2]))
        return [item[3] for item in found[: self._settings.top_k]]

    def _spread(self, chunks: list[StoredChunk]) -> list[StoredChunk]:
        """Up to the context limit, spread evenly over the chunks so the whole selection is covered."""
        limit = self._settings.max_context_chunks
        if len(chunks) <= limit:
            return chunks
        positions = np.linspace(0, len(chunks) - 1, num=limit).round().astype(int)
        return [chunks[i] for i in dict.fromkeys(positions.tolist())]

    def _selection(self, scopes: list[tuple[CourseIndex, np.ndarray]], topic_id: int | None) -> list[StoredChunk]:
        chunks = [chunk for index, allowed in scopes for position, chunk in enumerate(index.chunks)
                  if allowed[position] and (topic_id is None or chunk.topicId == topic_id)]
        return self._spread(chunks)

    # --- Generation --------------------------------------------------------------------------

    def _generate(self, task: str, context: _Context, output: type[Output], temperature: float,
                  parameters: dict, usable: Callable[[Output], bool] = lambda _: True,
                  question: str | None = None, teacher_note: str | None = None) -> Output | None:
        """Valid output, or None when two attempts gave valid JSON that was still unusable."""
        system = prompts.system_prompt(task, parameters)
        user = prompts.user_prompt(context.for_prompt(), question=question, teacher_note=teacher_note)
        schema = inline_schema(output)
        parsed_any = False
        for attempt in range(2):
            raw = self._models.generate_json(GenerationRequest(
                task=task, system_prompt=system, user_prompt=user if attempt == 0 else user + REPAIR_NOTE,
                json_schema=schema, temperature=temperature, max_tokens=self._settings.max_output_tokens,
                context=context.for_prompt(), parameters={**parameters, "question": question or ""},
            ))
            try:
                result = output.model_validate_json(raw)
            except ValidationError:
                continue
            parsed_any = True
            if usable(result):
                return result
        if not parsed_any:
            raise model_output_invalid()
        return None

    # --- Tasks -------------------------------------------------------------------------------

    def ask(self, course_key: str, request: schemas.AskRequest) -> schemas.AskResponse:
        insufficient = schemas.AskResponse(answer=None, insufficientContext=True, citations=[])
        hits = self._search(self._scopes(course_key, request), request.question)
        if not hits:
            return insufficient
        context = _Context(hits)
        result = self._generate(
            "ask", context, schemas.AskModelOutput, self._settings.answer_temperature, {},
            usable=lambda out: out.insufficientContext or (bool(out.answer) and bool(context.citations(out.sourceIds))),
            question=request.question)
        if result is None or result.insufficientContext:
            return insufficient
        return schemas.AskResponse(answer=result.answer, insufficientContext=False,
                                   citations=context.citations(result.sourceIds))

    def summary(self, course_key: str, request: schemas.SummaryRequest) -> schemas.SummaryResponse:
        insufficient = schemas.SummaryResponse(summary=None, keyPoints=[], insufficientContext=True, citations=[])
        chunks = self._selection(self._scopes(course_key, request), request.topicId)
        if not chunks:
            return insufficient
        context = _Context(chunks)
        result = self._generate("summary", context, schemas.SummaryModelOutput, self._settings.answer_temperature, {},
                                usable=lambda out: out.insufficientContext or bool(out.summary))
        if result is None or result.insufficientContext:
            return insufficient
        # Every chunk given belongs to this topic, so when the model names none, all of them back the summary.
        citations = context.citations(result.sourceIds) or context.citations(list(context.chunks))
        return schemas.SummaryResponse(summary=result.summary, keyPoints=[p for p in result.keyPoints if p],
                                       insufficientContext=False, citations=citations)

    def practice(self, course_key: str, request: schemas.PracticeRequest) -> schemas.PracticeResponse:
        chunks = self._selection(self._scopes(course_key, request), request.topicId)
        if not chunks:
            return schemas.PracticeResponse(questions=[], insufficientContext=True)
        context = _Context(chunks)

        def cited(out: schemas.PracticeModelOutput) -> list[schemas.PracticeQuestion]:
            return [schemas.PracticeQuestion(question=q.question, suggestedAnswer=q.suggestedAnswer,
                                             citations=context.citations(q.sourceIds))
                    for q in out.questions if context.citations(q.sourceIds)][:request.questionCount]

        result = self._generate("practice", context, schemas.PracticeModelOutput, self._settings.answer_temperature,
                                {"questionCount": request.questionCount}, usable=lambda out: bool(cited(out)))
        if result is None:
            return schemas.PracticeResponse(questions=[], insufficientContext=True)
        return schemas.PracticeResponse(questions=cited(result), insufficientContext=False)

    def assessment_draft(self, course_key: str, request: schemas.AssessmentDraftRequest) -> schemas.AssessmentDraftResponse:
        empty = schemas.AssessmentDraftResponse(titleSuggestion=None, instructionsSuggestion=None, questions=[],
                                                insufficientContext=True)
        chunks = self._selection(self._scopes(course_key, request), None)
        if not chunks:
            return empty
        context = _Context(chunks)
        wanted = request.questionType

        def accepted(out: schemas.AssessmentModelOutput) -> list[schemas.AssessmentQuestion]:
            questions = []
            for q in out.questions:
                citations = context.citations(q.sourceIds)
                if not citations or (wanted is QuestionType.MCQ and q.questionType is not QuestionType.MCQ):
                    continue
                kind = q.questionType if wanted is QuestionType.MIXED else wanted
                questions.append(schemas.AssessmentQuestion(
                    questionType=kind, prompt=q.prompt, options=q.options if kind is QuestionType.MCQ else [],
                    suggestedAnswer=q.suggestedAnswer, suggestedMarks=q.suggestedMarks, citations=citations))
            return questions[:request.questionCount]

        result = self._generate(
            "assessment_draft", context, schemas.AssessmentModelOutput, self._settings.draft_temperature,
            {"questionCount": request.questionCount, "difficulty": request.difficulty.value,
             "questionType": wanted.value},
            usable=lambda out: bool(accepted(out)), teacher_note=request.additionalInstruction)
        if result is None:
            return empty
        return schemas.AssessmentDraftResponse(titleSuggestion=result.titleSuggestion,
                                               instructionsSuggestion=result.instructionsSuggestion or None,
                                               questions=accepted(result), insufficientContext=False)
