from __future__ import annotations

import pytest
from test_learning import NORMALIZATION, TRANSACTIONS, course_sources, post

from helpers import FINGERPRINT, HEADERS, OTHER_FINGERPRINT, replace, topic

TEACHER = "fedcba9876543210fedcba9876543210"


@pytest.fixture
def indexed(client):
    assert replace(client, course_sources()).status_code == 200
    return client


def test_a_source_left_out_of_the_allowed_list_is_never_cited(indexed):
    allowed = [{"sourceType": "TOPIC", "sourceId": 2}]
    body = post(indexed, "ask", {"question": "What does third normal form remove?", "allowedSources": allowed}).json()
    assert all(c["sourceId"] == 2 for c in body["citations"])
    summary = post(indexed, "summary", {"topicId": 1, "allowedSources": allowed}).json()
    assert summary["insufficientContext"] is True


def test_an_empty_allowed_list_allows_nothing(indexed):
    body = post(indexed, "ask", {"question": "What is a transaction?", "allowedSources": []}).json()
    assert body == {"answer": None, "insufficientContext": True, "citations": []}


def test_an_additional_index_is_searched_and_checked(client):
    assert replace(client, [topic(1, "Transactions", TRANSACTIONS)]).status_code == 200
    teacher = replace(client, [topic(7, "Exam focus", NORMALIZATION)], fingerprint=OTHER_FINGERPRINT, course=TEACHER)
    assert teacher.status_code == 200
    extra = [{"indexKey": TEACHER, "indexFingerprint": OTHER_FINGERPRINT}]
    body = post(client, "ask", {"question": "What does third normal form remove?", "additionalIndexes": extra}).json()
    assert any(c["sourceId"] == 7 for c in body["citations"])
    stale = [{"indexKey": TEACHER, "indexFingerprint": FINGERPRINT}]
    assert post(client, "ask", {"question": "What does third normal form remove?", "additionalIndexes": stale}).status_code == 409


def test_bad_index_keys_and_unknown_fields_are_refused(indexed):
    extra = [{"indexKey": "../etc", "indexFingerprint": FINGERPRINT}]
    assert post(indexed, "ask", {"question": "What is a transaction?", "additionalIndexes": extra}).status_code == 400
    response = indexed.post("/internal/v1/indexes/0123456789abcdef0123456789abcdef/ask", headers=HEADERS,
                            json={"indexFingerprint": FINGERPRINT, "question": "What is a transaction?", "accountId": 5})
    assert response.status_code == 400


def test_course_sources_still_answer_without_a_list(indexed):
    assert len(course_sources()) == 3
    assert post(indexed, "ask", {"question": "What does commit do in a transaction?"}).json()["citations"]


def teacher_document(source_id: int, title: str, text: str) -> dict:
    return {"sourceType": "TEACHER_DOCUMENT", "sourceId": source_id, "title": title, "text": text}


def test_teacher_documents_and_notes_are_text_sources_without_a_topic(client):
    assert replace(client, [topic(1, "Transactions", TRANSACTIONS)]).status_code == 200
    teacher = replace(client, [teacher_document(9, "Exam focus", NORMALIZATION)], fingerprint=OTHER_FINGERPRINT,
                      course=TEACHER)
    assert teacher.status_code == 200
    extra = [{"indexKey": TEACHER, "indexFingerprint": OTHER_FINGERPRINT,
              "allowedSources": [{"sourceType": "TEACHER_DOCUMENT", "sourceId": 9}]}]
    body = post(client, "ask", {"question": "What does third normal form remove?", "additionalIndexes": extra}).json()
    cited = next(c for c in body["citations"] if c["sourceType"] == "TEACHER_DOCUMENT")
    assert cited == {"sourceType": "TEACHER_DOCUMENT", "sourceId": 9, "topicId": None, "title": "Exam focus",
                     "fileName": None, "pageNumber": None, "slideNumber": None}
    alone = post(client, "ask", {"question": "What does third normal form remove?"}).json()
    assert all(c["sourceType"] != "TEACHER_DOCUMENT" for c in alone["citations"])


@pytest.mark.parametrize("source", [
    {"sourceType": "TEACHER_DOCUMENT", "sourceId": 9, "topicId": 1, "title": "Doc", "text": "Text"},
    {"sourceType": "NOTE", "sourceId": 9, "title": "Note"},
    {"sourceType": "TOPIC", "sourceId": 9, "title": "Topic", "text": "Text"},
])
def test_source_shapes_are_checked(client, source):
    assert replace(client, [source]).status_code == 400


def test_an_index_is_deleted_completely(client, settings):
    note = {"sourceType": "NOTE", "sourceId": 4, "title": "My note", "text": NORMALIZATION}
    assert replace(client, [note], fingerprint=OTHER_FINGERPRINT, course=TEACHER).status_code == 200
    assert (settings.index_dir / TEACHER).exists()
    assert client.delete(f"/internal/v1/indexes/{TEACHER}", headers=HEADERS).status_code == 204
    assert not (settings.index_dir / TEACHER).exists()
    extra = [{"indexKey": TEACHER, "indexFingerprint": OTHER_FINGERPRINT}]
    assert replace(client, [topic(1, "Transactions", TRANSACTIONS)]).status_code == 200
    assert post(client, "ask", {"question": "What is a transaction?", "additionalIndexes": extra}).status_code == 404
    assert client.delete(f"/internal/v1/indexes/{TEACHER}", headers=HEADERS).status_code == 204
    assert client.delete(f"/internal/v1/indexes/{TEACHER}").status_code == 401
    assert client.delete("/internal/v1/indexes/not-a-key", headers=HEADERS).status_code == 400
