"""审计日志追加写门禁：禁止任何代码/脚本改写或删除审计类日志表。

用法：
    python scripts/security/check-audit-append-only.py            # 人读报表
    python scripts/security/check-audit-append-only.py --json     # 机器读（CI 用）
    python scripts/security/check-audit-append-only.py --root <仓库根> --scan-dir <目录>

退出码：0 = 未发现改写；1 = 发现 UPDATE / DELETE / REPLACE / TRUNCATE 命中受保护表。

为什么需要它（等保 2.0「审计记录应受到保护，避免被删除、修改或覆盖」）：
    audit_log 与四张观测通道表是 append-only 数据，服务层只允许 INSERT + SELECT。
    一旦有人为了「清理脏数据」写一句 DELETE，编译、启动、单测、其它门禁全绿，
    只有事后审计取证时才会发现证据链断了。本脚本把这类改动拦在提交前。

受保护表（新增表请同步 PROTECTED_TABLES）：
    audit_log / api_log / behavior_log / frontend_log / outbound_log

允许的写法：
    INSERT INTO 上述表、SELECT 查询、`BEFORE UPDATE ON audit_log`（触发器 DDL，
    表名不紧跟 UPDATE，不会误判）；`db/init.sql` 里的 `DROP TABLE IF EXISTS`
    属于建库引导，放行并记为提示；已执行的一次性迁移按 ALLOWED_MUTATION_SCRIPTS
    豁免（带理由，新增豁免必须在提交说明里写清为什么不会再有第二次）。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

PROTECTED_TABLES = (
    "audit_log",
    "api_log",
    "behavior_log",
    "frontend_log",
    "outbound_log",
)

SCAN_SUFFIXES = (".java", ".xml", ".sql")

# 建库引导脚本可以 DROP TABLE 重建
BOOTSTRAP_FILES = {"init.sql"}

# 已执行的历史迁移豁免（一次性动作，不会再有第二次）：文件名 -> 理由
ALLOWED_MUTATION_SCRIPTS = {
    "V25__drop_local_log_tables.sql":
        "auth 服务日志改为统一转发 whale-audit，本迁移删除 auth 库内的本地日志副本（已执行，不再重复）",
}

TABLE_ALT = "|".join(PROTECTED_TABLES)

# UPDATE / DELETE / REPLACE / TRUNCATE 命中受保护表（允许 schema 前缀）
MUTATION_PATTERNS = (
    ("UPDATE", re.compile(rf"\bUPDATE\s+(?:`?\w+`?\.)?`?({TABLE_ALT})`?\b", re.IGNORECASE)),
    ("DELETE", re.compile(rf"\bDELETE\s+(?:LOW_PRIORITY\s+|QUICK\s+|IGNORE\s+)*FROM\s+(?:`?\w+`?\.)?`?({TABLE_ALT})`?\b", re.IGNORECASE)),
    ("REPLACE", re.compile(rf"\bREPLACE\s+(?:INTO\s+)?(?:`?\w+`?\.)?`?({TABLE_ALT})`?\b", re.IGNORECASE)),
    ("TRUNCATE", re.compile(rf"\bTRUNCATE\s+(?:TABLE\s+)?(?:`?\w+`?\.)?`?({TABLE_ALT})`?\b", re.IGNORECASE)),
)

DROP_PATTERN = re.compile(rf"\bDROP\s+TABLE\s+(IF\s+EXISTS\s+)?(?:`?\w+`?\.)?`?({TABLE_ALT})`?\b", re.IGNORECASE)

# MyBatis 注解里常见的整体改写语句（@Update("...") 已在上面覆盖，这里补 LambdaUpdate 的链式写法）
LAMBDA_UPDATE_HINT = re.compile(r"new\s+LambdaUpdateWrapper<\s*AuditLogRecord\s*>", re.IGNORECASE)


def strip_comments(text: str) -> str:
    """把注释替换成等长空白，保留行列位置，避免注释里的示例语句误报。"""
    out = list(text)
    i, n = 0, len(text)
    in_line = False
    in_block = False
    in_str = None
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_line:
            if ch == "\n":
                in_line = False
            else:
                out[i] = " "
            i += 1
            continue
        if in_block:
            if ch == "*" and nxt == "/":
                out[i] = out[i + 1] = " "
                in_block = False
                i += 2
                continue
            if ch != "\n":
                out[i] = " "
            i += 1
            continue
        if in_str:
            if ch == "\\":
                i += 2
                continue
            if ch == in_str:
                in_str = None
            i += 1
            continue
        if ch in ("'", '"'):
            in_str = ch
            i += 1
            continue
        if ch == "-" and nxt == "-":
            in_line = True
            out[i] = out[i + 1] = " "
            i += 2
            continue
        if ch == "/" and nxt == "*":
            in_block = True
            out[i] = out[i + 1] = " "
            i += 2
            continue
        i += 1
    return "".join(out)


def line_of(text: str, index: int) -> int:
    return text.count("\n", 0, index) + 1


def snippet(text: str, index: int, width: int = 110) -> str:
    end = text.find("\n", index)
    end = len(text) if end == -1 else end
    raw = " ".join(text[index:end].split())
    return raw[:width]


def iter_files(scan_dir: str):
    for root, dirs, files in os.walk(scan_dir):
        dirs[:] = [d for d in dirs if d not in ("target", "node_modules", "dist", ".git")]
        for name in files:
            if name.endswith(SCAN_SUFFIXES):
                yield os.path.join(root, name)


def main() -> int:
    parser = argparse.ArgumentParser(description="审计日志追加写门禁")
    parser.add_argument("--root", default=None, help="仓库根（默认取脚本上两级）")
    parser.add_argument("--scan-dir", default=None, help="只扫描指定目录（自测用）")
    parser.add_argument("--json", action="store_true", help="输出 JSON")
    args = parser.parse_args()

    repo_root = args.root or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    scan_dirs = [args.scan_dir] if args.scan_dir else [
        os.path.join(repo_root, "whale-backend"),
        os.path.join(repo_root, "docs"),
        os.path.join(repo_root, "scripts"),
    ]

    violations = []
    hints = []
    scanned = 0
    for scan_dir in scan_dirs:
        if not os.path.isdir(scan_dir):
            continue
        for path in iter_files(scan_dir):
            scanned += 1
            try:
                with open(path, "r", encoding="utf-8", errors="replace") as fh:
                    raw = fh.read()
            except OSError as exc:
                hints.append({"file": path, "line": 0, "kind": "READ-ERROR", "snippet": str(exc)})
                continue
            text = strip_comments(raw)
            rel = os.path.relpath(path, repo_root).replace("\\", "/")
            base = os.path.basename(path)
            is_bootstrap = base in BOOTSTRAP_FILES
            allow_reason = ALLOWED_MUTATION_SCRIPTS.get(base)

            for kind, pattern in MUTATION_PATTERNS:
                for match in pattern.finditer(text):
                    entry = {
                        "file": rel,
                        "line": line_of(text, match.start()),
                        "kind": kind,
                        "table": match.group(1),
                        "snippet": snippet(raw, match.start()),
                    }
                    if allow_reason:
                        entry["reason"] = allow_reason
                        hints.append(entry)
                    else:
                        violations.append(entry)

            for match in DROP_PATTERN.finditer(text):
                entry = {
                    "file": rel,
                    "line": line_of(text, match.start()),
                    "kind": "DROP",
                    "table": match.group(2),
                    "snippet": snippet(raw, match.start()),
                }
                if allow_reason:
                    entry["reason"] = allow_reason
                    hints.append(entry)
                elif is_bootstrap and match.group(1):
                    hints.append(entry)
                else:
                    violations.append(entry)

            for match in LAMBDA_UPDATE_HINT.finditer(text):
                violations.append({
                    "file": rel,
                    "line": line_of(text, match.start()),
                    "kind": "LAMBDA-UPDATE",
                    "table": "audit_log",
                    "snippet": snippet(raw, match.start()),
                })

    if args.json:
        print(json.dumps({
            "scanned_files": scanned,
            "protected_tables": list(PROTECTED_TABLES),
            "violations": violations,
            "hints": hints,
        }, ensure_ascii=False, indent=2))
        return 1 if violations else 0

    print(f"扫描 {scanned} 个文件（受保护表 {len(PROTECTED_TABLES)} 张）")
    for item in violations:
        print(f"  FAIL {item['file']}:{item['line']} [{item['kind']} {item['table']}] {item['snippet']}")
    for item in hints:
        reason = f"  # {item['reason']}" if item.get("reason") else ""
        print(f"  INFO {item['file']}:{item['line']} [{item['kind']} {item['table']}] {item['snippet']}{reason}")
    if violations:
        print(f"FAIL：发现 {len(violations)} 处对审计日志的改写/删除，审计表必须 append-only")
        return 1
    print("OK：未发现对审计类日志表的 UPDATE / DELETE / REPLACE / TRUNCATE")
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
