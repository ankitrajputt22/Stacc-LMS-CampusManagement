"""The internal API. Only ``/health`` is open (for container health checks, and it says nothing about
the setup); every ``/internal`` route needs the internal token. The Stacc backend is the only client:
it has already checked who the user is and what they may see before it calls any of these."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Request, Response

from app.models import schemas
from app.security import checked_course_key, require_internal_token

public = APIRouter()
internal = APIRouter(prefix="/internal/v1", dependencies=[Depends(require_internal_token)])


@public.get("/health")
def health() -> dict:
    return {"status": "ok"}


@internal.get("/health")
def internal_health(request: Request) -> dict:
    return {"status": "ok", "provider": request.app.state.settings.provider}


@internal.post("/indexes/{course_key}/replace", response_model=schemas.ReplaceIndexResponse)
def replace_index(course_key: str, body: schemas.ReplaceIndexRequest, request: Request):
    return request.app.state.builder.replace(checked_course_key(course_key), body)


@internal.post("/indexes/{course_key}/question-bank-draft", response_model=schemas.BankDraftResponse)
def question_bank_draft(course_key: str, body: schemas.BankDraftRequest, request: Request):
    return request.app.state.learning.bank_draft(checked_course_key(course_key), body)


@internal.post("/indexes/{course_key}/evaluate", response_model=schemas.EvaluateResponse)
def evaluate(course_key: str, body: schemas.EvaluateRequest, request: Request):
    return request.app.state.learning.evaluate(checked_course_key(course_key), body)


@internal.post("/indexes/{course_key}/flashcards", response_model=schemas.FlashcardResponse)
def flashcards(course_key: str, body: schemas.FlashcardRequest, request: Request):
    return request.app.state.learning.flashcards(checked_course_key(course_key), body)


@internal.post("/indexes/{course_key}/model-answer", response_model=schemas.ModelAnswerResponse)
def model_answer(course_key: str, body: schemas.ModelAnswerRequest, request: Request):
    return request.app.state.learning.model_answer(checked_course_key(course_key), body)


@internal.post("/indexes/{course_key}/rubric", response_model=schemas.RubricResponse)
def rubric(course_key: str, body: schemas.RubricRequest, request: Request):
    return request.app.state.learning.rubric(checked_course_key(course_key), body)


@internal.delete("/indexes/{course_key}", status_code=204)
def delete_index(course_key: str, request: Request) -> Response:
    """Removes an index completely, such as a student's notes index after their last note is deleted. Deleting a
    missing index changes nothing."""
    request.app.state.store.delete(checked_course_key(course_key))
    return Response(status_code=204)


@internal.post("/indexes/{course_key}/ask", response_model=schemas.AskResponse)
def ask(course_key: str, body: schemas.AskRequest, request: Request):
    return request.app.state.learning.ask(checked_course_key(course_key), body)


@internal.post("/indexes/{course_key}/summary", response_model=schemas.SummaryResponse)
def summary(course_key: str, body: schemas.SummaryRequest, request: Request):
    return request.app.state.learning.summary(checked_course_key(course_key), body)


@internal.post("/indexes/{course_key}/practice", response_model=schemas.PracticeResponse)
def practice(course_key: str, body: schemas.PracticeRequest, request: Request):
    return request.app.state.learning.practice(checked_course_key(course_key), body)


@internal.post("/indexes/{course_key}/assessment-draft", response_model=schemas.AssessmentDraftResponse)
def assessment_draft(course_key: str, body: schemas.AssessmentDraftRequest, request: Request):
    return request.app.state.learning.assessment_draft(checked_course_key(course_key), body)
