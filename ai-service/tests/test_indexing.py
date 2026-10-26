from __future__ import annotations

import numpy as np
import pytest

from app.indexing import store as store_module
from helpers import COURSE, FINGERPRINT, HEADERS, OTHER_FINGERPRINT, material, pdf_bytes, pptx_bytes, replace, topic

NORMALIZATION = "Database normalization organises tables to reduce redundancy. Third normal form removes transitive dependencies."


def ask(client, question, fingerprint=FINGERPRINT):
    return client.post(f"/internal/indexes/{COURSE}/ask", headers=HEADERS,
                       json={"indexFingerprint": fingerprint, "question": question})


def test_replace_builds_an_index_and_counts_sources(client):
    response = replace(client, [
        topic(1, "Database Normalization", NORMALIZATION),
        material(10, 1, "Normalization notes", "normalization-notes.pdf", "PDF",
                 pdf_bytes(["Overview of keys", "Boyce Codd normal form is stricter than third normal form"])),
        material(11, 1, "Broken file", "broken.pdf", "PDF", b"%PDF-1.4 not really"),
        material(12, 1, "Empty slides", "empty.pptx", "PPTX", pptx_bytes([" "])),
    ])
    assert response.status_code == 200
    body = response.json()
    assert body["indexedSourceCount"] == 2
    assert body["skippedSourceCount"] == 2
    assert body["chunkCount"] >= 3
    assert body["contentFingerprint"] == FINGERPRINT


def test_no_extractable_text_fails_and_keeps_the_live_index(client):
    assert replace(client, [topic(1, "Database Normalization", NORMALIZATION)]).status_code == 200
    response = replace(client, [material(11, 1, "Broken", "broken.pdf", "PDF", b"not a pdf")], OTHER_FINGERPRINT)
    assert response.status_code == 422
    assert response.json()["code"] == "NO_INDEXABLE_CONTENT"
    # The earlier index still answers for its own content.
    assert ask(client, "What does normalization reduce?").json()["insufficientContext"] is False


def test_replacement_is_atomic_and_cleans_old_versions(client, settings):
    replace(client, [topic(1, "Database Normalization", NORMALIZATION)])
    assert replace(client, [topic(2, "Transactions", "A transaction is atomic, consistent, isolated, and durable.")],
                   OTHER_FINGERPRINT).status_code == 200
    course_dir = settings.index_dir.resolve() / COURSE
    versions = [entry.name for entry in course_dir.iterdir() if entry.is_dir()]
    assert len(versions) == 1
    assert (course_dir / "current").read_text() == versions[0]
    # The old content is gone and the new content answers.
    assert ask(client, "What does normalization reduce?", FINGERPRINT).status_code == 409
    answer = ask(client, "Is a transaction atomic?", OTHER_FINGERPRINT).json()
    assert answer["citations"][0]["sourceId"] == 2


def test_a_failed_write_leaves_the_previous_index_live(settings, monkeypatch):
    from fastapi.testclient import TestClient

    from app.main import create_app
    from app.providers.fake import FakeEmbeddingProvider, FakeModelProvider

    client = TestClient(create_app(settings, FakeModelProvider(), FakeEmbeddingProvider()),
                        raise_server_exceptions=False)
    replace(client, [topic(1, "Database Normalization", NORMALIZATION)])

    def broken_save(*args, **kwargs):
        raise OSError("disk full")

    monkeypatch.setattr(store_module.np, "save", broken_save)
    response = replace(client, [topic(2, "Transactions", "A transaction is atomic.")], OTHER_FINGERPRINT)
    assert response.status_code == 500
    monkeypatch.undo()
    assert ask(client, "What does normalization reduce?").json()["insufficientContext"] is False
    leftovers = [entry.name for entry in (settings.index_dir.resolve() / COURSE).iterdir() if entry.name.startswith(".tmp")]
    assert leftovers == []


def test_source_limits_skip_the_rest(tmp_path):
    from fastapi.testclient import TestClient

    from app.main import create_app
    from app.providers.fake import FakeEmbeddingProvider, FakeModelProvider
    from helpers import make_settings

    limited = TestClient(create_app(make_settings(tmp_path, AI_MAX_SOURCES="2"), FakeModelProvider(),
                                    FakeEmbeddingProvider()))
    body = replace(limited, [topic(i, f"Topic {i}", f"Text about subject {i}.") for i in range(1, 6)]).json()
    assert body["indexedSourceCount"] == 2
    assert body["skippedSourceCount"] == 3


def test_vectors_are_stored_outside_any_database_as_unit_vectors(client, settings):
    replace(client, [topic(1, "Database Normalization", NORMALIZATION)])
    course_dir = settings.index_dir.resolve() / COURSE
    version = (course_dir / "current").read_text()
    vectors = np.load(course_dir / version / "vectors.npy", allow_pickle=False)
    assert np.allclose(np.linalg.norm(vectors, axis=1), 1.0)


@pytest.mark.parametrize("bad", [
    {"sourceType": "TOPIC", "sourceId": 1, "topicId": 1, "title": "T"},
    {"sourceType": "STUDY_MATERIAL", "sourceId": 1, "topicId": 1, "title": "M", "text": "x"},
    {"sourceType": "TOPIC", "sourceId": 0, "topicId": 1, "title": "T", "text": "x"},
    {"sourceType": "TOPIC", "sourceId": 1, "topicId": 1, "title": "T", "text": "x", "objectKey": "lms/courses/1"},
])
def test_malformed_sources_are_refused(client, bad):
    response = client.post(f"/internal/indexes/{COURSE}/replace", headers=HEADERS,
                           json={"contentFingerprint": FINGERPRINT, "sources": [bad]})
    assert response.status_code == 400
