"""LangGraph 编排图：``intent → retrieve → answer → verify_citations``。

``run_agent()`` 是唯一执行入口；流式端点通过状态注入 ``emit`` 回调，
在同一张图上完成 token 级下发，不另立执行路径。
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

from langgraph.graph import END, START, StateGraph

from whale_ai.graph.nodes import answer_node, intent_node, retrieve_node, verify_node
from whale_ai.graph.state import AgentState

Emit = Callable[[str, dict[str, Any]], None]


def build_graph():
    """构建并编译问答图。"""
    graph = StateGraph(AgentState)
    graph.add_node("intent", intent_node)
    graph.add_node("retrieve", retrieve_node)
    graph.add_node("answer", answer_node)
    graph.add_node("verify_citations", verify_node)
    graph.add_edge(START, "intent")
    graph.add_edge("intent", "retrieve")
    graph.add_edge("retrieve", "answer")
    graph.add_edge("answer", "verify_citations")
    graph.add_edge("verify_citations", END)
    return graph.compile()


_COMPILED = build_graph()


def run_agent(
    question: str,
    conversation_id: int | None = None,
    scope: str | None = None,
    user_id: str | None = None,
    permission_codes: str | None = None,
    emit: Emit | None = None,
    tool_client: Any = None,
    llm_client: Any = None,
) -> AgentState:
    """执行一次问答图。

    Args:
        question: 用户问题。
        conversation_id: 智能体会话ID，可空。
        scope: 问答范围（``code,doc,sql,config`` 等），可空。
        user_id: 提问者ID（透传给只读工具）。
        permission_codes: 逗号分隔权限码（透传给只读工具）。
        emit: 可选事件回调（``meta``/``tool``/``delta``）。
        tool_client: 可选工具客户端替身（测试用）。
        llm_client: 可选模型客户端替身（测试用）。

    Returns:
        合并后的运行状态（含 ``citations``、``answer``、``degraded``）。
    """
    return _COMPILED.invoke(
        {
            "question": question,
            "conversation_id": conversation_id,
            "scope": scope,
            "user_id": user_id,
            "permission_codes": permission_codes,
            "tool_steps": 0,
            "emit": emit,
            "tool_client": tool_client,
            "llm_client": llm_client,
        }
    )
