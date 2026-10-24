"""LangGraph 编排状态定义。"""

from __future__ import annotations

from typing import Any, TypedDict


class AgentState(TypedDict, total=False):
    """一次问答的运行状态。

    ``total=False`` 允许节点只返回增量字段，由 LangGraph 合并到全局状态。
    """

    conversation_id: int | None
    question: str
    scope: str | None
    permission_codes: str | None
    intent: str
    answer: str
    citations: list[dict[str, Any]]
    tool_steps: int
    degraded: bool
