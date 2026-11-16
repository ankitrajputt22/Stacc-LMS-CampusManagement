"""v0.15.2: summaries of a topic, of whatever the allowed sources hold (a module or a document), in a few styles."""

from __future__ import annotations

import pytest
from test_learning import course_sources, post

from helpers import replace


@pytest.fixture
def indexed(client):
    assert replace(client, course_sources()).status_code == 200
    return client


def test_without_a_topic_the_allowed_sources_are_summarised(indexed):
    body = post(indexed, "summary", {"allowedSources": [{"sourceType": "STUDY_MATERIAL", "sourceId": 10}]}).json()
    assert body["insufficientContext"] is False
    assert {(c["sourceType"], c["sourceId"]) for c in body["citations"]} == {("STUDY_MATERIAL", 10)}


@pytest.mark.parametrize("style,check", [("REVISION_NOTES", lambda text: text.startswith("Remember: ")),
                                         ("BRIEF", lambda text: not text.startswith("Remember"))])
def test_styles_shape_the_summary(indexed, style, check):
    body = post(indexed, "summary", {"topicId": 1, "style": style}).json()
    assert check(body["summary"])


def test_an_unknown_style_is_refused(indexed):
    assert post(indexed, "summary", {"topicId": 1, "style": "POEM"}).status_code == 400
