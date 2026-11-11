"""角色码覆盖检查：Java 里硬编码的角色码，迁移 SQL 里是否真的播种过。

用法：
    python scripts/security/check-role-coverage.py            # 人读报表
    python scripts/security/check-role-coverage.py --json     # 机器读（CI 用）
    python scripts/security/check-role-coverage.py --sql-dir <目录> --java-dir <目录>

退出码：0 = 无缺失；1 = 有缺失（代码判定某个角色，但库里永远不会有该角色）。

为什么需要它（2026-11-11 实测事故）：
    库里 sys_role 共 55 个角色，`tenant_admin` **一条都没有**，但：
      · migration-5.65 / 5.67 / 5.68 / 5.70 / 5.71 / 5.73 / 5.74 / 5.75 / 5.76 /
        5.77 / 5.78 … 以及 V4 / V5 / V6 / V8 的授权段全写成
        `WHERE r.role_code IN ('tenant_admin')` —— JOIN 结果为空，整段授权静默不生效；
      · MallActor / LmsActor / PartyActor / CrmActor / PdmDocRoles 用它做「全量数据
        范围」判定 —— 角色不存在，除硬旁路的 super_admin 外无人能拿到全量；
      · mall_admin / mall_operator 同样没建 —— 商城发货、改价、售后审核、经营报表
        对非超管永久不可用。
    这些全是**静默失败**：编译、启动、单测、门禁全绿，只有真人点按钮才会 403 或
    「只能看到自己的数据」。本脚本把「角色只被引用、从未播种」变成可重复执行的检查。

判定口径：
    ERROR  代码引用的角色码，没有任何迁移脚本 `INSERT INTO sys_role` 播种过
    WARN   代码引用的角色码只在 db/init.sql 里播种（存量库不会执行 init.sql，
           V1 baseline 只建表；新环境才有——属于「只在全新库可用」的隐患）

采集范围：
    · Java  src/main 下：`*_ROLE(S) = Set.of(...)`、`*_ROLE = "..."`、
            `roles.contains("...")`、`hasRole(...)` / `hasAnyRole(...)`
    · SQL   全部 *.sql（排除 target/），只取 `INSERT INTO sys_role` 语句内的
            单引号小写字面量（role_code 列；role_name/description 是中文、status 是数字，
            不会误采）
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_SQL_DIR = os.path.join(ROOT, "whale-backend")
DEFAULT_JAVA_DIR = os.path.join(ROOT, "whale-backend")

SKIP_DIRS = {"target", "node_modules", "build", "dist", ".git"}

# 角色码字面量：纯小写字母开头的下划线标识符
CODE = r"([a-z][a-z0-9_]{2,63})"

# 代码侧的四种引用形态（只在明确的角色语义上下文取词）
JAVA_PATTERNS = [
    # private static final Set<String> MANAGE_ROLES = Set.of("tenant_admin", "mall_admin");
    ("const-set", re.compile(r"\b\w*ROLES?\s*=\s*Set\.of\(([^)]*)\)")),
    # private static final String MALL_OPERATOR_ROLE = "mall_operator";
    ("const", re.compile(r"\b\w*ROLE\s*=\s*\"(" + CODE[1:-1] + r")\"")),
    # roles.contains("tenant_admin")
    ("contains", re.compile(r"\broles\.contains\(\s*\"(" + CODE[1:-1] + r")\"\s*\)")),
    # hasRole("a", "b") / hasAnyRole(...)
    ("hasRole", re.compile(r"\bhas(?:Any)?Role\(\s*([^)]*)\)")),
]

STRING_LITERAL = re.compile(r'"([^"]*)"')

# SQL：插入角色语句块
SQL_INSERT_ROLE = re.compile(
    r"INSERT\s+INTO\s+sys_role\s*\([^)]*\)(.*?);", re.IGNORECASE | re.DOTALL
)
SQL_ROLE_CODE_LITERAL = re.compile(r"'([a-z][a-z0-9_]{2,63})'")
# 语句块里可能出现的非角色码小写字面量（排序/状态/租户类型等），显式排除
SQL_NON_ROLE_LITERALS = {"tenant", "platform", "null", "true", "false", "default"}


def iter_files(base: str, suffixes: tuple[str, ...]):
    for root, dirs, files in os.walk(base):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            if name.endswith(suffixes):
                yield os.path.join(root, name)


def rel_of(path: str) -> str:
    """仓库内路径取相对路径；仓库外（如临时负向验证目录）退回绝对路径。"""
    try:
        return os.path.relpath(path, ROOT).replace(os.sep, "/")
    except ValueError:
        return path.replace(os.sep, "/")


def scan_sql(sql_dir: str) -> tuple[dict[str, set[str]], dict[str, set[str]]]:
    """返回 (migration 播种的角色码, init.sql 播种的角色码)，值均为来源文件集合。"""
    migrated: dict[str, set[str]] = {}
    from_init: dict[str, set[str]] = {}
    for path in iter_files(sql_dir, (".sql",)):
        text = io.open(path, encoding="utf-8", errors="replace").read()
        rel = rel_of(path)
        is_init = rel.endswith("/db/init.sql")
        for block in SQL_INSERT_ROLE.finditer(text):
            for m in SQL_ROLE_CODE_LITERAL.finditer(block.group(1)):
                code = m.group(1)
                if code in SQL_NON_ROLE_LITERALS or code.startswith("sys_"):
                    continue
                target = from_init if is_init else migrated
                target.setdefault(code, set()).add(rel)
    return migrated, from_init


def extract_codes(fragment: str) -> list[str]:
    """从捕获片段取角色码。

    片段有两种形态：
      · 带引号的列表（`"tenant_admin", "mall_admin"`，来自 Set.of / hasRole）→ 取引号内；
      · 已是裸码（`mall_operator`，来自单常量 / roles.contains）→ 直接返回。
    两种都要支持，否则单值常量会被二次按引号提取而静默漏检。
    """
    if re.fullmatch(r"[a-z][a-z0-9_]{2,63}", fragment.strip()):
        return [fragment.strip()]
    out = []
    for m in STRING_LITERAL.finditer(fragment):
        value = m.group(1)
        if re.fullmatch(r"[a-z][a-z0-9_]{2,63}", value):
            out.append(value)
    return out


def scan_java(java_dir: str) -> dict[str, set[str]]:
    """返回 角色码 -> 引用位置集合（只扫 src/main，测试里的假角色不参与判定）。"""
    used: dict[str, set[str]] = {}
    for path in iter_files(java_dir, (".java",)):
        norm = path.replace(os.sep, "/")
        if "/src/test/" in norm:
            continue
        text = io.open(path, encoding="utf-8", errors="replace").read()
        rel = rel_of(path)
        for _kind, pattern in JAVA_PATTERNS:
            for m in pattern.finditer(text):
                for code in extract_codes(m.group(1)):
                    used.setdefault(code, set()).add(rel)
    return used


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--sql-dir", default=DEFAULT_SQL_DIR)
    ap.add_argument("--java-dir", default=DEFAULT_JAVA_DIR)
    ap.add_argument("--json", action="store_true", help="输出 JSON（CI 用）")
    args = ap.parse_args()

    migrated, from_init = scan_sql(args.sql_dir)
    used = scan_java(args.java_dir)

    # 播种集合只认「迁移脚本」；init.sql 播种的单独记为 WARN（存量库不跑 init.sql）
    missing: dict[str, set[str]] = {}
    init_only: dict[str, set[str]] = {}
    for code, refs in used.items():
        if code in migrated:
            continue
        if code in from_init:
            init_only[code] = refs
        else:
            missing[code] = refs

    if args.json:
        print(json.dumps(
            {
                "seeded": sorted(migrated),
                "referenced": sorted(used),
                "missing": {k: sorted(v) for k, v in sorted(missing.items())},
                "init_only": {k: sorted(v) for k, v in sorted(init_only.items())},
            },
            ensure_ascii=False,
            indent=2,
        ))
        return 1 if missing else 0

    print(f"迁移脚本播种角色 {len(migrated)} 个；代码引用角色 {len(used)} 个；"
          f"缺失 {len(missing)} 个；仅 init.sql 播种 {len(init_only)} 个")
    for code in sorted(missing):
        refs = sorted(used[code])
        print(f"  MISSING {code}  <- {refs[0]}{'' if len(refs) == 1 else f' (+{len(refs) - 1})'}")
    for code in sorted(init_only):
        refs = sorted(used[code])
        print(f"  INIT-ONLY {code}  <- {refs[0]}（只在 db/init.sql 播种，存量库不会有）")
    if not missing and not init_only:
        print("OK：代码引用的角色码全部有迁移脚本播种")
    return 1 if missing else 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
