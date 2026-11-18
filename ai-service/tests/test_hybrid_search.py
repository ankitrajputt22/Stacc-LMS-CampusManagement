from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.config import SettingsError
from app.main import create_app
from app.providers.fake import FakeEmbeddingProvider, FakeModelProvider
from app.retrieval import keyword
from helpers import COURSE, FINGERPRINT, HEADERS, make_settings, replace, topic

SOURCES = [
    topic(1, "Normal forms", "A table is in 3NF when no non-key column depends on another non-key column. "
                             "Long explanations of dependencies, keys, anomalies, and design follow here."),
    topic(2, "Aggregation", "Use GROUP BY with COUNT to total rows per department in a report."),
    topic(3, "Transactions", "Commit makes changes durable and rollback undoes them."),
]


def ask(client: TestClient, question: str) -> dict:
    return client.post(f"/internal/v1/indexes/{COURSE}/ask", headers=HEADERS,
                       json={"indexFingerprint": FINGERPRINT, "question": question}).json()


def client_with(tmp_path, **env) -> TestClient:
    client = TestClient(create_app(make_settings(tmp_path, **env), FakeModelProvider(), FakeEmbeddingProvider()))
    assert replace(client, SOURCES).status_code == 200
    return client


def test_terms_keep_course_terms_and_phrases():
    assert keyword.terms("Use GROUP BY in C++ and C#; 3NF, O(n).") == [
        "use", "group", "by", "in", "c++", "and", "c#", "3nf", "o", "n",
        "use group", "group by", "by in", "in c++", "c++ and", "and c#", "c# 3nf", "3nf o", "o n"]
    assert keyword.query_terms("What is the meaning of 3NF?") == ["3nf", "of 3nf"]
    assert keyword.query_terms("What is it about?") == []


def test_bm25_prefers_rare_exact_terms_and_phrases():
    documents = ["group the rows by hand", "use group by to total rows", "rows and more rows"]
    scores = keyword.bm25(documents, "How does GROUP BY work?")
    assert scores[1] > scores[0] > 0
    assert scores[2] == 0
    assert keyword.bm25(documents, "the of and") == [0.0, 0.0, 0.0]


def test_fusion_rewards_items_both_rankings_find():
    assert keyword.fuse([1, 2, 3], [3, 4]) == [3, 1, 2, 4]
    assert keyword.fuse([], []) == []


def test_keyword_match_finds_what_semantic_search_alone_misses(tmp_path):
    strict = {"AI_MIN_RELEVANCE": "0.99"}
    found = ask(client_with(tmp_path / "hybrid", **strict), "3NF?")
    assert found["insufficientContext"] is False
    assert [c["sourceId"] for c in found["citations"]] == [1]

    semantic_only = ask(client_with(tmp_path / "semantic", AI_KEYWORD_SEARCH="false", **strict), "3NF?")
    assert semantic_only == {"answer": None, "insufficientContext": True, "citations": []}


def test_unrelated_question_still_finds_nothing(tmp_path):
    body = ask(client_with(tmp_path), "Who won the football world cup in 1998?")
    assert body == {"answer": None, "insufficientContext": True, "citations": []}


def test_keyword_search_setting_is_checked(tmp_path):
    with pytest.raises(SettingsError):
        make_settings(tmp_path, AI_KEYWORD_SEARCH="maybe")
