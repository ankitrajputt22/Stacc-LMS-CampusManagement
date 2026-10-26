"""The internal API. Only ``/health`` is open (for container health checks, and it says nothing about
the setup); every ``/internal`` route needs the internal token. The Stacc backend is the only client:
it has already checked who the user is and what they may see before it calls any of these."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from app.models import schemas
from app.security import checked_course_key, require_internal_token

public = APIRouter()
internal = APIRouter(prefix="/internal", dependencies=[Depends(require_internal_token)])


@public.get("/health")
def health() -> dict:
    return {"status": "ok"}


@internal.get("/health")
def internal_health(request: Request) -> dict:
    return {"status": "ok", "provider": request.app.state.settings.provider}


@internal.post("/indexes/{course_key}/replace", response_model=schemas.ReplaceIndexResponse)
def replace_index(course_key: str, body: schemas.ReplaceIndexRequest, request: Request):
    return request.app.state.builder.replace(checked_course_key(course_key), body)


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
