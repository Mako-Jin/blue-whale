"""编排节点与图单测：意图、取证、降级摘要与引用收敛。"""

from __future__ import annotations

from typing import Any

import pytest

import whale_ai.graph.nodes as nodes
from whale_ai.graph import run_agent
from whale_ai.memory import InMemoryStore
from whale_ai.tools import ToolError

HITS: list[dict[str, Any]] = [
    {
        "path": "docs/service-catalog.md",
        "startLine": 10,
        "endLine": 20,
        "kind": "doc",
        "title": "服务目录",
        "snippet": "whale-im 8091",
        "score": 1.5,
    },
    {
        "path": "docs/architecture.md",
        "startLine": 1,
        "endLine": 8,
        "kind": "doc",
        "title": "架构",
        "snippet": "网关 8080",
        "score": 1.2,
    },
]


class FakeLlm:
    """可控模型替身。"""

    def __init__(self, chunks: list[str] | None = None, available: bool = True) -> None:
        self.chunks = chunks or []
        self._available = available

    @property
    def available(self) -> bool:
        return self._available

    def stream(self, messages: list[dict[str, str]]) -> Any:
        """按块返回文本。"""
        del messages
        yield from self.chunks


def _fake_tool(name: str, arguments: dict[str, Any], context: Any, client: Any = None) -> Any:
    del arguments, context, client
    if name == "knowledge_search":
        return {"count": len(HITS), "items": HITS}
    return {"count": 0, "items": []}


def test_run_agent_uses_tools_and_degrades_without_llm(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(nodes, "execute_tool", _fake_tool)
    events: list[tuple[str, dict[str, Any]]] = []

    state = run_agent(
        "平台架构如何？",
        conversation_id=7,
        user_id="9",
        permission_codes="sys:agent:chat",
        emit=lambda event, payload: events.append((event, payload)),
        llm_client=FakeLlm(available=False),
    )

    assert state["intent"] == "SEMANTIC"
    assert state["degraded"] is True
    assert [item["sourceId"] for item in state["citations"]] == ["S1", "S2"]
    assert "[S1]" in state["answer"]
    names = [event for event, _ in events]
    assert names[0] == "meta"
    assert names.count("tool") == 1
    assert names[-1] == "delta"


def test_run_agent_streams_model_deltas_and_aligns_citations(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(nodes, "execute_tool", _fake_tool)
    deltas: list[str] = []

    state = run_agent(
        "平台架构如何？",
        emit=lambda event, payload: deltas.append(payload["text"]) if event == "delta" else None,
        llm_client=FakeLlm(["网关路由见 ", "[S1]", "。"]),
    )

    assert state["degraded"] is False
    assert state["answer"] == "网关路由见 [S1]。"
    assert deltas == ["网关路由见 ", "[S1]", "。"]
    assert [item["sourceId"] for item in state["citations"]] == ["S1"]


def test_run_agent_survives_tool_failure(monkeypatch: pytest.MonkeyPatch) -> None:
    def failing(*_: Any, **__: Any) -> Any:
        raise ToolError("工具不可用")

    monkeypatch.setattr(nodes, "execute_tool", failing)

    state = run_agent("平台架构如何？", llm_client=FakeLlm(available=False))

    assert state["citations"] == []
    assert "知识库中未找到" in state["answer"]


def test_run_agent_recalls_history_and_writes_memory(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(nodes, "execute_tool", _fake_tool)
    store = InMemoryStore()
    llm = RecordingLlm(["[S1]"])

    first = run_agent("端口是多少？", conversation_id=21, llm_client=llm, memory_store=store)
    assert first["history"] == []

    second = run_agent("那网关呢？", conversation_id=21, llm_client=llm, memory_store=store)
    assert [turn["question"] for turn in second["history"]] == ["端口是多少？"]
    assert "端口是多少？" in [message["content"] for message in llm.messages[1]]

    record = store.load(21)
    assert [turn["question"] for turn in record["turns"]] == ["端口是多少？", "那网关呢？"]


class RecordingLlm(FakeLlm):
    """记录每次收到的消息，用于断言历史注入。"""

    def __init__(self, chunks: list[str]) -> None:
        super().__init__(chunks)
        self.messages: list[list[dict[str, str]]] = []

    def stream(self, messages: list[dict[str, str]]) -> Any:
        """记录消息后按块返回。"""
        self.messages.append(list(messages))
        yield from self.chunks
