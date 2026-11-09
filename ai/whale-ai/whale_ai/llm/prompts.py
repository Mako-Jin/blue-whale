"""提示词组装：证据编号化，要求模型用 ``[S1]`` 标记引用。"""

from __future__ import annotations

from typing import Any

SYSTEM_PROMPT = (
    "你是蓝鲸平台的智能体助手，只依据给定证据回答架构、技术与数据问题。\n"
    "要求：\n"
    "1. 每条结论后用 [S1] 形式标注来源，编号必须来自本次证据清单；\n"
    "2. 证据不足时明确说明缺少什么，不得编造路径、行号或配置；\n"
    "3. 历史对话中的 [S1] 等标记只代表历史来源，本次回答必须使用本次证据编号；\n"
    "4. 使用简洁的中文 Markdown 回答。"
)

MAX_SNIPPET_CHARS = 800

# 引用契约：无论助手是否自定义 system prompt，都必须遵守 [S1] 标记约定，
# 否则 verify_citations 节点的引用校验无法对齐（Wave 6.01 多助手口径）。
CITATION_CONTRACT = (
    "回答规范：\n"
    "1. 每条结论后用 [S1] 形式标注来源，编号必须来自本次证据清单；\n"
    "2. 证据不足时明确说明缺少什么，不得编造路径、行号或配置；\n"
    "3. 使用简洁的中文 Markdown 回答。"
)


def build_messages(
    question: str,
    citations: list[dict[str, Any]],
    history: list[dict[str, str]] | None = None,
    summary: str = "",
    system_prompt: str | None = None,
) -> list[dict[str, str]]:
    """组装模型消息（system + 可选历史 + 带证据的当前问题）。

    Args:
        question: 当前问题。
        citations: 本次证据清单。
        history: 最近若干轮问答（``question``/``answer``）。
        summary: 更早轮次压缩后的摘要。
        system_prompt: 助手自定义系统提示词（Wave 6.01）；缺省用 ``SYSTEM_PROMPT``。
            自定义时引用契约仍作为独立 system 消息追加，保证引用校验可用。

    Returns:
        OpenAI 协议消息列表。
    """
    persona = (system_prompt or "").strip() or SYSTEM_PROMPT
    messages: list[dict[str, str]] = [{"role": "system", "content": persona}]
    if system_prompt and system_prompt.strip() and system_prompt.strip() != SYSTEM_PROMPT:
        messages.append({"role": "system", "content": CITATION_CONTRACT})
    if summary:
        messages.append({"role": "system", "content": f"更早对话摘要：\n{summary}"})
    for turn in history or []:
        asked = str(turn.get("question") or "")
        replied = str(turn.get("answer") or "")
        if asked:
            messages.append({"role": "user", "content": asked})
        if replied:
            messages.append({"role": "assistant", "content": replied})
    evidence = _format_evidence(citations) or "（无可用证据）"
    messages.append({"role": "user", "content": f"证据清单：\n{evidence}\n\n问题：{question}"})
    return messages


def _format_evidence(citations: list[dict[str, Any]]) -> str:
    """把引用格式化为带编号的证据块。"""
    blocks: list[str] = []
    for citation in citations:
        location = str(citation.get("path") or "")
        start = citation.get("startLine")
        if start is not None:
            location = f"{location} L{start}-{citation.get('endLine')}"
        snippet = _flatten(str(citation.get("snippet") or ""))[:MAX_SNIPPET_CHARS]
        blocks.append(f"[{citation.get('sourceId')}] {location}\n{snippet}")
    return "\n\n".join(blocks)


def _flatten(text: str) -> str:
    """压缩连续空白，控制提示词体积。"""
    return " ".join(text.split())
