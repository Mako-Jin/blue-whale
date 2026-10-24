"""LangGraph 节点：意图 → 检索取证 → 回答 → 引用校验。

节点只读写 :class:`AgentState`；网络与模型调用均可通过状态注入替身（测试用）。
"""

from __future__ import annotations

import json
import logging
import time
from collections.abc import Callable
from typing import Any

from whale_ai.config import get_settings
from whale_ai.graph.citations import align_citations, build_summary, validate_citations
from whale_ai.graph.state import AgentState
from whale_ai.llm import LlmError, build_messages, get_llm_client
from whale_ai.memory import append_turn
from whale_ai.tools import ToolContext, ToolError, execute_tool

logger = logging.getLogger(__name__)

Emit = Callable[[str, dict[str, Any]], None]

STRUCTURED_WORDS = (
    "端口", "port", "版本", "version", "依赖", "路由", "网关", "服务清单",
    "表结构", "表字段", "字段", "ddl", "主键", "端点", "接口路径",
    "数据库", "数据源", "权限", "事实", "运行时",
)

SEMANTIC_WORDS = (
    "为什么", "为何", "如何", "怎么", "怎样", "架构", "设计", "流程", "实现",
    "原理", "方案", "区别", "作用", "机制", "策略", "规范", "约定",
)

FACT_TYPE_RULES: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("PORT", ("端口", "port")),
    ("SERVICE", ("服务", "service")),
    ("DEPENDENCY", ("依赖", "dependency")),
    ("TABLE", ("表结构", "数据表", "数据库表")),
    ("ROUTE", ("路由", "网关")),
    ("ENDPOINT", ("端点", "接口路径")),
    ("COMMIT", ("提交", "commit")),
)

MAX_FACT_SNIPPET_CHARS = 600
MAX_CHUNK_SNIPPET_CHARS = 1200


def intent_node(state: AgentState) -> dict[str, Any]:
    """规则意图识别并下发 ``meta`` 事件。"""
    question = state.get("question") or ""
    structured = _contains_any(question, STRUCTURED_WORDS)
    semantic = _contains_any(question, SEMANTIC_WORDS)
    if structured and semantic:
        intent = "MIXED"
    elif structured:
        intent = "STRUCTURED"
    else:
        intent = "SEMANTIC"
    client = state.get("llm_client") or get_llm_client()
    degraded = not client.available
    _emit(
        state,
        "meta",
        {
            "conversationId": _text(state.get("conversation_id")),
            "intent": intent,
            "degraded": degraded,
            "citations": [],
        },
    )
    return {"intent": intent, "degraded": degraded}


def retrieve_node(state: AgentState) -> dict[str, Any]:
    """调用只读工具取证，生成编号 S1..Sn 的引用。"""
    question = state.get("question") or ""
    settings = get_settings()
    context = ToolContext(
        user_id=_text(state.get("user_id")),
        permission_codes=state.get("permission_codes"),
    )
    tool_client = state.get("tool_client")
    citations: list[dict[str, Any]] = []
    steps = 0

    fact_type = _fact_type(question)
    if fact_type:
        steps += 1
        started = time.monotonic()
        try:
            data = execute_tool(
                "fact_get",
                {"type": fact_type, "limit": settings.fact_limit},
                context,
                client=tool_client,
            )
            items = data.get("items") or []
            citations.extend(_fact_citations(items, len(citations) + 1))
            _emit_tool(state, "fact_get", "ok", len(items), started)
        except ToolError as exc:
            logger.warning("事实查询工具失败：%s", exc)
            _emit_tool(state, "fact_get", "error", 0, started)

    steps += 1
    started = time.monotonic()
    try:
        data = execute_tool(
            "knowledge_search",
            {"query": question, "top_k": settings.search_top_k},
            context,
            client=tool_client,
        )
        hits = data.get("items") or []
        citations.extend(_chunk_citations(hits, len(citations) + 1))
        _emit_tool(state, "knowledge_search", "ok", len(hits), started)
    except ToolError as exc:
        logger.warning("知识检索工具失败：%s", exc)
        _emit_tool(state, "knowledge_search", "error", 0, started)

    return {
        "citations": validate_citations(citations, settings.repo_root),
        "tool_steps": state.get("tool_steps", 0) + steps,
    }


def answer_node(state: AgentState) -> dict[str, Any]:
    """模型流式生成；未配置或失败时回落检索摘要。"""
    citations = state.get("citations") or []
    client = state.get("llm_client") or get_llm_client()
    if client.available and citations:
        buffer: list[str] = []
        messages = build_messages(
            state.get("question") or "",
            citations,
            state.get("history") or [],
            str(state.get("memory_summary") or ""),
        )
        try:
            for delta in client.stream(messages):
                buffer.append(delta)
                _emit(state, "delta", {"text": delta})
            return {"answer": "".join(buffer), "degraded": False}
        except LlmError as exc:
            logger.warning("模型流式回答失败：%s", exc)
            if buffer:
                return {"answer": "".join(buffer), "degraded": True}
    summary = build_summary(state.get("question") or "", citations, _degrade_reason(client))
    _emit(state, "delta", {"text": summary})
    return {"answer": summary, "degraded": True}


def verify_node(state: AgentState) -> dict[str, Any]:
    """引用校验并收敛到正文实际标记的集合。"""
    citations = validate_citations(state.get("citations") or [], get_settings().repo_root)
    return {"citations": align_citations(state.get("answer") or "", citations)}


def remember_node(state: AgentState) -> dict[str, Any]:
    """把本轮问答写入会话记忆；失败不阻塞问答（append_turn 内已兜底）。"""
    store = state.get("memory_store")
    if store is None:
        return {}
    settings = get_settings()
    append_turn(
        store,
        state.get("conversation_id"),
        state.get("question") or "",
        state.get("answer") or "",
        settings.memory_max_turns,
        settings.memory_summary_chars,
    )
    return {}


def _fact_citations(items: list[dict[str, Any]], start_seq: int) -> list[dict[str, Any]]:
    """事实条目转引用。"""
    citations: list[dict[str, Any]] = []
    for index, item in enumerate(items):
        citations.append(
            {
                "sourceId": f"S{start_seq + index}",
                "kind": "fact",
                "path": item.get("sourcePath"),
                "title": f"{item.get('type')} {item.get('key')}",
                "startLine": None,
                "endLine": None,
                "snippet": json.dumps(item.get("value"), ensure_ascii=False)[:MAX_FACT_SNIPPET_CHARS],
                "factType": item.get("type"),
                "factKey": item.get("key"),
                "score": None,
            }
        )
    return citations


def _chunk_citations(items: list[dict[str, Any]], start_seq: int) -> list[dict[str, Any]]:
    """检索命中转引用。"""
    citations: list[dict[str, Any]] = []
    for index, item in enumerate(items):
        citations.append(
            {
                "sourceId": f"S{start_seq + index}",
                "kind": "chunk",
                "path": item.get("path"),
                "title": item.get("title"),
                "startLine": item.get("startLine"),
                "endLine": item.get("endLine"),
                "snippet": str(item.get("snippet") or "")[:MAX_CHUNK_SNIPPET_CHARS],
                "factType": None,
                "factKey": None,
                "score": item.get("score"),
            }
        )
    return citations


def _fact_type(question: str) -> str | None:
    """按关键词为结构化问题挑选事实类型。"""
    lower = question.lower()
    for fact_type, words in FACT_TYPE_RULES:
        if any(word in lower for word in words):
            return fact_type
    return None


def _degrade_reason(client: Any) -> str:
    """区分「未配置」与「调用失败」的降级原因。"""
    return "模型未接入" if not client.available else "模型暂不可用"


def _contains_any(text: str, words: tuple[str, ...]) -> bool:
    """判断文本是否命中任一关键词。"""
    lower = (text or "").lower()
    return any(word in lower for word in words)


def _emit_tool(
    state: AgentState,
    name: str,
    status: str,
    count: int,
    started: float,
) -> None:
    """下发工具轨迹事件。"""
    _emit(
        state,
        "tool",
        {
            "name": name,
            "status": status,
            "count": count,
            "elapsedMillis": int((time.monotonic() - started) * 1000),
        },
    )


def _emit(state: AgentState, event: str, payload: dict[str, Any]) -> None:
    """有回调时下发事件（非流式调用忽略）。"""
    emit = state.get("emit")
    if emit is not None:
        emit(event, payload)


def _text(value: Any) -> str | None:
    """把会话/用户标识转为字符串（SSE 载荷统一为字符串）。"""
    return None if value is None else str(value)
