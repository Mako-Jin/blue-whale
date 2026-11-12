#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""雪花 ID 前端类型门禁（Long → string）

背景
----
后端雪花 ID 是 Long（64 位），前端 `number` 只有 53 位安全整数：超过 2^53 会
**静默丢精度**（不报错、不告警，只是查不到 / 改错对象）。所以全站口径是
「Long 一律 string」，由 `SpringdocConfig` 把 OpenAPI 的 int64 改写成 string，
前端按生成的 `*-schema.d.ts` 接线。

本门禁补上「接线之后又被改回 number」这一类回归：
  · 数据源 = 该应用已生成的契约（`src/api/generated/*-schema.d.ts`）
  · 检查点 = 该应用 `src/` 下手写的 ID 字段声明
  · 判据   = 契约里是 string 的 ID 字段，手写侧不得声明为 number
            （含 `number | string` 联合——联合等于把丢精度的口子留着）

未出契约的服务（服务镜像未重建）无法比对，用 ALLOWLIST 显式记录，避免
「假绿」：条目必须写清服务名与待办，出契约后应当删除。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# 应用 -> 它消费的契约（契约文件位于 <app>/src/api/generated/<name>-schema.d.ts）
APP_CONTRACTS = {
    "whale-frontend/admin": ["auth", "file", "job", "message", "pay"],
    "whale-frontend/platform-apps/whale-portal": ["message"],
    "whale-frontend/platform-apps/whale-twin": ["twin"],
    "whale-frontend/platform-apps/whale-agent": ["agent"],
}

# 明知是 number 但暂不判违规：(相对路径, 字段) -> 原因
ALLOWLIST = {
    ("whale-frontend/admin/src/api/archive.ts", "parentId"): "archive 服务未出契约",
    ("whale-frontend/platform-apps/whale-portal/src/api/notice.ts", "ruleId"): "alert 服务未出契约",
    ("whale-frontend/platform-apps/whale-portal/src/api/workflow.ts", "instanceId"): "workflow 服务未出契约",
}

SCHEMA_FIELD = re.compile(r"^[ \t]+(\w+)(\??):[ \t]*([^;]+);", re.M)
# 只看复合名（userId/tenantId/entityId…）：裸 id 每个服务都有，无法用单服务契约判定，
# 由各应用 api/contract-checks.ts 的 Equal 断言逐文件钉死。
ID_NAME = re.compile(r"^[a-z][A-Za-z0-9]*Id$")


def contract_id_fields(schema_path: Path) -> set[str]:
    """契约中声明为 string 的 ID 字段名集合（逐行解析，避免跨行误吞）。"""
    if not schema_path.exists():
        return set()
    names: set[str] = set()
    for line in schema_path.read_text(encoding="utf-8", errors="replace").splitlines():
        m = SCHEMA_FIELD.match(line)
        if not m:
            continue
        name, typ = m.group(1), m.group(3).strip()
        if ID_NAME.match(name) and typ.startswith("string"):
            names.add(name)
    return names


def scan(app: str, id_names: set[str]) -> list[tuple[str, int, str, str]]:
    """返回 (相对路径, 行号, 字段名, 该行原文)。"""
    if not id_names:
        return []
    pattern = re.compile(
        r"\b(%s)(\??)\s*:\s*number\b(?:\s*\|\s*string\b)?" % "|".join(sorted(id_names))
    )
    found = []
    src = ROOT / app / "src"
    for path in sorted(src.rglob("*")):
        if path.suffix not in (".ts", ".tsx"):
            continue
        if "generated" in path.parts or "node_modules" in path.parts:
            continue
        rel = path.relative_to(ROOT).as_posix()
        for lineno, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            if line.lstrip().startswith(("//", "*", "/*")):
                continue
            m = pattern.search(line)
            if m:
                found.append((rel, lineno, m.group(1), line.strip()))
    return found


def main() -> int:
    violations = []
    checked = 0
    for app, contracts in APP_CONTRACTS.items():
        id_names: set[str] = set()
        for name in contracts:
            id_names |= contract_id_fields(ROOT / app / "src/api/generated" / f"{name}-schema.d.ts")
        if not id_names:
            print(f"[SKIP] {app}: 未找到契约（缺 src/api/generated/*-schema.d.ts）")
            continue
        checked += 1
        for rel, lineno, field, text in scan(app, id_names):
            if (rel, field) in ALLOWLIST:
                continue
            violations.append((rel, lineno, field, text))

    if violations:
        print(f"[FAIL] 雪花 ID 被声明成 number：{len(violations)} 处")
        for rel, lineno, field, text in violations:
            print(f"  {rel}:{lineno}  {field} -> {text}")
        print("\n口径：Long 一律 string（雪花 ID 超 2^53，number 会静默丢精度）。")
        print("若该字段确属未出契约的服务，请把服务补进 scripts/gen-openapi-contracts.py 并接线，")
        print("不要往 ALLOWLIST 里塞。")
        return 1

    print(f"[PASS] 雪花 ID 类型门禁：{checked} 个应用、契约覆盖范围内无 number 声明")
    return 0


if __name__ == "__main__":
    sys.exit(main())
