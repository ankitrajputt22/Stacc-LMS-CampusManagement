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
    """One published source. A topic carries its plain text; a study material carries its file."""

    sourceType: SourceType
    sourceId: int = Field(gt=0)
    topicId: int = Field(gt=0)
    title: str = Field(min_length=1, max_length=200)
    text: str | None = Field(default=None, max_length=25_000)
    fileName: str | None = Field(default=None, max_length=255)
    fileType: FileType | None = None
    contentBase64: str | None = None

    @model_validator(mode="after")
    def matches_type(self) -> IndexSource:
        if self.sourceType is SourceType.TOPIC:
            if not self.text or self.fileName or self.fileType or self.contentBase64:
                raise ValueError("a topic source carries text only")
        elif not (self.fileName and self.fileType and self.contentBase64) or self.text:
            raise ValueError("a study material source carries a file only")
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
    topicId: int
    title: str
    fileName: str | None = None
    pageNumber: int | None = None
    slideNumber: int | None = None


# --- Student requests --------------------------------------------------------------------------


class IndexedRequest(Strict):
    indexFingerprint: str = Field(pattern=FINGERPRINT)


class AskRequest(IndexedRequest):
    question: str

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


class SummaryRequest(IndexedRequest):
    topicId: int = Field(gt=0)


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


# --- What the model must return ----------------------------------------------------------------
# Source references are the short labels ("S1", "S2", ...) the prompt gives each retrieved chunk.

SourceRefs = Annotated[list[Annotated[str, _cut(10)]], Field(max_length=12)]


class AskModelOutput(BaseModel):
    insufficientContext: bool
    answer: Bounded(2000) = ""
    sourceIds: SourceRefs = []


class SummaryModelOutput(BaseModel):
    insufficientContext: bool
    summary: Bounded(1500) = ""
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
