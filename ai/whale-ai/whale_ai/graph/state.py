"""LangGraph 编排状态定义。"""

from __future__ import annotations

from typing import Any, Callable, TypedDict


class AgentState(TypedDict, total=False):
    """一次问答的运行状态。

    ``total=False`` 允许节点只返回增量字段，由 LangGraph 合并到全局状态。
    ``emit`` / ``tool_client`` / ``llm_client`` 为进程内注入的运行依赖，不参与持久化。
    """

    conversation_id: int | None
    question: str
    scope: str | None
    user_id: str | None
    permission_codes: str | None
    intent: str
    answer: str
    citations: list[dict[str, Any]]
    tool_steps: int
    degraded: bool
    emit: Callable[[str, dict[str, Any]], None] | None
    tool_client: Any
    llm_client: Any
