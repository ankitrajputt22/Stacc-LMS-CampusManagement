"""Builds a course index from the published sources the backend sends.

A source that cannot be read, is too large, or has no text is skipped and counted; the course is
indexed from the rest. Only when no source yields any text does the whole build fail, and then the
live index stays as it was.
"""

from __future__ import annotations

import base64
import binascii
import logging
from dataclasses import dataclass

from app.config import Settings
from app.errors import no_indexable_content
from app.extraction.extractors import ExtractionError, Segment, extract
from app.indexing.chunking import chunk_segments
from app.indexing.store import CourseIndex, IndexStore, StoredChunk, unit_vectors
from app.models.schemas import ReplaceIndexRequest, ReplaceIndexResponse, SourceType
from app.providers.base import EmbeddingProvider

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class _Prepared:
    chunks: list[StoredChunk]
    indexed: int
    skipped: int


class IndexBuilder:
    def __init__(self, settings: Settings, embeddings: EmbeddingProvider, store: IndexStore) -> None:
        self._settings = settings
        self._embeddings = embeddings
        self._store = store

    def replace(self, course_key: str, request: ReplaceIndexRequest) -> ReplaceIndexResponse:
        prepared = self._prepare(request)
        if not prepared.chunks:
            raise no_indexable_content()
        vectors = []
        size = self._settings.embedding_batch_size
        for start in range(0, len(prepared.chunks), size):
            batch = prepared.chunks[start:start + size]
            # The title travels with each chunk, so a question naming the topic finds it.
            vectors.extend(self._embeddings.embed([f"{chunk.title}\n{chunk.text}" for chunk in batch]))
        index = CourseIndex(request.contentFingerprint, self._embeddings.model_id,
                            unit_vectors(vectors), tuple(prepared.chunks))
        self._store.replace(course_key, index)
        return ReplaceIndexResponse(
            indexedSourceCount=prepared.indexed,
            skippedSourceCount=prepared.skipped,
            chunkCount=len(prepared.chunks),
            contentFingerprint=request.contentFingerprint,
        )

    def _prepare(self, request: ReplaceIndexRequest) -> _Prepared:
        settings = self._settings
        chunks: list[StoredChunk] = []
        indexed = skipped = 0
        total_chars = 0
        for position, source in enumerate(request.sources):
            if position >= settings.max_sources or total_chars >= settings.max_total_chars:
                skipped += len(request.sources) - position
                break
            segments = self._segments(source, min(settings.max_chars_per_source, settings.max_total_chars - total_chars))
            pieces = chunk_segments(segments, settings.chunk_size, settings.chunk_overlap)
            pieces = pieces[: max(settings.max_chunks - len(chunks), 0)]
            if not pieces:
                skipped += 1
                continue
            indexed += 1
            total_chars += sum(len(piece.text) for piece in pieces)
            for piece in pieces:
                chunks.append(StoredChunk(
                    sourceType=source.sourceType.value,
                    sourceId=source.sourceId,
                    topicId=source.topicId,
                    title=source.title,
                    fileName=source.fileName,
                    pageNumber=piece.page_number,
                    slideNumber=piece.slide_number,
                    text=piece.text,
                ))
        return _Prepared(chunks, indexed, skipped)

    def _segments(self, source, max_chars: int) -> list[Segment]:
        if source.sourceType is SourceType.TOPIC:
            return [Segment(source.text[:max_chars])]
        try:
            # Checked before decoding: base64 text is a third longer than the file.
            if len(source.contentBase64) > (self._settings.max_source_bytes * 4) // 3 + 4:
                raise ExtractionError("too large")
            data = base64.b64decode(source.contentBase64, validate=True)
            return extract(source.fileType, data, max_chars)
        except (ExtractionError, binascii.Error, ValueError) as error:
            # Only the kind of problem is logged, never the file name or its text.
            log.info("Skipped a %s source: %s", source.fileType, type(error).__name__)
            return []
