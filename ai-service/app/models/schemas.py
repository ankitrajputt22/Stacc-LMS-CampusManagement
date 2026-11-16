"""Request and response shapes of the internal API, and the shapes the model must return.

Field names are camelCase to match the Stacc backend's JSON. Requests forbid unknown fields, so a
caller cannot slip extra data in. Model output is never passed on as-is: it is parsed into the
``*ModelOutput`` classes, overlong text is cut to its limit, and only then turned into a response.
"""

from __future__ import annotations

from enum import StrEnum
from typing import Annotated

from pydantic import BaseModel, BeforeValidator, ConfigDict, Field, field_validator, model_validator

FINGERPRINT = r"^[a-f0-9]{64}$"


def _cut(limit: int):
    def cut(value: object) -> object:
        if isinstance(value, str):
            value = value.strip()
            return value[:limit]
        return value

    return BeforeValidator(cut)


def Bounded(limit: int, min_length: int = 0):  # noqa: N802 - reads as a type
    return Annotated[str, _cut(limit), Field(min_length=min_length, max_length=limit)]


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


class SourceType(StrEnum):
    TOPIC = "TOPIC"
    STUDY_MATERIAL = "STUDY_MATERIAL"
    # Kept in their own indexes (v0.13.5, v0.13.6): a course's teacher-only documents and a student's private notes.
    TEACHER_DOCUMENT = "TEACHER_DOCUMENT"
    NOTE = "NOTE"

    @property
    def carries_text(self) -> bool:
        return self is not SourceType.STUDY_MATERIAL


class FileType(StrEnum):
    PDF = "PDF"
    TXT = "TXT"
    DOCX = "DOCX"
    PPTX = "PPTX"
    XLSX = "XLSX"


class Difficulty(StrEnum):
    EASY = "EASY"
    MEDIUM = "MEDIUM"
    HARD = "HARD"
    MIXED = "MIXED"


class QuestionType(StrEnum):
    MCQ = "MCQ"
    SHORT_ANSWER = "SHORT_ANSWER"
    LONG_ANSWER = "LONG_ANSWER"
    MIXED = "MIXED"


# --- Index replacement -------------------------------------------------------------------------


class IndexSource(Strict):
    """One source. A topic, teacher document, or note carries its text; a study material carries its file. Only
    topics and study materials belong to a topic."""

    sourceType: SourceType
    sourceId: int = Field(gt=0)
    topicId: int | None = Field(default=None, gt=0)
    title: str = Field(min_length=1, max_length=200)
    text: str | None = Field(default=None, max_length=25_000)
    fileName: str | None = Field(default=None, max_length=255)
    fileType: FileType | None = None
    contentBase64: str | None = None

    @model_validator(mode="after")
    def matches_type(self) -> IndexSource:
        if self.sourceType.carries_text:
            if not self.text or self.fileName or self.fileType or self.contentBase64:
                raise ValueError("a text source carries text only")
        elif not (self.fileName and self.fileType and self.contentBase64) or self.text:
            raise ValueError("a study material source carries a file only")
        if (self.topicId is None) != (self.sourceType in (SourceType.TEACHER_DOCUMENT, SourceType.NOTE)):
            raise ValueError("topics and study materials name their topic; other sources do not")
        return self


class ReplaceIndexRequest(Strict):
    contentFingerprint: str = Field(pattern=FINGERPRINT)
    sources: list[IndexSource] = Field(min_length=1)


class ReplaceIndexResponse(BaseModel):
    indexedSourceCount: int
    skippedSourceCount: int
    chunkCount: int
    contentFingerprint: str


# --- Citations ---------------------------------------------------------------------------------


class Citation(BaseModel):
    sourceType: SourceType
    sourceId: int
    topicId: int | None = None
    title: str
    fileName: str | None = None
    pageNumber: int | None = None
    slideNumber: int | None = None


# --- Student requests --------------------------------------------------------------------------


class SourceRef(Strict):
    """One source a request may use, as the backend allows it right now."""

    sourceType: SourceType
    sourceId: int = Field(gt=0)


AllowedSources = Annotated[list[SourceRef] | None, Field(default=None, max_length=5000)]


class IndexRef(Strict):
    """Another index to search with the course's own, such as a teacher's or a student's notes index."""

    indexKey: str = Field(pattern=r"^[a-f0-9]{32}$")
    indexFingerprint: str = Field(pattern=FINGERPRINT)
    allowedSources: AllowedSources = None


class IndexedRequest(Strict):
    """A request over the course index named in the path, and optionally more indexes.

    ``allowedSources`` lists the sources the backend allows at this moment; chunks of any other source are never
    searched, summarised, or cited, even if an index still holds them. ``None`` allows every source of the index.
    """

    indexFingerprint: str = Field(pattern=FINGERPRINT)
    allowedSources: AllowedSources = None
    additionalIndexes: Annotated[list[IndexRef], Field(default_factory=list, max_length=3)]


class AnswerStyle(StrEnum):
    """How to answer (v0.15.1): normally, more simply, or with an example."""
    NORMAL = "NORMAL"
    SIMPLER = "SIMPLER"
    EXAMPLE = "EXAMPLE"


class Turn(Strict):
    """An earlier question and answer on the student's open page (v0.15.1); the page sends them, nothing is stored."""
    question: str = Field(min_length=1, max_length=1000)
    answer: str = Field(min_length=1, max_length=2000)


class AskRequest(IndexedRequest):
    question: str
    history: Annotated[list[Turn], Field(default_factory=list, max_length=3)]
    style: AnswerStyle = AnswerStyle.NORMAL

    @field_validator("question")
    @classmethod
    def trimmed(cls, value: str) -> str:
        value = value.strip()
        if not 2 <= len(value) <= 1000:
            raise ValueError("the question must have 2 to 1000 characters")
        return value


class AskResponse(BaseModel):
    answer: str | None
    insufficientContext: bool
    citations: list[Citation]


class SummaryStyle(StrEnum):
    """How to summarise (v0.15.2)."""
    BRIEF = "BRIEF"
    DETAILED = "DETAILED"
    REVISION_NOTES = "REVISION_NOTES"


class SummaryRequest(IndexedRequest):
    """A topic's summary, or, without a topic, a summary of whatever the allowed sources hold, such as one module or one
    document (v0.15.2)."""
    topicId: int | None = Field(default=None, gt=0)
    style: SummaryStyle = SummaryStyle.BRIEF


class SummaryResponse(BaseModel):
    summary: str | None
    keyPoints: list[str]
    insufficientContext: bool
    citations: list[Citation]


class PracticeRequest(IndexedRequest):
    questionCount: int = Field(default=5, ge=1, le=10)
    topicId: int | None = Field(default=None, gt=0)


class PracticeQuestion(BaseModel):
    question: str
    suggestedAnswer: str
    citations: list[Citation]


class PracticeResponse(BaseModel):
    questions: list[PracticeQuestion]
    insufficientContext: bool


class AssessmentDraftRequest(IndexedRequest):
    questionCount: int = Field(ge=1, le=20)
    difficulty: Difficulty
    questionType: QuestionType
    additionalInstruction: str | None = Field(default=None, max_length=500)


class AssessmentQuestion(BaseModel):
    questionType: QuestionType
    prompt: str
    options: list[str]
    suggestedAnswer: str
    suggestedMarks: float
    citations: list[Citation]


class AssessmentDraftResponse(BaseModel):
    titleSuggestion: str | None
    instructionsSuggestion: str | None
    questions: list[AssessmentQuestion]
    insufficientContext: bool


# --- Question bank drafts (v0.14.1) ------------------------------------------------------------------


class BankType(StrEnum):
    """The question bank's own kinds of question."""
    SINGLE_CHOICE = "SINGLE_CHOICE"
    MULTIPLE_CHOICE = "MULTIPLE_CHOICE"
    TRUE_FALSE = "TRUE_FALSE"
    SHORT_ANSWER = "SHORT_ANSWER"
    DESCRIPTIVE = "DESCRIPTIVE"

    @property
    def keyed(self) -> bool:
        """Whether the question has a key the self-check can test."""
        return self in (BankType.SINGLE_CHOICE, BankType.MULTIPLE_CHOICE, BankType.TRUE_FALSE)


class Bloom(StrEnum):
    REMEMBER = "REMEMBER"
    UNDERSTAND = "UNDERSTAND"
    APPLY = "APPLY"
    ANALYZE = "ANALYZE"
    EVALUATE = "EVALUATE"
    CREATE = "CREATE"


class ExistingQuestion(Strict):
    """A question already in the course's bank, for the duplicate and previous-paper checks (v0.14.2)."""
    id: int = Field(gt=0)
    text: str = Field(min_length=1, max_length=2000)
    inFinalPaper: bool = False


class BankDraftRequest(IndexedRequest):
    questionCount: int = Field(ge=1, le=10)
    questionTypes: list[BankType] = Field(min_length=1, max_length=5)
    difficulty: Difficulty
    topicId: int | None = Field(default=None, gt=0)
    additionalInstruction: str | None = Field(default=None, max_length=500)
    existingQuestions: list[ExistingQuestion] = Field(default_factory=list, max_length=500)


class QualityChecks(BaseModel):
    """Flags worked out by code and embeddings, and the model's suggestions, for the reviewer (v0.14.2)."""
    duplicateOfQuestionId: int | None
    inPreviousPaper: bool
    outOfSyllabus: bool
    suggestedDifficulty: str | None
    suggestedBloomLevel: str | None


class BankOption(BaseModel):
    text: str
    correct: bool


class BankQuestion(BaseModel):
    questionType: BankType
    text: str
    options: list[BankOption]
    trueFalseAnswer: bool | None
    acceptedAnswers: list[str]
    answerGuidance: str | None
    explanation: str | None
    suggestedMarks: float
    citations: list[Citation]
    checks: QualityChecks


class BankDraftResponse(BaseModel):
    questions: list[BankQuestion]
    droppedForKeyMismatch: int
    droppedAsRepeats: int = 0
    insufficientContext: bool


# --- Model answers and rubric suggestions (v0.14.5) ------------------------------------------------


class ModelAnswerRequest(IndexedRequest):
    questionText: str = Field(min_length=1, max_length=5000)
    questionType: BankType
    marks: float = Field(gt=0, le=1000)


class ModelAnswerResponse(BaseModel):
    answer: str | None
    keyPoints: list[str]
    citations: list[Citation]
    insufficientContext: bool


class RubricRequest(IndexedRequest):
    title: str = Field(min_length=1, max_length=200)
    instructions: str | None = Field(default=None, max_length=20_000)
    maxMarks: float = Field(gt=0, le=1000)
    criteriaCount: int = Field(ge=2, le=8)


class RubricLevel(BaseModel):
    title: str
    description: str | None
    points: float


class RubricCriterion(BaseModel):
    title: str
    description: str | None
    maxPoints: float
    levels: list[RubricLevel]


class RubricResponse(BaseModel):
    criteria: list[RubricCriterion]
    citations: list[Citation]
    insufficientContext: bool


# --- What the model must return ----------------------------------------------------------------
# Source references are the short labels ("S1", "S2", ...) the prompt gives each retrieved chunk.

SourceRefs = Annotated[list[Annotated[str, _cut(10)]], Field(max_length=12)]


class AskModelOutput(BaseModel):
    insufficientContext: bool
    answer: Bounded(2000) = ""
    sourceIds: SourceRefs = []


class SummaryModelOutput(BaseModel):
    insufficientContext: bool
    summary: Bounded(4000) = ""
    keyPoints: Annotated[list[Bounded(300)], Field(max_length=8)] = []
    sourceIds: SourceRefs = []


class PracticeModelQuestion(BaseModel):
    question: Bounded(600, 1)
    suggestedAnswer: Bounded(1500, 1)
    sourceIds: SourceRefs = []


class PracticeModelOutput(BaseModel):
    questions: Annotated[list[PracticeModelQuestion], Field(max_length=10)]


class AssessmentModelQuestion(BaseModel):
    questionType: QuestionType
    prompt: Bounded(1500, 1)
    options: Annotated[list[Bounded(300, 1)], Field(max_length=6)] = []
    suggestedAnswer: Bounded(2000, 1)
    suggestedMarks: float = Field(gt=0, le=100)
    sourceIds: SourceRefs = []

    @model_validator(mode="after")
    def options_fit_type(self) -> AssessmentModelQuestion:
        if self.questionType is QuestionType.MIXED:
            raise ValueError("each question needs a concrete type")
        if self.questionType is QuestionType.MCQ and len(self.options) < 2:
            raise ValueError("a multiple-choice question needs at least two options")
        if self.questionType is not QuestionType.MCQ:
            self.options = []
        self.suggestedMarks = round(self.suggestedMarks, 2)
        return self


class AssessmentModelOutput(BaseModel):
    titleSuggestion: Bounded(200, 1)
    instructionsSuggestion: Bounded(2000)
    questions: Annotated[list[AssessmentModelQuestion], Field(max_length=20)]


class BankModelOption(BaseModel):
    text: Bounded(300, 1)
    correct: bool


class BankModelQuestion(BaseModel):
    questionType: BankType
    text: Bounded(1500, 1)
    options: Annotated[list[BankModelOption], Field(max_length=6)] = []
    trueFalseAnswer: bool | None = None
    acceptedAnswers: Annotated[list[Bounded(200, 1)], Field(max_length=5)] = []
    answerGuidance: Bounded(2000) | None = None
    explanation: Bounded(2000) | None = None
    suggestedMarks: float = Field(gt=0, le=100)
    difficultySuggestion: Difficulty | None = None
    bloomSuggestion: Bloom | None = None
    sourceIds: SourceRefs = []

    @model_validator(mode="after")
    def answers_fit_type(self) -> BankModelQuestion:
        correct = sum(option.correct for option in self.options)
        kind = self.questionType
        if kind is BankType.SINGLE_CHOICE and (len(self.options) < 2 or correct != 1):
            raise ValueError("a single-choice question needs two or more options and exactly one correct")
        if kind is BankType.MULTIPLE_CHOICE and (len(self.options) < 2 or correct < 1):
            raise ValueError("a multiple-choice question needs two or more options and one or more correct")
        if kind is BankType.TRUE_FALSE and self.trueFalseAnswer is None:
            raise ValueError("a true/false question needs its answer")
        if kind is BankType.SHORT_ANSWER and not self.acceptedAnswers:
            raise ValueError("a short-answer question needs accepted answers")
        if kind is BankType.DESCRIPTIVE and not self.answerGuidance:
            raise ValueError("a descriptive question needs marking guidance")
        return self


class BankModelOutput(BaseModel):
    questions: Annotated[list[BankModelQuestion], Field(max_length=10)]


class AnswerCheckOutput(BaseModel):
    """The model's own answer to a keyed question, given without the key: the chosen option numbers (from 1), or the
    true/false answer."""
    chosenOptions: Annotated[list[int], Field(max_length=10)] = []
    trueFalseAnswer: bool | None = None


class ModelAnswerOutput(BaseModel):
    insufficientContext: bool
    answer: Bounded(4000) = ""
    keyPoints: Annotated[list[Bounded(300, 1)], Field(max_length=8)] = []
    sourceIds: SourceRefs = []


class RubricModelLevel(BaseModel):
    title: Bounded(100, 1)
    description: Bounded(500) | None = None
    points: float = Field(ge=0, le=1000)


class RubricModelCriterion(BaseModel):
    title: Bounded(200, 1)
    description: Bounded(1000) | None = None
    maxPoints: float = Field(gt=0, le=1000)
    levels: Annotated[list[RubricModelLevel], Field(max_length=6)] = []


class RubricModelOutput(BaseModel):
    criteria: Annotated[list[RubricModelCriterion], Field(max_length=8)]
    sourceIds: SourceRefs = []

