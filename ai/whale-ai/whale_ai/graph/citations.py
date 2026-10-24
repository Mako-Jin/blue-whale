"""引用校验：结构裁剪、行号收敛与正文标记一致性。

语义对齐 ``whale-agent`` 的 ``CitationValidator``：丢弃空路径、越界行号与不存在的文件，
并把结束行裁剪到文件实际范围；``git:`` / ``runtime:`` 视为虚拟来源，不做存在性校验。
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any

MARKER_PATTERN = re.compile(r"\[S(\d+)\]")

VIRTUAL_PREFIXES = ("git:", "runtime:")

SUMMARY_SNIPPET_LIMIT = 200


def validate_citations(
    citations: list[dict[str, Any]], repo_root: str = ""
) -> list[dict[str, Any]]:
    """校验引用并去重（保持原顺序）。"""
    root = Path(repo_root).resolve() if repo_root else None
    result: list[dict[str, Any]] = []
    seen: set[tuple[str, Any]] = set()
    for citation in citations:
        path = str(citation.get("path") or "").strip()
        if not path:
            continue
        key = (path, citation.get("startLine"))
        if key in seen:
            continue
        if root is not None and not path.startswith(VIRTUAL_PREFIXES):
            file = (root / path).resolve()
            if not _inside(root, file) or not file.is_file():
                continue
            if citation.get("startLine") is not None:
                total = _line_count(file)
                start = int(citation["startLine"])
                if total <= 0 or start > total:
                    continue
                end = citation.get("endLine")
                citation["endLine"] = total if end is None else min(int(end), total)
        seen.add(key)
        result.append(citation)
    return result


def align_citations(
    answer: str, citations: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    """保留正文实际引用的编号；正文无标记时原样返回。"""
    markers = {f"S{number}" for number in MARKER_PATTERN.findall(answer or "")}
    if not markers:
        return list(citations)
    return [citation for citation in citations if citation.get("sourceId") in markers]


def build_summary(
    question: str, citations: list[dict[str, Any]], reason: str = "模型未接入"
) -> str:
    """无模型或模型失败时的检索摘要（同样带编号，保证标记与引用一致）。"""
    if not citations:
        return (
            f"知识库中未找到与「{_one_line(question, 120)}」相关的资料。\n\n"
            "可尝试补充服务名、表名或接口路径等关键词；或先重建索引"
            "（`POST /agent/index/rebuild`）。"
        )
    lines = [f"{reason}，以下为检索结果摘要（共 {len(citations)} 条证据）：", ""]
    for citation in citations:
        location = str(citation.get("path") or "")
        if citation.get("startLine") is not None:
            location = f"{location}（L{citation.get('startLine')}-{citation.get('endLine')}）"
        snippet = _one_line(str(citation.get("snippet") or ""), SUMMARY_SNIPPET_LIMIT)
        lines.append(f"- [{citation.get('sourceId')}] `{location}`：{snippet}")
    lines.extend(["", "> 配置 `WHALE_AGENT_LLM_API_KEY` 后可获得模型生成的完整回答。"])
    return "\n".join(lines)


def _inside(root: Path, file: Path) -> bool:
    """判断文件是否位于根目录内（防路径穿越）。"""
    try:
        file.relative_to(root)
    except ValueError:
        return False
    return True


def _line_count(file: Path) -> int:
    """统计文本行数；读取失败返回 0。"""
    try:
        with file.open("r", encoding="utf-8", errors="ignore") as handle:
            return sum(1 for _ in handle)
    except OSError:
        return 0


def _one_line(text: str, limit: int) -> str:
    """压成单行并截断。"""
    flat = " ".join(text.split())
    return flat if len(flat) <= limit else flat[:limit] + "…"
