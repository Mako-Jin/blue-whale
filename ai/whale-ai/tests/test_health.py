"""健康检查与骨架图单测。"""

from fastapi.testclient import TestClient

from whale_ai.graph import PLACEHOLDER_ANSWER, run_agent
from whale_ai.main import app

client = TestClient(app)


def test_health_returns_ok() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["service"] == "whale-ai"
    assert body["engine"] == "langgraph"
    assert body["graph"] == "skeleton"


def test_run_agent_returns_placeholder_answer() -> None:
    state = run_agent("平台架构如何？", conversation_id=7)

    assert state["answer"] == PLACEHOLDER_ANSWER
    assert state["intent"] == "SKELETON"
    assert state["conversation_id"] == 7
    assert state["degraded"] is False
