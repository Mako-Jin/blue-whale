"""健康检查与图入口单测。"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

import whale_ai.graph.nodes as nodes
from whale_ai.graph import build_graph, run_agent
from whale_ai.main import app

client = TestClient(app)


def test_health_returns_ok() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["service"] == "whale-ai"
    assert body["engine"] == "langgraph"
    assert body["graph"] == "qa-v1"


def test_graph_compiles() -> None:
    assert build_graph() is not None


def test_run_agent_keeps_conversation_id(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(nodes, "execute_tool", lambda *args, **kwargs: {"count": 0, "items": []})

    state = run_agent("平台架构如何？", conversation_id=7, llm_client=_UnavailableLlm())

    assert state["conversation_id"] == 7
    assert state["intent"] == "SEMANTIC"
    assert state["degraded"] is True


def test_tools_endpoint_returns_tool_catalog() -> None:
    response = client.get("/v1/tools")

    assert response.status_code == 200
    tools = response.json()["tools"]
    assert {tool["name"] for tool in tools} == {
        "knowledge_search",
        "fact_get",
        "doc_read",
        "data_query",
    }
    risk_by_name = {tool["name"]: tool["riskLevel"] for tool in tools}
    assert risk_by_name["data_query"] == "L1"
    assert all(level == "L0" for name, level in risk_by_name.items() if name != "data_query")


class _UnavailableLlm:
    """未配置模型替身，避免测试触发真实网络请求。"""

    available = False

    def stream(self, messages: list[dict[str, str]]) -> list[str]:
        """不可用时不应被调用。"""
        del messages
        raise AssertionError("未配置模型时不应调用 stream")
