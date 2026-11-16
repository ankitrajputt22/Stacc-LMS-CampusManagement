"""v0.15.3: flashcards from the allowed sources, each cited, no repeated fronts."""

from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient
from test_learning import Scripted, course_sources, post

from app.main import create_app
from app.providers.fake import FakeEmbeddingProvider
from helpers import replace


@pytest.fixture
def indexed(client):
    assert replace(client, course_sources()).status_code == 200
    return client


def test_cards_are_cited_and_come_from_the_chosen_topic(indexed):
    body = post(indexed, "flashcards", {"cardCount": 4, "topicId": 2}).json()
    assert body["insufficientContext"] is False and len(body["cards"]) >= 1
    assert all(card["citations"] and {c["topicId"] for c in card["citations"]} == {2} for card in body["cards"])


def test_uncited_and_repeated_cards_are_dropped(settings):
    reply = {"cards": [{"front": "Commit?", "back": "Makes changes durable.", "sourceIds": ["S1"]},
                       {"front": " commit? ", "back": "Again.", "sourceIds": ["S1"]},
                       {"front": "Rollback?", "back": "Undoes changes.", "sourceIds": []}]}
    client = TestClient(create_app(settings, Scripted(json.dumps(reply)), FakeEmbeddingProvider()))
    replace(client, course_sources())
    body = post(client, "flashcards", {"cardCount": 3}).json()
    assert [card["front"] for card in body["cards"]] == ["Commit?"]


@pytest.mark.parametrize("count", [2, 31])
def test_card_counts_are_bounded(indexed, count):
    assert post(indexed, "flashcards", {"cardCount": count}).status_code == 400
