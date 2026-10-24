"""引用校验单测：结构裁剪、文件存在性与正文标记一致性。"""

from __future__ import annotations

from pathlib import Path

from whale_ai.graph.citations import align_citations, build_summary, validate_citations


def _citation(source_id: str, path: str | None, start: int | None = None) -> dict:
    return {
        "sourceId": source_id,
        "kind": "chunk",
        "path": path,
        "startLine": start,
        "endLine": start,
        "snippet": "片段",
    }


def test_validate_drops_blank_path_and_duplicates() -> None:
    citations = [
        _citation("S1", "docs/a.md", 1),
        _citation("S2", "docs/a.md", 1),
        _citation("S3", ""),
        _citation("S4", None),
    ]

    result = validate_citations(citations)

    assert [item["sourceId"] for item in result] == ["S1"]


def test_validate_checks_file_and_clamps_end_line(tmp_path: Path) -> None:
    (tmp_path / "docs").mkdir()
    (tmp_path / "docs/a.md").write_text("l1\nl2\nl3\n", encoding="utf-8")
    citations = [
        _citation("S1", "docs/a.md", 2),
        _citation("S2", "docs/missing.md", 1),
        _citation("S3", "../outside.md", 1),
    ]
    citations[0]["endLine"] = 99

    result = validate_citations(citations, str(tmp_path))

    assert [item["sourceId"] for item in result] == ["S1"]
    assert result[0]["endLine"] == 3


def test_virtual_sources_skip_existence_check(tmp_path: Path) -> None:
    result = validate_citations(
        [_citation("S1", "git:HEAD"), _citation("S2", "runtime:nacos")], str(tmp_path)
    )

    assert len(result) == 2


def test_align_keeps_only_marked_citations() -> None:
    citations = [_citation("S1", "docs/a.md", 1), _citation("S2", "docs/b.md", 1)]

    assert [item["sourceId"] for item in align_citations("结论见 [S2]。", citations)] == ["S2"]
    assert len(align_citations("没有标记的正文", citations)) == 2


def test_summary_contains_markers_and_hint() -> None:
    summary = build_summary("平台架构？", [_citation("S1", "docs/a.md", 1)])

    assert "[S1]" in summary
    assert "docs/a.md" in summary
    assert "WHALE_AGENT_LLM_API_KEY" in summary


def test_summary_without_citations_reports_empty() -> None:
    summary = build_summary("未知问题？", [])

    assert "知识库中未找到" in summary
    assert "POST /agent/index/rebuild" in summary
