"""The internal token check and the course-key rule.

Only the Stacc backend calls this service. It sends the shared internal token in the
``X-Stacc-AI-Token`` header; users' access tokens never reach this service. A course key is an
opaque value the backend derives from an LMS course; it is checked strictly before it is used as
a directory name.
"""

from __future__ import annotations

import hmac
import re

from fastapi import Header, Request

from app.errors import ServiceError

TOKEN_HEADER = "X-Stacc-AI-Token"  # noqa: S105 - a header name, not a secret
COURSE_KEY_PATTERN = re.compile(r"^[a-f0-9]{32}$")


def require_internal_token(request: Request, x_stacc_ai_token: str | None = Header(default=None, alias=TOKEN_HEADER)) -> None:
    expected: str = request.app.state.settings.internal_token
    given = (x_stacc_ai_token or "").encode("utf-8")
    if not given or not hmac.compare_digest(given, expected.encode("utf-8")):
        raise ServiceError(401, "UNAUTHORIZED", "A valid internal token is required.")


def checked_course_key(course_key: str) -> str:
    """The course key, if it is exactly 32 lower-case hex characters. Anything else is refused."""
    if not COURSE_KEY_PATTERN.fullmatch(course_key):
        raise ServiceError(400, "INVALID_COURSE_KEY", "The course key is not valid.")
    return course_key
