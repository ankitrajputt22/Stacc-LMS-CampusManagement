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
from app.errors import ServiceError, index_mismatch, model_output_invalid
from app.indexing.store import CourseIndex, IndexStore, StoredChunk, unit_vectors
from app.models import schemas
from app.models.schemas import Citation, QuestionType
from app.providers.base import ContextChunk, EmbeddingProvider, GenerationRequest, ModelProvider
from app.retrieval import keyword, prompts
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


def _searchable(chunk: StoredChunk) -> str:
    """What keyword matching reads: the source title, the heading path, and the text."""
    return "\n".join(part for part in (chunk.title, chunk.heading, chunk.text) if part)


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
        """The best allowed chunks across every index, best first.

        Hybrid search (v0.13.4): the semantic ranking (chunks above the relevance floor) and the keyword ranking
        (BM25 over words and word pairs, chunks sharing a meaningful term) are merged by reciprocal rank fusion. A
        question that neither finds returns nothing, so the answer is insufficient context without asking the model.
        """
        query = unit_vectors(self._embeddings.embed([question]))[0]
        candidates: list[StoredChunk] = []
        semantic: list[tuple[float, int]] = []
        for index, allowed in scopes:
            for position, score in index.search(query, len(index.chunks)):
                if allowed[position]:
                    candidates.append(index.chunks[position])
                    if score >= self._settings.min_relevance:
                        semantic.append((score, len(candidates) - 1))
        semantic.sort(key=lambda item: (-item[0], item[1]))
        semantic_ranking = [item for _, item in semantic]
        if not self._settings.keyword_search:
            return [candidates[item] for item in semantic_ranking[: self._settings.top_k]]
        scores = keyword.bm25([_searchable(chunk) for chunk in candidates], question)
        keyword_ranking = sorted((item for item, score in enumerate(scores) if score > 0),
                                 key=lambda item: (-scores[item], item))
        fused = keyword.fuse(semantic_ranking, keyword_ranking)
        return [candidates[item] for item in fused[: self._settings.top_k]]

    def retrieve(self, course_key: str, request: schemas.IndexedRequest, question: str) -> list[StoredChunk]:
        """The chunks an answer to the question would be given, best first (v0.13.8, for evaluation)."""
        return self._search(self._scopes(course_key, request), question)

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
                  question: str | None = None, teacher_note: str | None = None,
                  history: tuple[tuple[str, str], ...] = (), style: str | None = None) -> Output | None:
        """Valid output, or None when two attempts gave valid JSON that was still unusable."""
        system = prompts.system_prompt(task, parameters)
        if style in prompts.STYLES:
            system += "\n" + prompts.STYLES[style]
        user = prompts.user_prompt(context.for_prompt(), question=question, teacher_note=teacher_note, history=history)
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
        # A follow-up (v0.15.1) is searched together with the question it follows, so "and why?" still finds sources.
        search_text = request.question if not request.history else request.history[-1].question + "\n" + request.question
        hits = self._search(self._scopes(course_key, request), search_text)
        if not hits:
            return insufficient
        context = _Context(hits)
        result = self._generate(
            "ask", context, schemas.AskModelOutput, self._settings.answer_temperature, {"style": request.style.value,
             "followsUp": request.history[-1].question if request.history else ""},
            usable=lambda out: out.insufficientContext or (bool(out.answer) and bool(context.citations(out.sourceIds))),
            question=request.question, history=tuple((turn.question, turn.answer) for turn in request.history),
            style=request.style.value)
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
        result = self._generate("summary", context, schemas.SummaryModelOutput, self._settings.answer_temperature,
                                {"styleInstruction": prompts.SUMMARY_STYLES[request.style.value], "style": request.style.value},
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

    # --- Model answers and rubric suggestions (v0.14.5) ---------------------------------------

    def model_answer(self, course_key: str, request: schemas.ModelAnswerRequest) -> schemas.ModelAnswerResponse:
        insufficient = schemas.ModelAnswerResponse(answer=None, keyPoints=[], citations=[], insufficientContext=True)
        hits = self._search(self._scopes(course_key, request), request.questionText)
        if not hits:
            return insufficient
        context = _Context(hits)
        result = self._generate(
            "model_answer", context, schemas.ModelAnswerOutput, self._settings.answer_temperature,
            {"marks": request.marks, "questionType": request.questionType.value},
            usable=lambda out: out.insufficientContext or (bool(out.answer) and bool(context.citations(out.sourceIds))),
            teacher_note=request.questionText)
        if result is None or result.insufficientContext:
            return insufficient
        return schemas.ModelAnswerResponse(answer=result.answer, keyPoints=[p for p in result.keyPoints if p],
                                           citations=context.citations(result.sourceIds), insufficientContext=False)

    def rubric(self, course_key: str, request: schemas.RubricRequest) -> schemas.RubricResponse:
        """Criteria whose points are scaled to add up to the assignment's maximum marks; levels never exceed their criterion."""
        empty = schemas.RubricResponse(criteria=[], citations=[], insufficientContext=True)
        query = request.title + "\n" + (request.instructions or "")
        hits = self._search(self._scopes(course_key, request), query[:2000])
        context = _Context(hits)
        result = self._generate(
            "rubric", context, schemas.RubricModelOutput, self._settings.draft_temperature,
            {"criteriaCount": request.criteriaCount, "maxMarks": request.maxMarks},
            usable=lambda out: len(out.criteria) >= 1, teacher_note=query[:4000])
        if result is None or not result.criteria:
            return empty
        criteria = result.criteria[:request.criteriaCount]
        stated = sum(criterion.maxPoints for criterion in criteria)
        scale = request.maxMarks / stated if stated else 0
        scaled = [round(criterion.maxPoints * scale, 2) for criterion in criteria]
        scaled[-1] = round(request.maxMarks - sum(scaled[:-1]), 2)
        made = []
        for criterion, points in zip(criteria, scaled, strict=True):
            if points <= 0:
                return empty
            ratio = points / criterion.maxPoints
            levels = [schemas.RubricLevel(title=level.title, description=level.description or None,
                                          points=min(points, round(level.points * ratio, 2)))
                      for level in criterion.levels]
            made.append(schemas.RubricCriterion(title=criterion.title, description=criterion.description or None,
                                                maxPoints=points, levels=levels))
        return schemas.RubricResponse(criteria=made, citations=context.citations(result.sourceIds), insufficientContext=False)

    # --- Question bank drafts (v0.14.1) --------------------------------------------------------

    def bank_draft(self, course_key: str, request: schemas.BankDraftRequest) -> schemas.BankDraftResponse:
        """Questions in the bank's own types, each cited. Every keyed question (choice or true/false) is answered
        again by the model from its own sources without the key; a question whose answer differs is dropped."""
        empty = schemas.BankDraftResponse(questions=[], droppedForKeyMismatch=0, insufficientContext=True)
        chunks = self._selection(self._scopes(course_key, request), request.topicId)
        if not chunks:
            return empty
        context = _Context(chunks)
        wanted = set(request.questionTypes)

        def usable(out: schemas.BankModelOutput) -> list[tuple[schemas.BankModelQuestion, list[Citation]]]:
            kept = []
            for question in out.questions:
                citations = context.citations(question.sourceIds)
                if citations and question.questionType in wanted:
                    kept.append((question, citations))
            return kept[:request.questionCount]

        result = self._generate(
            "bank_questions", context, schemas.BankModelOutput, self._settings.draft_temperature,
            {"questionCount": request.questionCount, "difficulty": request.difficulty.value,
             "questionTypes": ", ".join(kind.value for kind in request.questionTypes)},
            usable=lambda out: bool(usable(out)), teacher_note=request.additionalInstruction)
        if result is None:
            return empty
        scopes = self._scopes(course_key, request)
        quality = _Quality(self._embeddings, self._settings, scopes, request.existingQuestions)
        questions, dropped, repeats = [], 0, 0
        for question, citations in usable(result):
            if question.questionType.keyed and not self._key_confirmed(question, context):
                dropped += 1
                continue
            checks = quality.check(question)
            if checks is None:
                repeats += 1
                continue
            questions.append(schemas.BankQuestion(
                questionType=question.questionType, text=question.text,
                options=[schemas.BankOption(text=o.text, correct=o.correct) for o in question.options]
                if question.questionType in (schemas.BankType.SINGLE_CHOICE, schemas.BankType.MULTIPLE_CHOICE) else [],
                trueFalseAnswer=question.trueFalseAnswer if question.questionType is schemas.BankType.TRUE_FALSE else None,
                acceptedAnswers=question.acceptedAnswers if question.questionType is schemas.BankType.SHORT_ANSWER else [],
                answerGuidance=question.answerGuidance if question.questionType is schemas.BankType.DESCRIPTIVE else None,
                explanation=question.explanation or None, suggestedMarks=question.suggestedMarks, citations=citations,
                checks=checks))
        return schemas.BankDraftResponse(questions=questions, droppedForKeyMismatch=dropped, droppedAsRepeats=repeats,
                                         insufficientContext=not questions and dropped == 0 and repeats == 0)

    def _key_confirmed(self, question: schemas.BankModelQuestion, context: _Context) -> bool:
        """Answers the question again from its own cited sources, without the key, and compares."""
        cited = _Context([context.chunks[ref.strip().upper()] for ref in question.sourceIds
                          if ref.strip().upper() in context.chunks])
        if question.questionType is schemas.BankType.TRUE_FALSE:
            asked = f"True or false: {question.text}"
            options: list[str] = []
        else:
            options = [option.text for option in question.options]
            asked = question.text + "\n" + "\n".join(f"{number}. {text}" for number, text in enumerate(options, start=1))
        try:
            check = self._generate("answer_check", cited, schemas.AnswerCheckOutput, 0.0,
                                   {"options": options, "kind": question.questionType.value}, question=asked)
        except ServiceError:
            return False
        if check is None:
            return False
        if question.questionType is schemas.BankType.TRUE_FALSE:
            return check.trueFalseAnswer is not None and check.trueFalseAnswer == question.trueFalseAnswer
        key = {number for number, option in enumerate(question.options, start=1) if option.correct}
        return set(check.chosenOptions) == key


def _content_words(text: str) -> set[str]:
    return {word for word in keyword.WORD.findall(text.lower()) if word not in keyword.STOP_WORDS}


def _word_overlap(first: set[str], second: set[str]) -> float:
    return len(first & second) / len(first | second) if first and second else 0.0


class _Quality:
    """The v0.14.2 checks for one batch of drafts: possible duplicates of bank questions (by meaning or wording), whether
    the match is in a final exam paper, repeats within the batch (dropped), and how close each draft is to the allowed
    course content."""

    DUPLICATE_SIMILARITY = 0.92
    DUPLICATE_WORD_OVERLAP = 0.8

    def __init__(self, embeddings: EmbeddingProvider, settings: Settings, scopes: list[tuple[CourseIndex, np.ndarray]],
                 existing: list[schemas.ExistingQuestion]) -> None:
        self._embeddings = embeddings
        self._settings = settings
        course, allowed = scopes[0]
        self._course_vectors = course.vectors[allowed] if allowed.any() else np.zeros((0, course.vectors.shape[1]))
        self._course_words = [_content_words(chunk.text) for chunk, keep in zip(course.chunks, allowed, strict=True) if keep]
        self._existing = existing
        self._existing_words = [_content_words(question.text) for question in existing]
        self._existing_vectors = self._embed([question.text for question in existing])
        self._kept_vectors: list[np.ndarray] = []
        self._kept_words: list[set[str]] = []

    def _embed(self, texts: list[str]) -> np.ndarray:
        if not texts:
            return np.zeros((0, 0))
        size = self._settings.embedding_batch_size
        vectors = []
        for start in range(0, len(texts), size):
            vectors.extend(self._embeddings.embed(texts[start:start + size]))
        return unit_vectors(vectors)

    def _similar(self, vector: np.ndarray, words: set[str], vectors: np.ndarray, word_sets: list[set[str]]) -> int | None:
        """The position of the closest match at or above the duplicate thresholds, or None."""
        best, best_score = None, 0.0
        for position, other in enumerate(word_sets):
            meaning = float(vectors[position] @ vector) if len(vectors) else 0.0
            wording = _word_overlap(words, other)
            if meaning >= self.DUPLICATE_SIMILARITY or wording >= self.DUPLICATE_WORD_OVERLAP:
                score = max(meaning, wording)
                if score > best_score:
                    best, best_score = position, score
        return best

    def check(self, question: schemas.BankModelQuestion) -> schemas.QualityChecks | None:
        """The checks for one draft, or None when it repeats an earlier draft of the same batch."""
        text = question.text
        vector = self._embed([text])[0]
        words = _content_words(text)
        if self._similar(vector, words, np.array(self._kept_vectors), self._kept_words) is not None:
            return None
        self._kept_vectors.append(vector)
        self._kept_words.append(words)
        match = self._similar(vector, words, self._existing_vectors, self._existing_words)
        closeness = float((self._course_vectors @ vector).max()) if len(self._course_vectors) else 0.0
        shares_words = any(words & chunk_words for chunk_words in self._course_words)
        return schemas.QualityChecks(
            duplicateOfQuestionId=None if match is None else self._existing[match].id,
            inPreviousPaper=match is not None and self._existing[match].inFinalPaper,
            outOfSyllabus=closeness < self._settings.min_relevance and not shares_words,
            suggestedDifficulty=question.difficultySuggestion.value if question.difficultySuggestion else None,
            suggestedBloomLevel=question.bloomSuggestion.value if question.bloomSuggestion else None)

