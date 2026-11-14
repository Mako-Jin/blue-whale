#!/usr/bin/env python3
"""前端菜单/权限码 ↔ 后端种子 静态门禁。

背景：前端的菜单与按钮级权限完全由后端数据驱动，但两侧没有编译期约束——

1. admin 动态路由：``/auth/me`` 返回 ``sys_menu.component`` 字符串，前端
   ``dynamicRoutes.tsx`` 的 ``COMPONENT_MAP`` 按契约键（或路径末段）解析成页面组件；
   解析失败不报错，只渲染「页面未配置」404 占位——菜单配了但键写错，只会静默白屏。
2. 按钮级权限：``AuthButton perm="sys:xxx"`` / ``hasPermission('sys:xxx')`` 判断的
   权限码必须出现在后端种子（``sys_permission.perm_code``、``sys_menu.permission_code``
   或控制器 ``@RequirePermission``）里，否则没有任何角色能持有它，按钮永远不渲染。

本脚本把两侧的口径对齐：

- 检查 A：每个 ``sys_menu.component`` 非空值必须能被 ``COMPONENT_MAP`` 解析
  （复刻 ``resolveComponent`` 的「契约键直连 / 路径末段回退」两条规则）；
- 检查 B：前端引用的每个权限码必须存在于后端种子全集；
- 检查 C（WARN）：``COMPONENT_MAP`` 中从未被任何菜单种子使用的键（疑似死代码）。

用法：
    python scripts/check-frontend-perm-mapping.py            # 全量
    python scripts/check-frontend-perm-mapping.py --verbose  # 逐条打印 OK

退出码：0 通过（可能有 WARN）；1 存在 FAIL。
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

# ---------------------------------------------------------------- 常量

REPO_ROOT = Path(__file__).resolve().parent.parent

BACKEND_DIR = REPO_ROOT / "whale-backend"

#: auth 种子 SQL 目录（sys_menu / sys_permission 唯一来源）
AUTH_DB_DIR = (
    BACKEND_DIR
    / "whale-services"
    / "whale-service-auth"
    / "whale-service-auth-starter"
    / "src"
    / "main"
    / "resources"
    / "db"
)

FRONTEND_DIR = REPO_ROOT / "whale-frontend"

#: 前端契约键所在文件
DYNAMIC_ROUTES = FRONTEND_DIR / "admin" / "src" / "router" / "dynamicRoutes.tsx"

#: 基座子应用注册表（qiankun activeRule 为权威口径，比 routes.tsx 的挂载点更全）
PLATFORM_QIANKUN = FRONTEND_DIR / "platform-apps" / "whale-platform" / "src" / "qiankun" / "apps.ts"

#: 刻意引用不存在权限码的白名单（演示「无权限按钮不渲染」的教学样例）：
#: 键为仓库相对路径片段，值为允许不存在的权限码
INTENTIONAL_MISSING_CODES = {
    "admin/src/pages/demo/ComponentsDemo.tsx": {"sys:demo:not-exist"},
}

#: 已知「菜单在、前端没挂」的基线（知识助手为 Wave 5.47 遗留模块，
#: whale-agent 尚未注册进基座 qiankun，见 docs/plans/whale-platform-backlog）。
#: 门禁对基线内菜单只报 WARN；新增未挂载菜单仍 FAIL。
KNOWN_UNROUTED_MENUS = {
    ("9", "/agent"),
    ("10", "/agent/chat"),
}

#: 已知「后端菜单已定义、admin 前端页面尚未实现」的基线。
#:
#: 历史：2026-11-13 曾登记 7 个权限中心扩展菜单（V11/V19/V20/V21/V22），2026-11-14
#: admin 已补齐全部页面（brand/user-profile/user-group/perm-strategy/role-constraint/
#: session/login-history + V29 新增的 oauth-client），基线收敛为空——条目删除后门禁
#: 自动恢复 FAIL。此后新增「只有菜单种子没有页面」的菜单时在此登记。
KNOWN_UNROUTED_PATHS: dict[str, str] = {}

#: 前端扫描排除目录
FRONTEND_EXCLUDES = {"node_modules", "dist", "build", ".turbo", "coverage"}

#: 前端源码后缀
FRONTEND_SUFFIXES = {".ts", ".tsx", ".js", ".jsx"}

#: 权限码形态：小写段、冒号分隔、至少两段
PERM_CODE_RE = re.compile(r"^[a-z][a-z0-9-]*(?::[a-z0-9][a-z0-9._-]*)+$")


@dataclass
class Finding:
    """一条核对结果。"""

    level: str
    message: str


@dataclass
class MenuSeed:
    """一条菜单种子。"""

    menu_id: str
    name: str
    path: str | None
    component: str | None
    permission_code: str | None
    menu_type: str | None
    source: Path


# ---------------------------------------------------------------- 通用工具


def strip_line_comments(source: str) -> str:
    """去掉 SQL/TS 源码里的注释（--、//、/* */），保留字符串字面量。

    :param source: 原始源码
    :return: 注释被替换为等长空白的源码
    """
    result: list[str] = []
    i = 0
    length = len(source)
    while i < length:
        char = source[i]
        if char in ("'", '"', "`"):
            quote = char
            result.append(char)
            i += 1
            while i < length:
                result.append(source[i])
                if source[i] == "\\":
                    if i + 1 < length:
                        result.append(source[i + 1])
                        i += 2
                        continue
                elif source[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        if char == "-" and i + 1 < length and source[i + 1] == "-":
            while i < length and source[i] != "\n":
                result.append(" ")
                i += 1
            continue
        if char == "/" and i + 1 < length and source[i + 1] == "/":
            while i < length and source[i] != "\n":
                result.append(" ")
                i += 1
            continue
        if char == "/" and i + 1 < length and source[i + 1] == "*":
            result.append("  ")
            i += 2
            while i + 1 < length and not (source[i] == "*" and source[i + 1] == "/"):
                result.append("\n" if source[i] == "\n" else " ")
                i += 1
            result.append("  ")
            i += 2
            continue
        result.append(char)
        i += 1
    return "".join(result)


def split_top_level(text: str) -> list[str]:
    """按顶层逗号切分（引号内的逗号、括号内的逗号不切）。

    :param text: 待切分文本
    :return: 切分后的片段列表
    """
    parts: list[str] = []
    buf: list[str] = []
    depth = 0
    i = 0
    length = len(text)
    while i < length:
        char = text[i]
        if char in ("'", '"'):
            quote = char
            buf.append(char)
            i += 1
            while i < length:
                buf.append(text[i])
                if text[i] == "\\" and i + 1 < length:
                    buf.append(text[i + 1])
                    i += 2
                    continue
                if text[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        if char == "," and depth == 0:
            parts.append("".join(buf))
            buf = []
            i += 1
            continue
        buf.append(char)
        i += 1
    tail = "".join(buf)
    if tail.strip():
        parts.append(tail)
    return parts


def literal(value: str) -> str | None:
    """把 SQL 值表达式转成字符串：带引号取内容，NULL 返回 ``None``。

    :param value: 单个值表达式
    :return: 字符串值或 ``None``
    """
    text = value.strip()
    match = re.fullmatch(r"'((?:[^']|'')*)'", text)
    if match:
        return match.group(1).replace("''", "'")
    if text.upper() == "NULL" or not text:
        return None
    return text  # 数字等裸值


# ---------------------------------------------------------------- SQL 种子解析


def parse_insert_tuples(sql: str, table: str) -> list[tuple[list[str], list[list[str]]]]:
    """解析 SQL 里对某张表的全部 INSERT，返回 (列名列表, 值元组列表)。

    兼容两种写法：``VALUES (...), (...)`` 多行、迁移脚本惯用的
    ``SELECT ... FROM DUAL``。值统一用顶层逗号切分。

    :param sql: 已剥注释的 SQL 文本
    :param table: 表名（容忍反引号）
    :return: [(列名, [值元组]), ...]
    """
    results: list[tuple[list[str], list[list[str]]]] = []
    pattern = re.compile(
        r"INSERT\s+INTO\s+`?" + re.escape(table) + r"`?\s*\(([^)]*)\)\s*(VALUES|SELECT)\b",
        re.IGNORECASE,
    )
    for match in pattern.finditer(sql):
        columns = [col.strip().strip("`") for col in match.group(1).split(",")]
        start = match.end()
        # 语句到顶层分号为止（SELECT 风格分号在 WHERE NOT EXISTS 之后）
        end = sql.find(";", start)
        body = sql[start : end if end != -1 else len(sql)]
        if match.group(2).upper() == "SELECT":
            # 去掉 FROM DUAL 及其后的 WHERE 子句，只留 SELECT 列表
            body = re.split(r"\bFROM\b", body, maxsplit=1, flags=re.IGNORECASE)[0]
            body = body.strip().rstrip(",")
            body = f"({body})" if not body.startswith("(") else body
            tuples = [[v.strip() for v in split_top_level(body[1:-1])]]
        else:
            tuples = []
            for raw in re.findall(r"\(([^()]*)\)", body):
                tuples.append([v.strip() for v in split_top_level(raw)])
        results.append((columns, tuples))
    return results


def collect_auth_seeds() -> tuple[list[MenuSeed], set[str]]:
    """收集 auth 种子：菜单（component/permission_code）与权限码全集。

    :return: (菜单种子列表, 权限码集合)
    """
    menus: list[MenuSeed] = []
    codes: set[str] = set()
    for sql_file in sorted(AUTH_DB_DIR.rglob("*.sql")):
        sql = strip_line_comments(sql_file.read_text(encoding="utf-8", errors="replace"))
        for columns, tuples in parse_insert_tuples(sql, "sys_menu"):
            idx_comp = columns.index("component") if "component" in columns else None
            idx_perm = columns.index("permission_code") if "permission_code" in columns else None
            idx_id = columns.index("id") if "id" in columns else None
            idx_name = columns.index("name") if "name" in columns else None
            idx_path = columns.index("path") if "path" in columns else None
            idx_type = columns.index("menu_type") if "menu_type" in columns else None
            for values in tuples:
                if len(values) != len(columns):
                    continue
                # 判定「拷贝型 INSERT ... SELECT」（按租户复制既有菜单）时只看**事实列**：
                # name/path/component/permission_code/menu_type。这些列是别名或函数表达式，
                # 说明该行是从别的行复制来的，不携带新的「菜单是什么」的事实 → 跳过。
                #
                # id 列不参与判定：项目红线要求新菜单主键用 MD5 派生
                # （`CAST(CONV(SUBSTR(MD5(...))) AS UNSIGNED)`），若把 id 也算进去，
                # 越是按规范写的迁移越会被整行丢弃——门禁反而对最该覆盖的脚本失明。
                fact_indexes = [
                    idx for idx in (idx_name, idx_path, idx_comp, idx_perm, idx_type)
                    if idx is not None
                ]
                if any(
                    "(" in values[idx] or re.match(r"^[A-Za-z_]\w*\.\w+", values[idx].strip())
                    for idx in fact_indexes
                ):
                    continue

                def at(idx: int | None) -> str | None:
                    return literal(values[idx]) if idx is not None and idx < len(values) else None

                component = at(idx_comp)
                perm = at(idx_perm)
                menus.append(
                    MenuSeed(
                        menu_id=at(idx_id) or "?",
                        name=at(idx_name) or "?",
                        path=at(idx_path),
                        component=component,
                        permission_code=perm,
                        menu_type=at(idx_type),
                        source=sql_file,
                    )
                )
                if perm:
                    codes.add(perm)
        for columns, tuples in parse_insert_tuples(sql, "sys_permission"):
            if "perm_code" not in columns:
                continue
            idx = columns.index("perm_code")
            for values in tuples:
                if idx < len(values):
                    code = literal(values[idx])
                    if code:
                        codes.add(code)
    return menus, codes


def collect_require_permission_codes() -> set[str]:
    """收集后端 ``@RequirePermission`` 注解里的全部权限码字面量。

    :return: 权限码集合
    """
    codes: set[str] = set()
    pattern = re.compile(r"@RequirePermission\b\s*(\([^)]*\))?")
    literal_re = re.compile(r'"([^"]*)"')
    for java_file in BACKEND_DIR.rglob("*.java"):
        text = java_file.read_text(encoding="utf-8", errors="replace")
        text = strip_java_comments(text)
        for match in pattern.finditer(text):
            args = match.group(1)
            if not args:
                continue
            codes.update(literal_re.findall(args))
    return codes


def strip_java_comments(source: str) -> str:
    """去掉 Java 注释（``//`` 与 ``/* */``），保留字符串与字符字面量。

    类注释里写「刻意不加 ``@RequirePermission``」这类说明不能当成真注解。

    :param source: 原始源码
    :return: 注释替换为空白的源码
    """
    result: list[str] = []
    i = 0
    length = len(source)
    while i < length:
        char = source[i]
        if char == '"' or char == "'":
            quote = char
            result.append(char)
            i += 1
            while i < length:
                result.append(source[i])
                if source[i] == "\\":
                    if i + 1 < length:
                        result.append(source[i + 1])
                        i += 2
                        continue
                elif source[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        if char == "/" and i + 1 < length and source[i + 1] == "/":
            while i < length and source[i] != "\n":
                result.append(" ")
                i += 1
            continue
        if char == "/" and i + 1 < length and source[i + 1] == "*":
            result.append("  ")
            i += 2
            while i + 1 < length and not (source[i] == "*" and source[i + 1] == "/"):
                result.append("\n" if source[i] == "\n" else " ")
                i += 1
            result.append("  ")
            i += 2
            continue
        result.append(char)
        i += 1
    return "".join(result)


# ---------------------------------------------------------------- 前端解析


def collect_component_keys() -> dict[str, Path]:
    """从 ``dynamicRoutes.tsx`` 解析 ``COMPONENT_MAP`` 的全部契约键。

    :return: {契约键: 定义文件}
    """
    text = DYNAMIC_ROUTES.read_text(encoding="utf-8", errors="replace")
    text = strip_line_comments(text)
    match = re.search(r"COMPONENT_MAP[^=]*=\s*\{", text)
    if not match:
        return {}
    # 从 { 开始做括号配对（容忍字符串内的花括号——本文件键值均为标识符，够用）
    start = match.end() - 1
    depth = 0
    end = start
    for i in range(start, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                end = i
                break
    body = text[start + 1 : end]
    keys: dict[str, Path] = {}
    for line in body.splitlines():
        key_match = re.match(r"^\s*(?:'([^']+)'|([A-Za-z_$][\w$]*))\s*:", line)
        if key_match:
            key = key_match.group(1) or key_match.group(2)
            keys[key] = DYNAMIC_ROUTES
    return keys


def resolve_component(component: str, keys: dict[str, Path]) -> bool:
    """复刻 ``resolveComponent``：契约键直连或路径末段回退。

    :param component: 菜单 ``component`` 列的值
    :param keys: 契约键集合
    :return: 可解析返回 ``True``
    """
    normalized = component.strip().strip("/").removesuffix("/index")
    if normalized in keys:
        return True
    return any(seg in keys for seg in reversed(normalized.split("/")) if seg)


def collect_subapp_prefixes() -> set[str]:
    """从基座 qiankun 注册表解析子应用激活前缀（``activeRule: '/crm'``）。

    :return: 前缀集合（如 ``{"party", "lms", "crm"}``）
    """
    text = PLATFORM_QIANKUN.read_text(encoding="utf-8", errors="replace")
    text = strip_line_comments(text)
    return {
        rule.strip("/")
        for rule in re.findall(r"activeRule:\s*[\"']([^\"']+)[\"']", text)
        if rule.strip("/")
    }


def resolve_menu_target(menu: MenuSeed, keys: dict[str, Path], subapps: set[str]) -> str | None:
    """判断菜单由哪个前端承载。

    :param menu: 菜单种子
    :param keys: admin 契约键集合
    :param subapps: 基座子应用前缀集合
    :return: ``"admin"`` / ``"subapp"`` / ``None``（无法解析）
    """
    if menu.component and resolve_component(menu.component, keys):
        return "admin"
    first = (menu.path or "").strip("/").split("/")[0]
    if first and first in subapps:
        return "subapp"
    return None


def collect_frontend_perm_codes() -> dict[str, list[str]]:
    """收集前端源码里权限判断上下文中引用的权限码。

    只在权限判断上下文里取字面量（``perm=`` / ``anyOf=`` / ``hasPermission(`` /
    ``hasAny(`` / ``has(`` / ``permission:`` / ``includes(``），且要求符合权限码
    形态（小写冒号分段）——避免把 ``errorKeys.has(...)`` 这类 Set 操作误收。

    :return: {权限码: [引用位置 "file:line"]}
    """
    refs: dict[str, list[str]] = {}
    contexts = [
        re.compile(r"\bperm\s*=\s*[\"']([^\"']+)[\"']"),
        re.compile(r"\bhasPermission\(\s*[\"']([^\"']+)[\"']"),
        re.compile(r"\bhasAny\(\s*[\"']([^\"']+)[\"']"),
        re.compile(r"\.has\(\s*[\"']([^\"']+)[\"']"),
        re.compile(r"\bpermission\s*:\s*[\"']([^\"']+)[\"']"),
        re.compile(r"\bincludes\(\s*[\"']([^\"']+)[\"']"),
    ]
    any_of_re = re.compile(r"\banyOf\s*=\s*\{\[([^\]]*)\]\}")
    literal_re = re.compile(r"[\"']([^\"']+)[\"']")
    for path in sorted(iter_frontend_sources()):
        text = path.read_text(encoding="utf-8", errors="replace")
        text = strip_line_comments(text)

        def add(code: str, line_no: int) -> None:
            if PERM_CODE_RE.fullmatch(code):
                refs.setdefault(code, []).append(f"{path.relative_to(REPO_ROOT)}:{line_no}")

        for pattern in contexts:
            for match in pattern.finditer(text):
                line_no = text.count("\n", 0, match.start()) + 1
                add(match.group(1), line_no)
        for match in any_of_re.finditer(text):
            line_no = text.count("\n", 0, match.start()) + 1
            for lit in literal_re.findall(match.group(1)):
                add(lit, line_no)
    return refs


def iter_frontend_sources() -> list[Path]:
    """枚举前端源码文件（排除 node_modules/dist 等构建产物）。

    :return: 文件列表
    """
    files: list[Path] = []
    for path in FRONTEND_DIR.rglob("*"):
        if path.suffix not in FRONTEND_SUFFIXES:
            continue
        if any(part in FRONTEND_EXCLUDES for part in path.parts):
            continue
        if path.name.endswith((".test.ts", ".test.tsx", ".spec.ts", ".d.ts")):
            continue
        files.append(path)
    return files


# ---------------------------------------------------------------- 主流程


def main() -> int:
    """执行全量核对。

    :return: 退出码：0 通过；1 存在 FAIL
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verbose", action="store_true", help="逐条打印 OK")
    args = parser.parse_args()

    findings: list[Finding] = []
    ok_count = 0

    menus, seeded_codes = collect_auth_seeds()
    seeded_codes |= collect_require_permission_codes()
    keys = collect_component_keys()
    subapps = collect_subapp_prefixes()
    frontend_refs = collect_frontend_perm_codes()

    if not keys:
        findings.append(Finding("FAIL", "无法从 dynamicRoutes.tsx 解析 COMPONENT_MAP 契约键"))
    if not menus:
        findings.append(Finding("FAIL", "未解析到任何 sys_menu 菜单种子"))
    if not seeded_codes:
        findings.append(Finding("FAIL", "未解析到任何权限码种子（sys_permission/sys_menu/@RequirePermission）"))

    # ---- 检查 A：菜单必须能落到前端——admin 契约键、基座子应用前缀或目录型
    for menu in menus:
        if (menu.menu_type or "").upper() == "DIR":
            # 目录只做分组，动态路由只递归子节点不生成自身路由
            ok_count += 1
            if args.verbose:
                print(f"OK menu-dir    menu#{menu.menu_id} {menu.name}: {menu.path}")
            continue
        target = resolve_menu_target(menu, keys, subapps)
        if target == "admin":
            ok_count += 1
            if args.verbose:
                print(f"OK menu-admin  menu#{menu.menu_id} {menu.name}: {menu.component}")
        elif target == "subapp":
            ok_count += 1
            if args.verbose:
                print(
                    f"OK menu-subapp menu#{menu.menu_id} {menu.name}: "
                    f"{menu.path} (基座子应用承载, component='{menu.component}')"
                )
        elif (menu.menu_id, menu.path or "") in KNOWN_UNROUTED_MENUS:
            findings.append(
                Finding(
                    "WARN",
                    f"菜单 #{menu.menu_id}「{menu.name}」path={menu.path} 未挂载到任何前端"
                    f"（已知基线，whale-agent 待接入基座，{menu.source.name}）",
                )
            )
        elif (menu.path or "") in KNOWN_UNROUTED_PATHS:
            findings.append(
                Finding(
                    "WARN",
                    f"菜单 #{menu.menu_id}「{menu.name}」path={menu.path} 未挂载到任何前端"
                    f"（已知基线：admin 页面待补，{KNOWN_UNROUTED_PATHS[menu.path]}）",
                )
            )
        else:
            hint = f"component='{menu.component}'" if menu.component else "component 为空"
            findings.append(
                Finding(
                    "FAIL",
                    f"菜单 #{menu.menu_id}「{menu.name}」path={menu.path} {hint} "
                    f"既不是 admin 契约键也不在基座子应用前缀里（{menu.source.name}）"
                    "→ 运行时显示「页面未配置」占位或路由 404",
                )
            )

    # ---- 检查 B：前端引用的权限码必须存在于后端种子
    for code, locations in sorted(frontend_refs.items()):
        if code in seeded_codes:
            ok_count += 1
            if args.verbose:
                print(f"OK perm-code  {code} ({len(locations)} 处引用)")
            continue
        rel = locations[0].split(":")[0].replace("\\", "/")
        allowed = any(
            fragment in rel and code in codes
            for fragment, codes in INTENTIONAL_MISSING_CODES.items()
        )
        if allowed:
            ok_count += 1
            if args.verbose:
                print(f"OK perm-code  {code} (白名单: 演示刻意引用)")
        else:
            findings.append(
                Finding(
                    "FAIL",
                    f"前端引用权限码 '{code}' 不在后端种子（{locations[0]} 等 "
                    f"{len(locations)} 处）→ 任何角色都无法持有，按钮/入口永不渲染",
                )
            )

    # ---- 检查 C：COMPONENT_MAP 死键（WARN）
    used_components = {menu.component for menu in menus if menu.component}
    for key in keys:
        hits = [c for c in used_components if _resolves_to(c, key)]
        if not hits:
            findings.append(
                Finding("WARN", f"契约键 '{key}' 未被任何菜单种子 component 命中（疑似死代码）")
            )
        else:
            ok_count += 1

    print()
    print("=" * 64)
    print(
        f"菜单种子 {len(menus)} 条 / 契约键 {len(keys)} 个 / "
        f"后端权限码 {len(seeded_codes)} 个 / 前端引用权限码 {len(frontend_refs)} 个"
    )
    fails = [f for f in findings if f.level == "FAIL"]
    warns = [f for f in findings if f.level == "WARN"]
    for f in findings:
        print(f"[{f.level}] {f.message}")
    print(f"通过 {ok_count} 项，FAIL {len(fails)} 项，WARN {len(warns)} 项")
    print("=" * 64)
    return 1 if fails else 0


def _resolves_to(component: str, key: str) -> bool:
    """判断菜单 component 是否解析为该契约键（复刻 resolveComponent 规则）。"""
    normalized = component.strip().strip("/").removesuffix("/index")
    if normalized == key:
        return True
    return key in [seg for seg in reversed(normalized.split("/")) if seg]


if __name__ == "__main__":
    sys.exit(main())
