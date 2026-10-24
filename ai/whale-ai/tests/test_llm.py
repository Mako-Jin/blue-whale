"""模型客户端单测：SSE 行解析与流式增量。"""

from __future__ import annotations

import httpx

from whale_ai.llm.client import LlmClient, LlmError, parse_line
from whale_ai.llm.prompts import build_messages


def test_parse_line_extracts_content() -> None:
    assert parse_line('data: {"choices":[{"delta":{"content":"你好"}}]}') == "你好"
    assert parse_line('data: {"choices":[{"delta":{}}]}') is None
    assert parse_line("data: [DONE]") is None
    assert parse_line(": keep-alive") is None
    assert parse_line("") is None
    assert parse_line("data: not-json") is None


def test_stream_yields_deltas_until_done() -> None:
    body = (
        'data: {"choices":[{"delta":{"content":"网关"}}]}\n\n'
        'data: {"choices":[{"delta":{"content":" 8080"}}]}\n\n'
        "data: [DONE]\n\n"
    )
    transport = httpx.MockTransport(lambda request: httpx.Response(200, text=body))
    client = LlmClient(
        "http://llm/v1", "key", "model", timeout_seconds=2, transport=transport
    )

    assert list(client.stream([{"role": "user", "content": "q"}])) == ["网关", " 8080"]


def test_stream_raises_on_http_error() -> None:
    transport = httpx.MockTransport(lambda request: httpx.Response(503, text="busy"))
    client = LlmClient(
        "http://llm/v1", "key", "model", timeout_seconds=2, transport=transport
    )

    try:
        list(client.stream([{"role": "user", "content": "q"}]))
    except LlmError as exc:
        assert "503" in str(exc)
    else:  # pragma: no cover - 防御性断言
        raise AssertionError("应抛出 LlmError")


def test_available_requires_key_and_enabled() -> None:
    assert LlmClient("http://llm", "k", "m", 1).available is True
    assert LlmClient("http://llm", "", "m", 1).available is False
    assert LlmClient("http://llm", "k", "m", 1, enabled=False).available is False


def test_build_messages_injects_history_and_summary() -> None:
    citations = [
        {"sourceId": "S1", "path": "docs/a.md", "startLine": 1, "endLine": 2, "snippet": "x"}
    ]

    messages = build_messages(
        "现在的问题",
        citations,
        [{"question": "之前的问题", "answer": "之前的回答"}],
        "更早的摘要",
    )

    assert [item["role"] for item in messages] == ["system", "system", "user", "assistant", "user"]
    assert "更早的摘要" in messages[1]["content"]
    assert messages[2]["content"] == "之前的问题"
    assert messages[3]["content"] == "之前的回答"
    assert "现在的问题" in messages[-1]["content"]
    assert "[S1]" in messages[-1]["content"]
