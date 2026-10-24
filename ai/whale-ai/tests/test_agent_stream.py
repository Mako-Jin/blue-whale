"""流式问答接口单测：事件序列、工具轨迹与引用一致性。"""

from __future__ import annotations

import json
from typing import Any

import pytest
from fastapi.testclient import TestClient

import whale_ai.graph.nodes as nodes
from whale_ai.main import app
from whale_ai.tools import ToolError

client = TestClient(app)

HIT = {
    "path": "docs/service-catalog.md",
    "startLine": 3,
    "endLine": 6,
    "kind": "doc",
    "title": "服务目录",
    "snippet": "whale-agent 8090",
    "score": 1.0,
}


def _events(body: str) -> list[tuple[str, dict[str, Any]]]:
    """解析 SSE 帧为 (event, payload) 列表。"""
    result: list[tuple[str, dict[str, Any]]] = []
    name: str | None = None
    for line in body.splitlines():
        if line.startswith("event: "):
            name = line[len("event: ") :]
        elif line.startswith("data: ") and name:
            result.append((name, json.loads(line[len("data: ") :])))
    return result


def test_stream_emits_meta_tool_delta_done(monkeypatch: pytest.MonkeyPatch) -> None:
    def fake_tool(name: str, arguments: dict[str, Any], context: Any, client: Any = None) -> Any:
        del arguments, context, client
        if name == "knowledge_search":
            return {"count": 1, "items": [HIT]}
        return {"count": 0, "items": []}

    monkeypatch.setattr(nodes, "execute_tool", fake_tool)

    response = client.post(
        "/v1/agent/stream",
        json={"conversationId": 11, "question": "服务目录在哪？"},
        headers={"X-User-Id": "7", "X-Permission-Codes": "sys:agent:chat"},
    )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    events = _events(response.text)
    names = [event for event, _ in events]
    assert names[0] == "meta"
    assert "tool" in names
    assert "delta" in names
    assert names[-1] == "done"
    done = events[-1][1]
    assert done["conversationId"] == "11"
    assert done["degraded"] is True
    assert [item["sourceId"] for item in done["citations"]] == ["S1"]


def test_stream_falls_back_when_tools_unavailable(monkeypatch: pytest.MonkeyPatch) -> None:
    def failing(*_: Any, **__: Any) -> Any:
        raise ToolError("工具不可用")

    monkeypatch.setattr(nodes, "execute_tool", failing)

    response = client.post("/v1/agent/stream", json={"question": "平台架构如何？"})

    events = _events(response.text)
    assert [event for event, _ in events][-1] == "done"
    delta = next(payload["text"] for event, payload in events if event == "delta")
    assert "知识库中未找到" in delta
    assert events[-1][1]["citations"] == []


def test_stream_rejects_blank_question() -> None:
    response = client.post("/v1/agent/stream", json={"question": ""})

    assert response.status_code == 422
