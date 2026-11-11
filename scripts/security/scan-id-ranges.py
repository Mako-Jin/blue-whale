"""权限 / 菜单 / 角色号段占用扫描（静态，读迁移 SQL，不连库）。

用法：
    python scripts/security/scan-id-ranges.py            # 人读报表
    python scripts/security/scan-id-ranges.py --json      # 机器读（CI 用）

退出码：0 = 无冲突；1 = 有冲突（同号异构 / 同编码异号 / 声明号段越界）。

背景：权限号段靠「先查 naming/全景文档再申请」的口头约定维护，177 份迁移脚本里
ID 全是手写的。一旦两个模块撞号，`WHERE NOT EXISTS (... WHERE id = 1900 OR perm_code = ...)`
会让**后执行的那份静默跳过**——不是报错，是新权限点根本不存在，接口一路 403，
而建表与单测全绿，极难定位。本脚本把「撞号」变成可重复执行的检查。

判定口径：
    ERROR  同一张表同一 ID 被**不同编码**争用（后执行者被幂等条件整条吞掉）
    WARN  同一编码绑定了多个 ID（后者是 no-op；授权若按 ID 写就会漏权限）
    WARN  ID 不在该脚本头部声明的号段内（启发式解析，仅提示）
    INFO  同一 ID 同一编码在多个脚本里重复声明（幂等脚本的正常形态）
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCAN_DIRS = [ROOT / "whale-backend", ROOT / "deploy"]
SKIP_PARTS = {"target", "node_modules", "build", "dist", ".git"}

# INSERT INTO sys_permission (...) \n SELECT 1900, 0, 'sys:lms:browse', ...
RE_INSERT = re.compile(
    r"INSERT\s+INTO\s+(sys_permission|sys_menu|sys_role)\s*\([^)]*\)\s*"
    r"SELECT\s+(\d+)\s*,\s*(?:[^,']*,\s*)?'([^']+)'",
    re.IGNORECASE,
)
# 头部声明：-- 权限编号 : LMS 段 1900–1949；本脚本使用 1900–1910。
RE_DECL = {
    "sys_permission": re.compile(r"^--\s*权限编号\s*[:：]\s*(.+)$", re.MULTILINE),
    "sys_menu": re.compile(r"^--\s*菜单编号\s*[:：]\s*(.+)$", re.MULTILINE),
    "sys_role": re.compile(r"^--\s*角色编号\s*[:：]\s*(.+)$", re.MULTILINE),
}
RE_RANGE = re.compile(r"(\d{3,5})\s*[–\-~—]\s*(\d{3,5})")


@dataclass
class Entry:
    table: str
    ident: int
    code: str
    file: str
    line: int


@dataclass
class Report:
    entries: list[Entry] = field(default_factory=list)
    declared: dict[str, set[int]] = field(default_factory=dict)
    declared_by_table: dict[tuple[str, str], set[int]] = field(default_factory=dict)
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    info: list[str] = field(default_factory=list)


def iter_sql() -> list[Path]:
    out: list[Path] = []
    for base in SCAN_DIRS:
        if not base.exists():
            continue
        for p in base.rglob("*.sql"):
            if SKIP_PARTS & set(p.parts):
                continue
            out.append(p)
    return sorted(out)


def parse_declared(text: str) -> dict[str, set[int]]:
    ranges: dict[str, set[int]] = {}
    for table, pattern in RE_DECL.items():
        m = pattern.search(text)
        if not m:
            continue
        ids: set[int] = set()
        for lo, hi in RE_RANGE.findall(m.group(1)):
            lo_i, hi_i = int(lo), int(hi)
            if 0 <= hi_i - lo_i <= 200:
                ids.update(range(lo_i, hi_i + 1))
        if ids:
            ranges[table] = ids
    return ranges


def parse_header(text: str) -> set[int]:
    """头部注释块里出现的全部号段（`-- 权限 79–82 / 309–322` 这类写法也能识别）。

    只取文件开头到第一条 SQL 之前的部分——正文注释里的“校验建议”也会带区间，
    拿它当声明会把检查放成永远通过。
    """
    head = text.split("INSERT INTO", 1)[0]
    ids: set[int] = set()
    for lo, hi in RE_RANGE.findall(head):
        lo_i, hi_i = int(lo), int(hi)
        if 0 <= hi_i - lo_i <= 300:
            ids.update(range(lo_i, hi_i + 1))
    return ids


def scan() -> Report:
    rep = Report()
    for path in iter_sql():
        text = path.read_text(encoding="utf-8-sig", errors="replace")
        if "sys_permission" not in text and "sys_menu" not in text and "sys_role" not in text:
            continue
        rel = path.relative_to(ROOT).as_posix()
        declared = parse_declared(text)
        for table, ids in declared.items():
            rep.declared.setdefault(rel, set()).update(ids)
            rep.declared_by_table[(rel, table)] = ids
        header = parse_header(text)
        if header:
            rep.declared.setdefault(rel, set()).update(header)
        for m in RE_INSERT.finditer(text):
            table, ident, code = m.group(1).lower(), int(m.group(2)), m.group(3)
            line = text.count("\n", 0, m.start()) + 1
            rep.entries.append(Entry(table, ident, code, rel, line))

    # 同表同 ID 跨文件
    by_id: dict[tuple[str, int], list[Entry]] = defaultdict(list)
    by_code: dict[tuple[str, str], list[Entry]] = defaultdict(list)
    for e in rep.entries:
        by_id[(e.table, e.ident)].append(e)
        by_code[(e.table, e.code)].append(e)

    for (table, ident), items in sorted(by_id.items()):
        codes = {i.code for i in items}
        files = {i.file for i in items}
        if len(codes) > 1:
            # 真撞号：两份脚本抢一个 ID，后执行者被幂等条件整条吞掉
            detail = " / ".join(f"{i.file}:{i.line}={i.code}" for i in items)
            rep.errors.append(f"{table} ID {ident} 被不同编码争用 → {detail}")
        elif len(files) > 1:
            # 同 ID 同编码：幂等脚本的重复声明（如 5.57/5.66 都声明 pm_manager），无害
            rep.info.append(f"{table} ID {ident}（{items[0].code}）在 {len(files)} 份脚本中重复声明")

    for (table, code), items in sorted(by_code.items()):
        ids = {i.ident for i in items}
        if len(ids) > 1:
            # 编码已在别的 ID 上存在 → 这一行是 no-op；若授权按 ID 写，权限就永远发不出去
            detail = " / ".join(f"{i.file}:{i.line}={i.ident}" for i in items)
            rep.warnings.append(
                f"{table} 编码 {code} 存在多个 ID {sorted(ids)}（{detail}）；"
                f"后者会被幂等条件跳过，授权请按 perm_code 而非 ID"
            )

    # 声明号段越界（只认 `-- 权限编号 : ...` / `-- 菜单编号 : ...` 这类显式声明，
    # 且只查权限与菜单；角色 ID 走自己的 14xxx/25xxx 约定，同一处判断会全是噪声）
    outliers: dict[str, list[Entry]] = defaultdict(list)
    for e in rep.entries:
        if e.table == "sys_role":
            continue
        allowed = rep.declared_by_table.get((e.file, e.table))
        if allowed and e.ident not in allowed:
            outliers[e.file].append(e)
    for file, items in sorted(outliers.items()):
        ids = sorted({i.ident for i in items})
        rep.warnings.append(
            f"{file}: {len(items)} 个 ID 不在头部声明的号段内（{ids[:8]}{'…' if len(ids) > 8 else ''}）"
        )

    # 未声明号段（WARN，按文件汇总）
    undeclared: dict[str, int] = defaultdict(int)
    for e in rep.entries:
        if e.file not in rep.declared:
            undeclared[e.file] += 1
    for file, n in sorted(undeclared.items()):
        rep.warnings.append(f"{file}: {n} 个 ID 未声明号段（建议头部补 `-- 权限编号 : ...`）")
    return rep


def module_of(code: str) -> str:
    parts = code.split(":")
    if len(parts) >= 2 and parts[0] == "sys":
        return parts[1]
    return "其他"


def render(rep: Report) -> str:
    lines: list[str] = []
    perms = [e for e in rep.entries if e.table == "sys_permission"]
    menus = [e for e in rep.entries if e.table == "sys_menu"]
    roles = [e for e in rep.entries if e.table == "sys_role"]

    lines.append("# 号段占用报表")
    lines.append("")
    lines.append(f"脚本数 {len({e.file for e in rep.entries})}；"
                 f"权限点 {len(perms)}；菜单 {len(menus)}；角色 {len(roles)}")
    lines.append("")
    lines.append("| 模块 | 权限数 | 最小 ID | 最大 ID | 占用（去重后区间） |")
    lines.append("|---|---|---|---|---|")
    groups: dict[str, list[int]] = defaultdict(list)
    for e in perms:
        groups[module_of(e.code)].append(e.ident)
    for mod, ids in sorted(groups.items()):
        uniq = sorted(set(ids))
        spans = collapse(uniq)
        lines.append(f"| {mod} | {len(uniq)} | {uniq[0]} | {uniq[-1]} | {spans} |")

    if menus:
        lines.append("")
        mid = sorted({e.ident for e in menus})
        lines.append(f"菜单：{len(mid)} 个，{mid[0]}–{mid[-1]}，占用 {collapse(mid)}")

    lines.append("")
    if rep.errors:
        lines.append(f"## ERROR（{len(rep.errors)}）")
        lines.extend(f"- {x}" for x in rep.errors)
    else:
        lines.append("## ERROR（0）—— 无撞号")
    if rep.warnings:
        lines.append("")
        lines.append(f"## WARN（{len(rep.warnings)}）")
        lines.extend(f"- {x}" for x in rep.warnings[:40])
        if len(rep.warnings) > 40:
            lines.append(f"- …… 另有 {len(rep.warnings) - 40} 条")
    if rep.info:
        lines.append("")
        lines.append(f"## INFO（{len(rep.info)}）—— 幂等重复声明，无需处理")
        lines.extend(f"- {x}" for x in rep.info[:10])
        if len(rep.info) > 10:
            lines.append(f"- …… 另有 {len(rep.info) - 10} 条")
    return "\n".join(lines)


def collapse(ids: list[int]) -> str:
    """把有序去重列表折叠成 `1900–1910, 1922–1949` 形式。"""
    out: list[str] = []
    start = prev = ids[0]
    for i in ids[1:]:
        if i == prev + 1:
            prev = i
            continue
        out.append(f"{start}" if start == prev else f"{start}–{prev}")
        start = prev = i
    out.append(f"{start}" if start == prev else f"{start}–{prev}")
    return ", ".join(out)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true", help="输出 JSON（CI）")
    ap.add_argument("--strict", action="store_true", help="WARN 也判失败（收紧门禁时用）")
    args = ap.parse_args()

    rep = scan()
    if args.json:
        payload = {
            "errors": rep.errors,
            "warnings": len(rep.warnings),
            "infos": len(rep.info),
            "permissions": len([e for e in rep.entries if e.table == "sys_permission"]),
            "menus": len([e for e in rep.entries if e.table == "sys_menu"]),
            "roles": len([e for e in rep.entries if e.table == "sys_role"]),
        }
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        print(render(rep))
    return 1 if rep.errors or (args.strict and rep.warnings) else 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
