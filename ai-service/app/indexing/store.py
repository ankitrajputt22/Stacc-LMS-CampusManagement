"""The local vector index: one directory per course key, replaced atomically.

Layout under the index root (a Docker volume in production)::

    <courseKey>/current            the name of the live version
    <courseKey>/v-<uuid>/          one complete version
        manifest.json              format, content fingerprint, embedding model, sizes
        vectors.npy                float32 unit vectors, one row per chunk
        chunks.json                each chunk's text and source metadata

A new version is written to a temporary directory, read back and checked, renamed into place, and only
then made live by atomically replacing ``current``. A failure before that point leaves the live version
untouched. Vectors are compared with a plain dot product: a course index is small enough that an exact
search is fast and needs no extra library.
"""

from __future__ import annotations

import json
import os
import re
import shutil
import threading
import uuid
from collections import OrderedDict
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np

from app.errors import ServiceError, index_not_found
from app.security import checked_course_key

FORMAT_VERSION = 1
VERSION_NAME = re.compile(r"^v-[a-f0-9]{32}$")
CACHE_SIZE = 16


@dataclass(frozen=True)
class StoredChunk:
    sourceType: str
    sourceId: int
    topicId: int
    title: str
    fileName: str | None
    pageNumber: int | None
    slideNumber: int | None
    text: str


@dataclass(frozen=True)
class CourseIndex:
    fingerprint: str
    embedding_model: str
    vectors: np.ndarray
    chunks: tuple[StoredChunk, ...]

    def search(self, query: np.ndarray, top_k: int) -> list[tuple[int, float]]:
        """The best ``top_k`` chunks for a unit query vector, best first, as (position, score)."""
        scores = self.vectors @ query
        count = min(top_k, len(scores))
        best = np.argpartition(-scores, count - 1)[:count]
        return sorted(((int(i), float(scores[i])) for i in best), key=lambda item: (-item[1], item[0]))


def unit_vectors(vectors: list[list[float]]) -> np.ndarray:
    matrix = np.asarray(vectors, dtype=np.float32)
    if matrix.ndim != 2 or matrix.shape[0] == 0 or matrix.shape[1] == 0 or not np.all(np.isfinite(matrix)):
        raise ValueError("embeddings must be a non-empty matrix of finite numbers")
    norms = np.linalg.norm(matrix, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    return matrix / norms


class IndexStore:
    def __init__(self, root: Path) -> None:
        self._root = root.resolve()
        self._root.mkdir(parents=True, exist_ok=True)
        self._locks: dict[str, threading.Lock] = {}
        self._locks_guard = threading.Lock()
        self._cache: OrderedDict[tuple[str, str], CourseIndex] = OrderedDict()
        self._cache_guard = threading.Lock()

    def _course_dir(self, course_key: str) -> Path:
        path = (self._root / checked_course_key(course_key)).resolve()
        if path.parent != self._root:
            raise ServiceError(400, "INVALID_COURSE_KEY", "The course key is not valid.")
        return path

    def _lock(self, course_key: str) -> threading.Lock:
        with self._locks_guard:
            return self._locks.setdefault(course_key, threading.Lock())

    def replace(self, course_key: str, index: CourseIndex) -> None:
        course_dir = self._course_dir(course_key)
        with self._lock(course_key):
            course_dir.mkdir(exist_ok=True)
            temporary = course_dir / f".tmp-{uuid.uuid4().hex}"
            try:
                self._write(temporary, index)
                self._read(temporary)  # Validate what is on disk before it can go live.
                version = f"v-{uuid.uuid4().hex}"
                os.replace(temporary, course_dir / version)
                pointer = course_dir / f".current-{uuid.uuid4().hex}"
                pointer.write_text(version, encoding="utf-8")
                os.replace(pointer, course_dir / "current")
            finally:
                shutil.rmtree(temporary, ignore_errors=True)
            self._remove_old_versions(course_dir, keep=version)

    def load(self, course_key: str) -> CourseIndex:
        course_dir = self._course_dir(course_key)
        for _ in range(2):  # A replacement may remove a version between reading the pointer and loading it.
            try:
                version = (course_dir / "current").read_text(encoding="utf-8").strip()
            except FileNotFoundError:
                raise index_not_found() from None
            if not VERSION_NAME.fullmatch(version):
                raise index_not_found()
            with self._cache_guard:
                cached = self._cache.get((course_key, version))
                if cached is not None:
                    self._cache.move_to_end((course_key, version))
                    return cached
            try:
                index = self._read(course_dir / version)
            except FileNotFoundError:
                continue
            with self._cache_guard:
                self._cache[(course_key, version)] = index
                while len(self._cache) > CACHE_SIZE:
                    self._cache.popitem(last=False)
            return index
        raise index_not_found()

    @staticmethod
    def _write(directory: Path, index: CourseIndex) -> None:
        directory.mkdir()
        np.save(directory / "vectors.npy", index.vectors.astype(np.float32), allow_pickle=False)
        (directory / "chunks.json").write_text(
            json.dumps([asdict(chunk) for chunk in index.chunks], ensure_ascii=False), encoding="utf-8")
        (directory / "manifest.json").write_text(json.dumps({
            "formatVersion": FORMAT_VERSION,
            "contentFingerprint": index.fingerprint,
            "embeddingModel": index.embedding_model,
            "chunkCount": len(index.chunks),
            "dimension": int(index.vectors.shape[1]),
        }), encoding="utf-8")

    @staticmethod
    def _read(directory: Path) -> CourseIndex:
        manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
        vectors = np.load(directory / "vectors.npy", allow_pickle=False)
        chunks = tuple(StoredChunk(**item) for item in json.loads((directory / "chunks.json").read_text(encoding="utf-8")))
        if (manifest.get("formatVersion") != FORMAT_VERSION or vectors.ndim != 2
                or vectors.shape[0] != len(chunks) or vectors.shape[0] != manifest.get("chunkCount")
                or vectors.shape[1] != manifest.get("dimension") or not chunks):
            raise ValueError("the stored index is inconsistent")
        return CourseIndex(manifest["contentFingerprint"], manifest["embeddingModel"], vectors, chunks)

    @staticmethod
    def _remove_old_versions(course_dir: Path, keep: str) -> None:
        for entry in course_dir.iterdir():
            if entry.is_dir() and entry.name != keep and (VERSION_NAME.fullmatch(entry.name) or entry.name.startswith(".tmp-")):
                shutil.rmtree(entry, ignore_errors=True)
