"""The request log line: metadata only, with the backend's request ID and the course key in their places."""

import logging

from helpers import COURSE, FINGERPRINT, HEADERS, replace, topic


def test_the_log_names_the_request_task_and_course_but_nothing_else(client, caplog):
    replace(client, [topic(1, "Linked lists", "A node holds a value and a reference to the next node.")])
    caplog.clear()

    with caplog.at_level(logging.INFO, logger="stacc.ai"):
        client.post(f"/internal/v1/indexes/{COURSE}/ask", headers={**HEADERS, "X-Request-ID": "a1b2c3d4e5f60718"},
                    json={"indexFingerprint": FINGERPRINT, "question": "What does a node hold?"})

    lines = [record.getMessage() for record in caplog.records if record.name == "stacc.ai"]
    assert any(line.startswith(f"request=a1b2c3d4e5f60718 task=ask course={COURSE} status=") for line in lines)
    assert not any("What does a node hold" in line for line in lines)


def test_a_strange_request_id_is_not_logged(client, caplog):
    with caplog.at_level(logging.INFO, logger="stacc.ai"):
        client.post(f"/internal/v1/indexes/{COURSE}/ask", headers={**HEADERS, "X-Request-ID": "bad id\nwith=newline"},
                    json={"indexFingerprint": FINGERPRINT, "question": "Anything?"})

    lines = [record.getMessage() for record in caplog.records if record.name == "stacc.ai"]
    assert lines
    assert all(line.startswith("request=- ") for line in lines)
