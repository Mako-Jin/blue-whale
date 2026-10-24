"""流式问答占位接口单测。"""

from fastapi.testclient import TestClient

from whale_ai.graph import PLACEHOLDER_ANSWER
from whale_ai.main import app

client = TestClient(app)


def test_stream_emits_meta_delta_done() -> None:
    response = client.post(
        "/v1/agent/stream",
        json={"conversationId": 11, "question": "平台架构如何？"},
    )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    body = response.text
    assert "event: meta" in body
    assert '"intent":"SKELETON"' in body
    assert "event: delta" in body
    assert PLACEHOLDER_ANSWER in body
    assert "event: done" in body


def test_stream_rejects_blank_question() -> None:
    response = client.post("/v1/agent/stream", json={"question": ""})

    assert response.status_code == 422
