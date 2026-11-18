from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient
from test_learning import Scripted

from app.evaluation import dataset
from app.evaluation.run import evaluate
from app.main import create_app
from app.providers.fake import FakeEmbeddingProvider, FakeModelProvider
from app.retrieval import keyword
from helpers import COURSE, FINGERPRINT, HEADERS, OTHER_FINGERPRINT, make_settings, replace, topic

SECRET = "SECRETPHRASE quokka"


def report(tmp_path, **env):
    return evaluate(make_settings(tmp_path, **env), FakeModelProvider(), FakeEmbeddingProvider())


def test_every_question_has_its_expected_sources_and_nothing_false_is_found(tmp_path):
    result = report(tmp_path)
    assert len(result.answerable) >= 15
    assert result.hit_rate == 1.0
    assert result.first_hit_rate >= 0.9
    assert result.mean_reciprocal_rank >= 0.9
    assert result.false_finds == 0


def test_keyword_matching_finds_what_meaning_alone_misses(tmp_path):
    hybrid = report(tmp_path / "hybrid")
    semantic = report(tmp_path / "semantic", AI_KEYWORD_SEARCH="false")
    assert hybrid.hit_rate > semantic.hit_rate


def test_sql_words_in_capitals_count_even_when_they_are_common_english():
    assert "having" in keyword.query_terms("When do I use HAVING instead of WHERE?")
    assert "having" not in keyword.query_terms("What are we having for lunch?")


def test_the_question_set_is_well_formed():
    assert all(question.expected <= set(dataset.TOPICS) for question in dataset.QUESTIONS)
    assert any(not question.expected for question in dataset.QUESTIONS)



class Recording(FakeModelProvider):

    def __init__(self) -> None:
        self.requests = []

    def generate_json(self, request):
        self.requests.append(request)
        return super().generate_json(request)


def leak_client(settings, *replies):
    model = Scripted(*replies) if replies else Recording()
    client = TestClient(create_app(settings, model, FakeEmbeddingProvider()))
    sources = [topic(1, "Transactions", "Commit makes changes durable and rollback undoes them."),
               topic(2, "Hidden topic", f"Normal forms reduce redundancy. {SECRET}")]
    assert replace(client, sources).status_code == 200
    return client, model


def post(client, task, body):
    return client.post(f"/internal/v1/indexes/{COURSE}/{task}", headers=HEADERS,
                       json={"indexFingerprint": FINGERPRINT, **body})


ONLY_TOPIC_1 = [{"sourceType": "TOPIC", "sourceId": 1}]
ANSWER = json.dumps({"insufficientContext": False, "answer": "It is durable.", "sourceIds": ["S1"]})


@pytest.mark.parametrize("task,body", [
    ("ask", {"question": "What do normal forms reduce? quokka"}),
    ("summary", {"topicId": 2}),
    ("practice", {"questionCount": 2, "topicId": 2}),
    ("assessment-draft", {"questionCount": 2, "difficulty": "EASY", "questionType": "MIXED"}),
])
def test_a_source_left_out_of_the_allowed_list_never_reaches_the_model(settings, task, body):
    client, model = leak_client(settings)
    response = post(client, task, {**body, "allowedSources": ONLY_TOPIC_1})
    assert response.status_code == 200
    assert SECRET not in json.dumps(response.json())
    assert all(SECRET not in request.user_prompt for request in model.requests)


def test_an_index_not_named_in_the_request_is_never_searched(settings):
    client, model = leak_client(settings, ANSWER, ANSWER)
    other = "abcdefabcdefabcdefabcdefabcdef12"
    note = {"sourceType": "NOTE", "sourceId": 9, "title": "Someone's note", "text": f"Durable commits. {SECRET}"}
    assert replace(client, [note], fingerprint=OTHER_FINGERPRINT, course=other).status_code == 200
    post(client, "ask", {"question": "Why are commits durable? quokka", "allowedSources": ONLY_TOPIC_1})
    assert all(SECRET not in request.user_prompt for request in model.requests)
    extra = [{"indexKey": other, "indexFingerprint": OTHER_FINGERPRINT,
              "allowedSources": [{"sourceType": "NOTE", "sourceId": 10}]}]
    post(client, "ask", {"question": "Why are commits durable? quokka", "allowedSources": ONLY_TOPIC_1,
                         "additionalIndexes": extra})
    assert all(SECRET not in request.user_prompt for request in model.requests)
