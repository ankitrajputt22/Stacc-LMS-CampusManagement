"""v0.14.1: questions drafted in the bank's own types, cited, with keyed questions checked by answering them again."""

from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient
from test_learning import Scripted, course_sources

from app.main import create_app
from app.providers.fake import FakeEmbeddingProvider
from helpers import COURSE, FINGERPRINT, HEADERS, replace

ALL_TYPES = ["SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER", "DESCRIPTIVE"]


def draft(client, **body):
    request = {"indexFingerprint": FINGERPRINT, "questionCount": 5, "questionTypes": ALL_TYPES, "difficulty": "MEDIUM"}
    request.update(body)
    return client.post(f"/internal/v1/indexes/{COURSE}/question-bank-draft", headers=HEADERS, json=request)


@pytest.fixture
def indexed(client):
    assert replace(client, course_sources()).status_code == 200
    return client


def test_every_type_comes_back_cited_with_its_own_answer_shape(indexed):
    body = draft(indexed).json()
    assert body["insufficientContext"] is False
    assert body["droppedForKeyMismatch"] == 0
    by_type = {question["questionType"]: question for question in body["questions"]}
    assert set(by_type) == set(ALL_TYPES)
    assert all(question["citations"] for question in body["questions"])
    assert sum(option["correct"] for option in by_type["SINGLE_CHOICE"]["options"]) == 1
    assert by_type["TRUE_FALSE"]["trueFalseAnswer"] is True and by_type["TRUE_FALSE"]["options"] == []
    assert by_type["SHORT_ANSWER"]["acceptedAnswers"]
    assert by_type["DESCRIPTIVE"]["answerGuidance"] and by_type["DESCRIPTIVE"]["trueFalseAnswer"] is None


def test_only_the_requested_types_are_kept(indexed):
    body = draft(indexed, questionTypes=["TRUE_FALSE"], questionCount=3).json()
    assert [question["questionType"] for question in body["questions"]] == ["TRUE_FALSE"] * 3


def scripted(settings, *replies):
    model = Scripted(*replies)
    client = TestClient(create_app(settings, model, FakeEmbeddingProvider()))
    assert replace(client, course_sources()).status_code == 200
    return client, model


def test_a_question_whose_key_the_model_does_not_reproduce_is_dropped(settings):
    question = {"questionType": "SINGLE_CHOICE", "text": "Which is stricter than 3NF?", "suggestedMarks": 1,
                "options": [{"text": "BCNF", "correct": False}, {"text": "1NF", "correct": True}], "sourceIds": ["S1"]}
    statement = {"questionType": "TRUE_FALSE", "text": "Commit makes changes durable.", "trueFalseAnswer": True,
                 "suggestedMarks": 1, "sourceIds": ["S1"]}
    client, model = scripted(settings, json.dumps({"questions": [question, statement]}),
                             json.dumps({"chosenOptions": [1]}), json.dumps({"trueFalseAnswer": True}))
    body = draft(client, questionCount=2, questionTypes=["SINGLE_CHOICE", "TRUE_FALSE"]).json()
    assert body["droppedForKeyMismatch"] == 1
    assert [question["questionType"] for question in body["questions"]] == ["TRUE_FALSE"]
    check = model.requests[1]
    # The check never sees which option is marked correct.
    assert "correct" not in check.user_prompt and "1. BCNF" in check.user_prompt


def test_an_unusable_check_counts_as_a_mismatch(settings):
    question = {"questionType": "TRUE_FALSE", "text": "Commit makes changes durable.", "trueFalseAnswer": True,
                "suggestedMarks": 1, "sourceIds": ["S1"]}
    client, _ = scripted(settings, json.dumps({"questions": [question]}), "nonsense", "still nonsense")
    body = draft(client, questionCount=1, questionTypes=["TRUE_FALSE"]).json()
    assert body == {"questions": [], "droppedForKeyMismatch": 1, "droppedAsRepeats": 0, "insufficientContext": False}


@pytest.mark.parametrize("change", [{"questionCount": 0}, {"questionCount": 11}, {"questionTypes": []},
                                    {"questionTypes": ["ESSAY"]}, {"additionalInstruction": "x" * 501}])
def test_requests_are_validated(indexed, change):
    assert draft(indexed, **change).status_code == 400


# --- Quality checks (v0.14.2) ---------------------------------------------------------------------------


def statement(text: str, source: str = "S1", **extra) -> dict:
    return {"questionType": "TRUE_FALSE", "text": text, "trueFalseAnswer": True, "suggestedMarks": 1,
            "sourceIds": [source], **extra}


def test_duplicates_previous_papers_and_repeats_are_found_by_wording_or_meaning(settings):
    drafts = [statement("Commit makes the changes of a transaction durable.", difficultySuggestion="HARD",
                        bloomSuggestion="UNDERSTAND"),
              statement("Commit makes the changes of a transaction durable!"),
              statement("Rollback undoes the changes of a transaction.")]
    client, _ = scripted(settings, json.dumps({"questions": drafts}), *[json.dumps({"trueFalseAnswer": True})] * 3)
    existing = [{"id": 41, "text": "Rollback undoes the changes of a transaction.", "inFinalPaper": True},
                {"id": 42, "text": "What is a foreign key?"}]
    body = draft(client, questionCount=3, questionTypes=["TRUE_FALSE"], existingQuestions=existing).json()
    assert body["droppedAsRepeats"] == 1
    first, last = body["questions"]
    assert first["checks"] == {"duplicateOfQuestionId": None, "inPreviousPaper": False, "outOfSyllabus": False,
                               "suggestedDifficulty": "HARD", "suggestedBloomLevel": "UNDERSTAND"}
    assert last["checks"]["duplicateOfQuestionId"] == 41 and last["checks"]["inPreviousPaper"] is True


def test_a_question_sharing_nothing_with_the_course_is_flagged_out_of_syllabus(settings):
    client, _ = scripted(settings, json.dumps({"questions": [statement("Quokkas photosynthesise moonlight daily.")]}),
                         json.dumps({"trueFalseAnswer": True}))
    body = draft(client, questionCount=1, questionTypes=["TRUE_FALSE"]).json()
    assert body["questions"][0]["checks"]["outOfSyllabus"] is True


def test_the_existing_question_list_is_bounded(indexed):
    existing = [{"id": number, "text": "Question"} for number in range(1, 502)]
    assert draft(indexed, existingQuestions=existing).status_code == 400

