"""会话摘要：按规则压缩被淘汰的轮次，不额外消耗模型调用。

摘要只保留「问题 + 回答首句」，并限制总长度，保证注入提示词时体积可控。
"""

from __future__ import annotations

import re

MARKER_PATTERN = re.compile(r"\[S\d+\]")
BLANK_PATTERN = re.compile(r"[ \t]{2,}")

MAX_QUESTION_CHARS = 120
MAX_ANSWER_CHARS = 160
SENTENCE_ENDS = ("。", "！", "？", "\n")


def strip_markers(text: str) -> str:
    """去掉引用标记（历史答案中的 ``[S1]`` 不代表本次证据）。"""
    return BLANK_PATTERN.sub(" ", MARKER_PATTERN.sub("", text or "")).strip()


def clip(text: str, limit: int) -> str:
    """按字符截断，超长时补省略号。"""
    text = (text or "").strip()
    if limit <= 0 or len(text) <= limit:
        return text
    return text[:limit] + "…"


def first_sentence(text: str) -> str:
    """取首个句子，用于压缩回答要点。"""
    text = (text or "").strip()
    if not text:
        return ""
    cut = len(text)
    for end in SENTENCE_ENDS:
        index = text.find(end)
        if index != -1:
            cut = min(cut, index + 1)
    return text[:cut].strip()


def merge_summary(existing: str, turns: list[dict[str, str]], max_chars: int) -> str:
    """把被淘汰的轮次并入摘要，并按上限保留最近内容。

    Args:
        existing: 已有摘要。
        turns: 被挤出窗口的轮次（时间升序）。
        max_chars: 摘要总长上限。

    Returns:
        合并后的摘要文本。
    """
    lines: list[str] = [existing.strip()] if existing and existing.strip() else []
    for turn in turns:
        question = clip(str(turn.get("question") or ""), MAX_QUESTION_CHARS)
        answer = clip(first_sentence(str(turn.get("answer") or "")), MAX_ANSWER_CHARS)
        if not question and not answer:
            continue
        lines.append(f"- 用户：{question}｜回答：{answer}")
    text = "\n".join(lines).strip()
    if max_chars > 0 and len(text) > max_chars:
        text = text[-max_chars:]
    return text
