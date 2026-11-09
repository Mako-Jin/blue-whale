"""Wave 6.01 多助手配置化装配单测：请求契约、提示词覆盖、模型参数与工具白名单。"""

from __future__ import annotations

from typing import Any

import pytest

import whale_ai.graph.nodes as nodes
from whale_ai.api.schemas import AssistantConfig, ChatRequest, ModelParams
from whale_ai.graph import run_agent
from whale_ai.llm.client import get_llm_client
from whale_ai.llm.prompts import CITATION_CONTRACT, SYSTEM_PROMPT, build_messages

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
]


class RecordingLlm:
    """记录收到的消息的可控模型替身。"""

    def __init__(self, chunks: list[str]) -> None:
        self.chunks = chunks
        self.messages: list[list[dict[str, str]]] = []

    @property
    def available(self) -> bool:
        return True

    def stream(self, messages: list[dict[str, str]]) -> Any:
        """记录消息后按块返回。"""
        self.messages.append(list(messages))
        yield from self.chunks


def test_chat_request_accepts_assistant_camel_case() -> None:
    """请求契约：camelCase 的 assistant 对象可解析，缺省为 None。"""
    request = ChatRequest.model_validate(
        {
            "conversationId": 9,
            "question": "MES 设备状态如何？",
            "assistant": {
                "assistantId": "mes-helper",
                "systemPrompt": "你是 MES 助手。",
                "modelParams": {"model": "gpt-4o-mini", "temperature": 0.3},
                "toolWhitelist": ["knowledge_search"],
            },
        }
    )
    assert request.assistant is not None
    assert request.assistant.assistant_id == "mes-helper"
    assert request.assistant.model_params is not None
    assert request.assistant.model_params.max_tokens is None
    assert ChatRequest.model_validate({"question": "hi"}).assistant is None


def test_chat_request_rejects_unknown_model_param() -> None:
    """安全口径：modelParams 禁止携带未知字段（如 base_url / api_key）。"""
    with pytest.raises(ValueError):
        ModelParams.model_validate({"model": "x", "apiKey": "leak"})


def test_build_messages_custom_prompt_keeps_citation_contract() -> None:
    """自定义 system prompt 生效，且引用契约作为独立 system 消息追加。"""
    messages = build_messages("q", [], system_prompt="你是 MES 助手。")
    assert messages[0] == {"role": "system", "content": "你是 MES 助手。"}
    assert messages[1] == {"role": "system", "content": CITATION_CONTRACT}

    default = build_messages("q", [])
    assert default[0] == {"role": "system", "content": SYSTEM_PROMPT}
    assert CITATION_CONTRACT not in default[0]["content"]


def test_get_llm_client_overrides_model_params_only(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """助手级参数只覆盖 model/temperature/max_tokens，密钥与地址永远取全局。"""
    import whale_ai.llm.client as client_module

    class FakeSettings:
        enabled = True
        base_url = "https://global.example/v1"
        api_key = "global-key"
        model = "global-model"
        timeout_millis = 60000
        max_tokens = 1024
        temperature = 0.2

    monkeypatch.setattr(client_module, "get_llm_settings", lambda: FakeSettings())

    client = get_llm_client({"model": "asst-model", "temperature": 0.7, "max_tokens": 256})
    assert client._model == "asst-model"
    assert client._temperature == 0.7
    assert client._max_tokens == 256
    # 不可覆盖项
    assert client._base_url == "https://global.example/v1"
    assert client._api_key == "global-key"

    fallback = get_llm_client(None)
    assert fallback._model == "global-model"


def test_run_agent_applies_assistant_config(monkeypatch: pytest.MonkeyPatch) -> None:
    """端到端：meta 带 assistantId、自定义 prompt 进入消息、白名单外工具被跳过。"""
    called: list[str] = []

    def fake_tool(name: str, arguments: dict[str, Any], context: Any, client: Any = None) -> Any:
        del arguments, context, client
        called.append(name)
        return {"count": len(HITS), "items": HITS}

    monkeypatch.setattr(nodes, "execute_tool", fake_tool)
    events: list[tuple[str, dict[str, Any]]] = []
    llm = RecordingLlm(["[S1]"])

    state = run_agent(
        "端口是多少？",
        conversation_id=5,
        emit=lambda event, payload: events.append((event, payload)),
        llm_client=llm,
        assistant={
            "assistant_id": "mes-helper",
            "system_prompt": "你是 MES 助手。",
            "tool_whitelist": ["knowledge_search"],
        },
    )

    meta = next(payload for event, payload in events if event == "meta")
    assert meta["assistantId"] == "mes-helper"
    # 端口问题本会触发 fact_get，但白名单只放行 knowledge_search
    assert called == ["knowledge_search"]
    assert state["degraded"] is False
    assert llm.messages[0][0] == {"role": "system", "content": "你是 MES 助手。"}
    assert llm.messages[0][1] == {"role": "system", "content": CITATION_CONTRACT}


def test_assistant_config_serializes_for_state() -> None:
    """routes 下发的 dict 与 run_agent 的 assistant 参数结构对齐（snake_case）。"""
    config = AssistantConfig(
        assistantId="mes-helper", systemPrompt="p", toolWhitelist=["knowledge_search"]
    )
    payload = config.model_dump(exclude_none=True)
    assert set(payload) == {"assistant_id", "system_prompt", "tool_whitelist"}
