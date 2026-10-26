"""One error type with a stable code. Messages are short and never contain course text, prompts,
model output, file paths, or stack traces."""

from __future__ import annotations


class ServiceError(Exception):
    def __init__(self, status: int, code: str, message: str) -> None:
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message


def index_not_found() -> ServiceError:
    return ServiceError(404, "INDEX_NOT_FOUND", "No index exists for this course.")


def index_mismatch() -> ServiceError:
    return ServiceError(409, "INDEX_MISMATCH", "The course index does not match the requested content.")


def no_indexable_content() -> ServiceError:
    return ServiceError(422, "NO_INDEXABLE_CONTENT", "No source produced any text to index.")


def provider_unavailable() -> ServiceError:
    return ServiceError(503, "PROVIDER_UNAVAILABLE", "The model provider is unavailable.")


def model_output_invalid() -> ServiceError:
    return ServiceError(502, "MODEL_OUTPUT_INVALID", "The model did not return a usable response.")
