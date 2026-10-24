"""LangGraph 最小骨架图。

T10 只冻结图形态与状态协议：``intent → answer``。后续任务按规划 §6.2 依次插入
``retrieve``、``think``、``tools``、``verify_citations`` 节点，本模块保持
``build_graph()`` 与 ``run_agent()`` 两个入口不变。
"""

from __future__ import annotations

from langgraph.graph import END, START, StateGraph

from whale_ai.graph.state import AgentState

PLACEHOLDER_ANSWER = "鲸鱼智能体骨架已就绪：知识检索、工具调用与引用校验将在后续任务接入。"


def _intent(state: AgentState) -> dict[str, object]:
    """意图识别占位节点：骨架阶段统一标记为 ``SKELETON``。"""
    del state
    return {"intent": "SKELETON", "degraded": False}


def _answer(state: AgentState) -> dict[str, object]:
    """回答生成占位节点：返回固定提示，不调用模型与工具。"""
    del state
    return {"answer": PLACEHOLDER_ANSWER, "citations": []}


def build_graph():
    """构建并编译骨架图。"""
    graph = StateGraph(AgentState)
    graph.add_node("intent", _intent)
    graph.add_node("answer", _answer)
    graph.add_edge(START, "intent")
    graph.add_edge("intent", "answer")
    graph.add_edge("answer", END)
    return graph.compile()


_COMPILED = build_graph()


def run_agent(
    question: str,
    conversation_id: int | None = None,
    scope: str | None = None,
    permission_codes: str | None = None,
) -> AgentState:
    """同步执行一次骨架图。

    Args:
        question: 用户问题。
        conversation_id: 智能体会话ID，可空。
        scope: 问答范围（``platform``/``data`` 等），可空。
        permission_codes: 逗号分隔的权限码，可空。

    Returns:
        合并后的运行状态。
    """
    return _COMPILED.invoke(
        {
            "question": question,
            "conversation_id": conversation_id,
            "scope": scope,
            "permission_codes": permission_codes,
            "tool_steps": 0,
        }
    )
