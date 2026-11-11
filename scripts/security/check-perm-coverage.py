"""权限码覆盖检查：Java 里 `@RequirePermission("sys:...")` 引用的编码，库里是否真的存在。

用法：
    # 1) 从库里导出权限编码（一列，无表头）
    mysql -N -B -h127.0.0.1 -uroot -p bluewhale_auth \
        -e "SELECT perm_code FROM sys_permission WHERE deleted = 0" > /tmp/perm_codes.txt
    # 2) 比对
    python scripts/security/check-perm-coverage.py --db-codes /tmp/perm_codes.txt

退出码：0 = 无缺失；1 = 有缺失（代码里有、库里没有 → 该接口除超管外一律 403）。

为什么需要它：`@RequirePermission` 只是个字符串，拼错或漏建种子时**编译、启动、单测全绿**，
只有真人点那个按钮才会 403。2026-11-09 用这个思路查出 `sys:pm:transition:edit`（应为 `:add`）
与一批从未执行过种子的 CRM 权限（invoice / ticket / campaign / import 共 28 项）。

注意：只扫 `src/main`，测试里的假编码（`sys:demo:write` 之类）不参与判定。
"""
from __future__ import annotations

import argparse
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BACKEND = os.path.join(ROOT, "whale-backend")
PATTERN = re.compile(r'"(sys:[a-zA-Z0-9:_\-]+)"')


def load_db(path: str) -> set[str]:
    codes: set[str] = set()
    with io.open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.strip()
            if line.startswith("sys:") and not line.startswith("sys:_"):
                codes.add(line)
    return codes


def scan_sources() -> dict[str, set[str]]:
    found: dict[str, set[str]] = {}
    for root, dirs, files in os.walk(BACKEND):
        dirs[:] = [d for d in dirs if d not in ("target", "node_modules", ".git")]
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(root, name)
            norm = path.replace(os.sep, "/")
            if "/src/test/" in norm:
                continue
            text = io.open(path, encoding="utf-8", errors="replace").read()
            for m in PATTERN.finditer(text):
                found.setdefault(m.group(1), set()).add(os.path.relpath(path, ROOT))
    return found


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db-codes", required=True, help="库内权限编码清单（一行一个）")
    args = ap.parse_args()

    db = load_db(args.db_codes)
    if not db:
        print(f"!! {args.db_codes} 里没有读到任何 sys: 编码，检查导出命令是否带 -N")
        return 2

    used = scan_sources()
    missing = sorted(c for c in used if c not in db)
    print(f"库内 {len(db)} 个编码；代码引用 {len(used)} 个；缺失 {len(missing)} 个")
    for code in missing:
        files = sorted(used[code])
        print(f"  MISSING {code}  <- {files[0]}{'' if len(files) == 1 else f' (+{len(files) - 1})'}")
    return 1 if missing else 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
