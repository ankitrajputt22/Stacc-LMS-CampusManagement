from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient
from test_learning import Scripted, course_sources

from app.main import create_app
from app.providers.fake import FakeEmbeddingProvider
from helpers import COURSE, FINGERPRINT, HEADERS, replace


@pytest.fixture
def indexed(client):
    assert replace(client, course_sources()).status_code == 200
    return client


def post(client, task, body):
    return client.post(f"/internal/v1/indexes/{COURSE}/{task}", headers=HEADERS, json={"indexFingerprint": FINGERPRINT, **body})


def test_a_model_answer_cites_its_sources(indexed):
    body = post(indexed, "model-answer", {"questionText": "Explain what third normal form removes.",
                                          "questionType": "DESCRIPTIVE", "marks": 5}).json()
    assert body["insufficientContext"] is False and body["answer"] and body["keyPoints"]
    assert body["citations"]


def test_an_unrelated_question_has_no_model_answer(indexed):
    body = post(indexed, "model-answer", {"questionText": "Who won the football world cup in 1998?",
                                          "questionType": "DESCRIPTIVE", "marks": 5}).json()
    assert body == {"answer": None, "keyPoints": [], "citations": [], "insufficientContext": True}


def test_rubric_points_add_up_to_the_maximum_and_levels_stay_within(indexed):
    body = post(indexed, "rubric", {"title": "Normalise a schema", "instructions": "Take the schema to 3NF.",
                                    "maxMarks": 20, "criteriaCount": 3}).json()
    criteria = body["criteria"]
    assert len(criteria) == 3
    assert round(sum(criterion["maxPoints"] for criterion in criteria), 2) == 20
    assert all(level["points"] <= criterion["maxPoints"] for criterion in criteria for level in criterion["levels"])


def test_rubric_points_are_scaled_when_the_model_adds_them_up_wrong(settings):
    reply = {"criteria": [{"title": "A", "maxPoints": 5, "levels": [{"title": "Top", "points": 5}]},
                          {"title": "B", "maxPoints": 15, "levels": [{"title": "Top", "points": 30}]}], "sourceIds": []}
    client = TestClient(create_app(settings, Scripted(json.dumps(reply)), FakeEmbeddingProvider()))
    assert replace(client, course_sources()).status_code == 200
    body = post(client, "rubric", {"title": "Essay", "maxMarks": 10, "criteriaCount": 2}).json()
    assert [criterion["maxPoints"] for criterion in body["criteria"]] == [2.5, 7.5]
    assert body["criteria"][1]["levels"][0]["points"] == 7.5


@pytest.mark.parametrize("body", [{"title": "", "maxMarks": 10, "criteriaCount": 3},
                                  {"title": "X", "maxMarks": 0, "criteriaCount": 3},
                                  {"title": "X", "maxMarks": 10, "criteriaCount": 9}])
def test_rubric_requests_are_validated(indexed, body):
    assert post(indexed, "rubric", body).status_code == 400
