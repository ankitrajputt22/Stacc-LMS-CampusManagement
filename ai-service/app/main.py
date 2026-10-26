"""The Stacc AI service: an internal FastAPI app that only the Stacc backend calls.

Start it with ``uvicorn app.main:create_app --factory``. Settings come from the environment (see
``app/config.py`` and docs/ai-learning.md); the app refuses to start without the internal token.
"""

from __future__ import annotations

import logging
import time

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.routes import internal, public
from app.config import Settings
from app.errors import ServiceError, provider_unavailable
from app.indexing.builder import IndexBuilder
from app.indexing.store import IndexStore
from app.providers.base import EmbeddingProvider, ModelProvider, ProviderUnavailable
from app.providers.factory import create_providers
from app.retrieval.service import LearningService

log = logging.getLogger("stacc.ai")


def _error(error: ServiceError) -> JSONResponse:
    return JSONResponse(status_code=error.status, content={"code": error.code, "message": error.message})


class _BodyLimit:
    """Refuses request bodies over the limit, whether or not they declare their length."""

    def __init__(self, app, limit: int) -> None:
        self.app = app
        self.limit = limit

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        declared = dict(scope.get("headers", [])).get(b"content-length")
        too_large = _error(ServiceError(413, "REQUEST_TOO_LARGE", "The request is too large."))
        if declared is not None and declared.isdigit() and int(declared) > self.limit:
            return await too_large(scope, receive, send)
        received = 0

        async def limited_receive():
            nonlocal received
            message = await receive()
            if message["type"] == "http.request":
                received += len(message.get("body", b""))
                if received > self.limit:
                    raise ServiceError(413, "REQUEST_TOO_LARGE", "The request is too large.")
            return message

        try:
            await self.app(scope, limited_receive, send)
        except ServiceError as error:
            await _error(error)(scope, receive, send)


def create_app(settings: Settings | None = None, models: ModelProvider | None = None,
               embeddings: EmbeddingProvider | None = None) -> FastAPI:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    settings = settings or Settings.from_env()
    if models is None or embeddings is None:
        models, embeddings = create_providers(settings)
    store = IndexStore(settings.index_dir)

    app = FastAPI(title="Stacc AI service", docs_url=None, redoc_url=None, openapi_url=None)
    app.state.settings = settings
    app.state.builder = IndexBuilder(settings, embeddings, store)
    app.state.learning = LearningService(settings, models, embeddings, store)
    app.include_router(public)
    app.include_router(internal)

    @app.middleware("http")
    async def log_requests(request: Request, call_next):
        # Operational metadata only: never the question, course text, prompt, or model output.
        started = time.perf_counter()
        response = await call_next(request)
        parts = request.url.path.strip("/").split("/")
        task = parts[-1] if parts else ""
        course = parts[2] if len(parts) == 4 and parts[1] == "indexes" else "-"
        if task != "health":
            log.info("task=%s course=%s status=%s ms=%d", task, course, response.status_code,
                     (time.perf_counter() - started) * 1000)
        return response

    @app.exception_handler(ServiceError)
    async def service_error(_: Request, error: ServiceError):
        return _error(error)

    @app.exception_handler(RequestValidationError)
    async def invalid_request(_: Request, __: RequestValidationError):
        # The submitted values are never echoed back.
        return _error(ServiceError(400, "INVALID_REQUEST", "The request is not valid."))

    @app.exception_handler(ProviderUnavailable)
    async def unavailable(_: Request, __: ProviderUnavailable):
        return _error(provider_unavailable())

    @app.exception_handler(Exception)
    async def unexpected(_: Request, error: Exception):
        log.error("Unexpected %s", type(error).__name__)
        return _error(ServiceError(500, "INTERNAL_ERROR", "The AI service could not complete the request."))

    app.add_middleware(_BodyLimit, limit=settings.max_request_bytes)
    return app
