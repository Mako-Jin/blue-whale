#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""接口路径漂移门禁：前端调用的路径必须真实存在于后端。

背景：网关只转发、不剥离前缀（REQ-ARCH-003），所以前端写 `/user/page` 就必须对应
后端 `@RequestMapping("/user") + @GetMapping("/page")`；两边任何一侧改了路径，另一侧
不会编译报错、不会运行时报错（只在用户点下去的那一刻 404/405）。本项目已经因此踩过
三次同类事故：

  - `shared-packages/http` 硬写 `/auth/refresh`，而服务端早已是 `/auth/v1/refresh`；
  - `UserConfigController` 写 `@RequestMapping("/user-config")`，网关没有该前缀的路由，
    服务内也没有 `/auth/v1` 段——前端 `/auth/v1/user-config` 恒 404，通知偏好页全废；
  - `PartyUnionWelfareController` 类前缀已是 `/party/union/welfare`，方法又写
    `/welfare/item/{id}/sign`，实际变成 `.../welfare/welfare/...`。

比对口径：
- 后端：`@RequestMapping` 类前缀 + `@(Get|Post|Put|Delete|Patch)Mapping` 方法路径；
  `v{version}` 归一成 `v1`，`{id}` / `{userId}` 归一成 `*`；无参 `@PostMapping`
  落到类路径上。
- 前端：`get/post/put/del/patch<T>(...)` 的路径字面量；`${...}` 归一成 `*`，
  末尾拼接的 `${query}` / `${suffix}` 视为 query 串直接丢弃。
- 判定：路径与方法都要命中；**路径存在但方法不存在**同样 FAIL（405 一样是坏的，
  且比 404 更难从日志里认出来）。

用法：
    python scripts/check-api-path-drift.py
    python scripts/check-api-path-drift.py --json
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BACKEND = os.path.join(ROOT, "whale-backend")
FRONTEND = os.path.join(ROOT, "whale-frontend")

# ---------------------------------------------------------------- 后端解析

# 方法级映射：`@PostMapping`（无参）、`@GetMapping("/page")`、`@PutMapping(path = "/{id}")`
METHOD_MAPPING = re.compile(
    r'@(Get|Post|Put|Delete|Patch)Mapping\b\s*(?:\(\s*(?:(?:value|path)\s*=\s*)?"([^"]*)"[^)]*\))?'
)
CLASS_MAPPING = re.compile(r'@RequestMapping\s*\(\s*(?:(?:value|path)\s*=\s*)?"([^"]*)"')
# 类声明，用于把注解归属到正确的类（一个文件可能声明多个类型）
CLASS_DECL = re.compile(
    r'^[ \t]*(?:public\s+|final\s+|abstract\s+)*(?:class|interface|record|enum)\s+\w+', re.M
)
# `@RequestMapping(method = RequestMethod.GET)` 形态
REQUEST_MAPPING_METHOD = re.compile(
    r'@RequestMapping\s*\([^)]*method\s*=\s*RequestMethod\.(GET|POST|PUT|DELETE|PATCH)'
)

# ---------------------------------------------------------------- 前端解析

CALL = re.compile(
    r'\b(get|post|put|del|delete|patch|downloadBlob)\s*(?:<[^>]*>)?\s*\(\s*([`\'"])(.+?)\2'
)
# 包装式调用：`request<T>(url, { method: 'POST' })`（论坛/孪生等子应用统一走 @bluewhale/http 包装层）。
# 不补这条规则时，这类调用既不计数也不校验，等于「门禁看不见」——2026-11-12 负向验证发现。
WRAPPER_CALL = re.compile(
    r'\brequest\s*(?:<[^();]*?>)?\s*\(\s*([`\'"])(.+?)\1'
)
METHOD_OF = {
    "get": "GET", "post": "POST", "put": "PUT",
    "del": "DELETE", "delete": "DELETE", "patch": "PATCH",
    # 导出/下载类调用：`downloadBlob(url)` 走 GET 取原始字节。
    # 不识别时这类调用会被完全跳过——导出端点漂移就永远查不出来。
    "downloadBlob": "GET",
}


def iter_calls(text: str):
    """枚举文件内的接口调用，产出 (HTTP 方法, 路径字面量, 匹配结束位置)。"""
    for m in CALL.finditer(text):
        method = METHOD_OF.get(m.group(1))
        if method:
            yield method, m.group(3), m.end()
    for m in WRAPPER_CALL.finditer(text):
        # 方法取本次调用实参里的 method: 'XXX'，缺省按 GET。
        # 必须在本调用的 `)` 处截断：否则会读到下一个接口的 method，
        # 把 GET 误判成 POST（负向验证时踩到过，表现为「后端只有 GET」的假报错）。
        tail = text[m.end():m.end() + 300]
        stop = tail.find(")")
        if stop != -1:
            tail = tail[:stop]
        verb = re.search(r"method\s*:\s*['\"](\w+)['\"]", tail)
        yield (verb.group(1).upper() if verb else "GET"), m.group(2), m.end()


def iter_files(root: str, exts: tuple[str, ...]):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [
            d for d in dirnames
            if d not in {"node_modules", "dist", "target", "build", ".git", ".turbo", "coverage"}
        ]
        for name in filenames:
            if name.endswith(exts):
                yield os.path.join(dirpath, name)


def join_path(prefix: str, sub: str) -> str:
    parts = [p.strip("/") for p in (prefix, sub) if p and p.strip("/")]
    return "/" + "/".join(parts) if parts else "/"


def backend_pattern(prefix: str, sub: str) -> str:
    joined = join_path(prefix, sub)
    # Spring API Versioning：`v{version}` 由 PathApiVersionResolver 解析成 v1
    joined = joined.replace("v{version}", "v1").replace("{version}", "v1")
    return re.sub(r"\{[^}]+\}", "*", joined)


def backend_endpoints() -> dict[tuple[str, str], str]:
    """返回 {(METHOD, pattern): 来源文件}。"""
    out: dict[tuple[str, str], str] = {}
    for path in iter_files(BACKEND, (".java",)):
        norm = path.replace(os.sep, "/")
        if "/src/main/" not in norm:
            continue
        text = io.open(path, encoding="utf-8", errors="replace").read()
        if "Mapping" not in text:
            continue
        rel = os.path.relpath(path, ROOT).replace(os.sep, "/")

        # 把文件按类声明切块：每块用自己的 @RequestMapping 作前缀
        blocks: list[tuple[str, str]] = []
        decls = list(CLASS_DECL.finditer(text))
        if not decls:
            blocks.append(("", text))
        else:
            for idx, decl in enumerate(decls):
                end = decls[idx + 1].start() if idx + 1 < len(decls) else len(text)
                blocks.append((text[:decl.start()], text[decl.start():end]))

        for prelude, block in blocks:
            # 类前缀通常写在类声明上方（prelude 尾部）或紧跟声明
            cm = CLASS_MAPPING.search(prelude) or CLASS_MAPPING.search(block)
            prefix = cm.group(1) if cm else ""

            for m in METHOD_MAPPING.finditer(block):
                out.setdefault(
                    (m.group(1).upper(), backend_pattern(prefix, m.group(2) or "")), rel
                )

            # `@RequestMapping(method = RequestMethod.GET)` 形态
            for m in REQUEST_MAPPING_METHOD.finditer(block):
                rm = CLASS_MAPPING.search(block)
                out.setdefault(
                    (m.group(1).upper(), backend_pattern(prefix, rm.group(1) if rm else "")), rel
                )
    return out


# ---------------------------------------------------------------- 前端归一


def strip_query(raw: str) -> str:
    """截断 query，但跳过 `${...}` 内部的 `?`（模板里常写 `${q ? '?' + q : ''}`）。"""
    depth = 0
    i = 0
    while i < len(raw):
        ch = raw[i]
        if ch == "$" and i + 1 < len(raw) and raw[i + 1] == "{":
            depth += 1
            i += 2
            continue
        if ch == "}" and depth:
            depth -= 1
            i += 1
            continue
        if ch == "?" and depth == 0:
            return raw[:i]
        i += 1
    return raw


def drop_trailing_template(raw: str) -> str:
    """剥掉末尾拼接的 query 片段（`.../refresh${query}` → `.../refresh`）。

    判别规则看最后一个顶层 `${`（深度 0）：
      - 它前面是 `/`（`.../category/${id}`）→ 路径段变量，保留；
      - 它内部含 `/`（`${'a/b'}`）→ 路径段，保留；
      - 其余（`.../refresh${query}`、`.../execute${q ? `?${q}` : ''}`）→ query / 后缀拼接，剥掉。

    CALL 正则非贪婪，遇到嵌套反引号会提前截断，留下**未闭合**的 `${q ? `——
    这里按「未闭合也照样剥」处理，因为未闭合的模板不可能是合法路径段。
    """
    positions: list[int] = []
    depth = 0
    i = 0
    while i < len(raw):
        if raw.startswith("${", i):
            if depth == 0:
                positions.append(i)
            depth += 1
            i += 2
            continue
        if raw[i] == "}" and depth:
            depth -= 1
        i += 1

    if not positions:
        return raw
    pos = positions[-1]
    tail = raw[pos:]
    if "/" in tail:
        return raw
    if pos > 0 and raw[pos - 1] == "/":
        return raw
    return raw[:pos]


COMPLEX = "\x00"


def normalize_call(raw: str) -> tuple[str | None, str]:
    """返回 (归一化路径, 模式)。模式为 exact（精确比对）或 prefix（前缀比对）。

    路径为 None 表示无法可靠判定（非绝对路径）。
    """
    if not raw.startswith("/"):
        return None, "非绝对路径"
    p = strip_query(raw)
    if "`" in p:
        # 嵌套模板（如 `${q ? `?${q}` : ''}`）→ 截到第一个 ${ 为止
        idx = p.find("${")
        p = p[:idx] if idx >= 0 else p
    p = drop_trailing_template(p)

    # 逐个归一 ${...}：简单变量 → `*`；含三元 / 嵌套反引号 → 无法静态确定，退化为前缀
    def _repl(mo: re.Match) -> str:
        inner = mo.group(0)[2:-1]
        return COMPLEX if ("?" in inner or "`" in inner) else "*"

    p = re.sub(r"\$\{[^}]*\}", _repl, p)
    if COMPLEX in p:
        head = p[: p.index(COMPLEX)].rstrip("/")
        return (head, "前缀") if head else (None, "模板无法归一")
    return (p.rstrip("/") or "/"), "exact"


def path_matches(endpoint: str, actual: str) -> bool:
    e = endpoint.strip("/").split("/")
    a = actual.strip("/").split("/")
    if len(e) != len(a):
        return False
    return all(x == "*" or y == "*" or x == y for x, y in zip(e, a))


def prefix_matches(endpoint: str, actual: str) -> bool:
    """前缀匹配：前端只写到一段固定前缀（后面是字符串拼接）。"""
    e = endpoint.strip("/").split("/")
    a = actual.strip("/").split("/")
    if len(a) > len(e):
        return False
    return all(x == "*" or y == "*" or x == y for x, y in zip(a, e))


def frontend_apps() -> list[str]:
    apps: list[str] = []
    for name in ("admin", "ops"):
        candidate = os.path.join(FRONTEND, name)
        if os.path.isdir(os.path.join(candidate, "src")):
            apps.append(candidate)
    platform = os.path.join(FRONTEND, "platform-apps")
    if os.path.isdir(platform):
        for name in sorted(os.listdir(platform)):
            candidate = os.path.join(platform, name)
            if os.path.isdir(os.path.join(candidate, "src")):
                apps.append(candidate)
    shared = os.path.join(FRONTEND, "shared-packages")
    if os.path.isdir(shared):
        for name in sorted(os.listdir(shared)):
            candidate = os.path.join(shared, name)
            if os.path.isdir(os.path.join(candidate, "src")):
                apps.append(candidate)
    return apps


def main() -> int:
    parser = argparse.ArgumentParser(description="接口路径漂移门禁")
    parser.add_argument("--json", action="store_true", help="输出 JSON")
    parser.add_argument("--backend-dir", help="后端根目录（负向验证用）")
    args = parser.parse_args()

    global BACKEND
    if args.backend_dir:
        BACKEND = os.path.abspath(args.backend_dir)

    endpoints = backend_endpoints()
    by_method: dict[str, list[str]] = {}
    for method, pattern in endpoints:
        by_method.setdefault(method, []).append(pattern)

    total = 0
    skipped: list[dict] = []
    failures: list[dict] = []

    for app_dir in frontend_apps():
        src = os.path.join(app_dir, "src")
        for path in iter_files(src, (".ts", ".tsx")):
            base = os.path.basename(path)
            if ".test." in base or ".spec." in base or ".d.ts" in base:
                continue
            text = io.open(path, encoding="utf-8", errors="replace").read()
            if "/" not in text:
                continue
            rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
            for method, raw, end_pos in iter_calls(text):
                actual, mode = normalize_call(raw)
                if actual is None:
                    skipped.append({"call": raw, "file": rel, "why": mode})
                    continue
                total += 1

                # 字符串拼接形态：字面量后面紧跟 `+` → 前缀匹配
                tail = text[end_pos:end_pos + 3].lstrip()
                concat = tail.startswith("+")

                same_path = by_method.get(method, [])
                if mode == "exact" and not concat:
                    ok = any(path_matches(p, actual) for p in same_path)
                    if ok:
                        continue
                    # 路径存在但方法不对 → 405，同样是坏的
                    other = [
                        other_method for other_method, pats in by_method.items()
                        if other_method != method and any(path_matches(p, actual) for p in pats)
                    ]
                    failures.append({
                        "method": method,
                        "path": actual,
                        "call": raw,
                        "file": rel,
                        "reason": f"后端只有 {'/'.join(other)}，缺 {method}" if other else "后端无此路径",
                    })
                else:
                    # 前缀 / 拼接：允许任意方法（拼接后的方法无法静态确定）
                    ok = any(
                        prefix_matches(p, actual)
                        for pats in by_method.values() for p in pats
                    )
                    if not ok:
                        failures.append({
                            "method": method,
                            "path": actual,
                            "call": raw,
                            "file": rel,
                            "reason": "前缀无法匹配任何后端端点",
                        })

    if args.json:
        print(json.dumps({
            "endpoints": len(endpoints),
            "calls": total,
            "failures": failures,
            "skipped": skipped,
        }, ensure_ascii=False, indent=2))
        return 1 if failures else 0

    print("=" * 72)
    print(f"后端端点 {len(endpoints)} 个 / 前端可判定调用 {total} 处 / 跳过 {len(skipped)} 处")
    print("-" * 72)
    for item in failures:
        print(f"FAIL {item['method']:6s} {item['path']}")
        print(f"     {item['reason']}   ({item['file']})")
    print("-" * 72)
    if failures:
        print(f"RESULT: FAIL（{len(failures)} 处调用在后端找不到对应端点）")
        return 1
    print(f"RESULT: PASS（{total} 处调用全部命中后端端点，跳过 {len(skipped)} 处模板调用）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
