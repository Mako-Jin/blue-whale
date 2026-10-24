"""提示词组装：证据编号化，要求模型用 ``[S1]`` 标记引用。"""

from __future__ import annotations

from typing import Any

SYSTEM_PROMPT = (
    "你是蓝鲸平台的智能体助手，只依据给定证据回答架构、技术与数据问题。\n"
    "要求：\n"
    "1. 每条结论后用 [S1] 形式标注来源，编号必须来自证据清单；\n"
    "2. 证据不足时明确说明缺少什么，不得编造路径、行号或配置；\n"
    "3. 使用简洁的中文 Markdown 回答。"
)

MAX_SNIPPET_CHARS = 800


def build_messages(question: str, citations: list[dict[str, Any]]) -> list[dict[str, str]]:
    """组装模型消息（system + 带证据的 user）。"""
    evidence = _format_evidence(citations) or "（无可用证据）"
    return [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": f"证据清单：\n{evidence}\n\n问题：{question}"},
    ]


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
