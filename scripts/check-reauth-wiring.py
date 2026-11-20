#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""二次认证（step-up）接线门禁。

背景：危险端点（删用户、重置口令、改用户/租户状态、分配角色）标了 `@RequireReauth`，
未持有 step-up 凭证时后端返回 HTTP 428 + 业务码 42801。前端共享 http 只有在创建
客户端时传了 `onReauthRequired` 才会弹窗验密并带 `X-Reauth-Token` 重放一次；
**没传就会把 42801 原样抛给业务层**——用户只看到一句「需要二次认证」，
无论怎么重试都不可能成功，且没有任何报错线索。

本门禁做两件事：
1. 从后端 `@RequireReauth` 端点反推受保护路径；
2. 扫前端各应用的 api 调用，凡是命中这些路径的应用，其 `src/api/client.ts`
   必须注入 `onReauthRequired`。

用法：
    python scripts/check-reauth-wiring.py
    python scripts/check-reauth-wiring.py --json
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

# ---------------------------------------------------------------- 后端扫描

# `value =` / `path =` / 直接字面量 三种写法都要认
METHOD_MAPPING = re.compile(
    r'@(Get|Post|Put|Delete|Patch)Mapping\s*\(\s*(?:(?:value|path)\s*=\s*)?"([^"]*)"'
)
CLASS_MAPPING = re.compile(r'@RequestMapping\s*\(\s*(?:(?:value|path)\s*=\s*)?"([^"]*)"')
# 只认行首注解：javadoc 里的 {@code @RequireReauth} 与注解自身定义不算
REQUIRE_REAUTH = re.compile(r'^[ \t]*@RequireReauth\b', re.M)

# ---------------------------------------------------------------- 前端扫描

# get/post/put/del/patch<T>(`/user/${id}`) 或 http.get<T>('/user/1')
CALL = re.compile(r'\b(get|post|put|del|delete|patch|request)\s*(?:<[^>]*>)?\s*\(\s*([`\'"])(.+?)\2')
METHOD_OF = {"get": "GET", "post": "POST", "put": "PUT", "del": "DELETE", "delete": "DELETE", "patch": "PATCH"}


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


# 类型声明与实现列表：控制器普遍 implements 接口契约，路径注解在接口上，
# 而 `@RequireReauth` 标在实现类的方法上——只看单文件必然解析出 0 个端点。
TYPE_DECL = re.compile(r"\b(?:public\s+)?(?:interface|class|enum|record)\s+(\w+)")
IMPLEMENTS = re.compile(r"\bimplements\s+([\w\s,.]+?)\s*\{")
# 方法签名（取窗口内最后一个，即注解所修饰的方法）
METHOD_SIG = re.compile(
    r"(?:public|protected|private)\s+(?:static\s+|final\s+|synchronized\s+)*"
    r"[\w$<>\[\],.?\s]+?\s(\w+)\s*\("
)


def read_text(path: str) -> str:
    return io.open(path, encoding="utf-8", errors="replace").read()


_TYPE_INDEX: dict[str, str] | None = None


def java_type_index() -> dict[str, str]:
    """简单类名 → 文件路径（只索引 src/main 下的 .java；同名时优先 interfaces 包）。"""
    global _TYPE_INDEX
    if _TYPE_INDEX is not None:
        return _TYPE_INDEX
    index: dict[str, str] = {}
    for path in iter_files(BACKEND, (".java",)):
        norm = path.replace(os.sep, "/")
        if "/src/main/" not in norm:
            continue
        for m in TYPE_DECL.finditer(read_text(path)):
            name = m.group(1)
            known = index.get(name)
            if known is None:
                index[name] = path
            elif "interfaces" in norm and "interfaces" not in known.replace(os.sep, "/"):
                index[name] = path
    _TYPE_INDEX = index
    return index


def implemented_types(text: str) -> list[str]:
    """解析 implements 后的简单类型名（去泛型、去包名）。"""
    names: list[str] = []
    for m in IMPLEMENTS.finditer(text):
        for part in m.group(1).split(","):
            simple = part.split("<")[0].strip().split(".")[-1]
            if simple and simple not in names:
                names.append(simple)
    return names


def class_level_prefix(text: str) -> str:
    m = CLASS_MAPPING.search(text)
    return m.group(1) if m else ""


def mapping_of_method(text: str, method_name: str) -> tuple[str, str] | None:
    """在给定文本里定位方法的 @XxxMapping，返回 (verb, 子路径)。

    只认「与本方法声明之间没有方法边界」的那张 Mapping：注解与签名之间出现
    `;` 或 `}` 说明已经越过前一个方法的结尾，那张注解属于别人。
    """
    doc_block = re.compile(r"/\*.*?\*/", re.S)
    pattern = re.compile(r"\b" + re.escape(method_name) + r"\s*\(")
    for m in pattern.finditer(text):
        candidates = list(METHOD_MAPPING.finditer(text, 0, m.start()))
        for hit in reversed(candidates):
            between = doc_block.sub("", text[hit.end(): m.start()])
            if ";" in between or "}" in between:
                continue
            return hit.group(1).upper(), hit.group(2)
    return None


def backend_guarded_endpoints() -> tuple[list[dict], list[dict]]:
    """解析 @RequireReauth 端点。

    控制器多为「实现接口契约 + 注解标在实现方法」的写法：路径注解在接口上、
    二次认证注解在实现类里。因此先看实现类自身窗口内有无 Mapping，没有再顺着
    `implements` 去接口里找同名方法——否则一个端点都解析不到，门禁形同虚设。

    @return ({method, path, source} 列表，无法解析的注解列表)
    """
    out: list[dict] = []
    unresolved: list[dict] = []
    index = java_type_index()
    for path in iter_files(BACKEND, (".java",)):
        if "/src/main/" not in path.replace(os.sep, "/"):
            continue
        text = read_text(path)
        if "@RequireReauth" not in text:
            continue
        rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
        own_prefix = class_level_prefix(text)
        contracts = []
        for name in implemented_types(text):
            ipath = index.get(name)
            if ipath:
                contracts.append((name, read_text(ipath)))
        for hit in REQUIRE_REAUTH.finditer(text):
            window = text[max(0, hit.start() - 1200): hit.start()]
            local_matches = list(METHOD_MAPPING.finditer(window))
            if local_matches:
                last = local_matches[-1]
                out.append({
                    "method": last.group(1).upper(),
                    "path": make_pattern(own_prefix, last.group(2)),
                    "source": rel,
                })
                continue
            # 注解写在方法签名之前：注解之前的窗口只能看到「上一个方法」，
            # 被注解方法的名字必须往后找，否则会错配到邻接方法的路径上。
            sigs = list(METHOD_SIG.finditer(text[hit.end(): hit.end() + 1200]))
            method_name = sigs[0].group(1) if sigs else None
            found = None
            for cname, ctext in contracts:
                if not method_name:
                    break
                hit_mapping = mapping_of_method(ctext, method_name)
                if hit_mapping:
                    verb, sub = hit_mapping
                    found = {
                        "method": verb,
                        "path": make_pattern(class_level_prefix(ctext) or own_prefix, sub),
                        "source": f"{rel} (接口 {cname})",
                    }
                    break
            if found:
                out.append(found)
            else:
                unresolved.append({
                    "source": rel,
                    "method": method_name or "（未识别）",
                    "line": text[:hit.start()].count("\n") + 1,
                })
    return out, unresolved


def make_pattern(prefix: str, sub: str) -> str:
    joined = join_path(prefix, sub)
    # Spring Boot API Versioning：/{version}（常写作 v{version}）由 PathApiVersionResolver
    # 解析成 v1，门禁按 v1 比对，避免把版本段当成路径变量
    joined = joined.replace("v{version}", "v1").replace("{version}", "v1")
    # 其余 {id} / {userId} 一律归一成 *
    return re.sub(r"\{[^}]+\}", "*", joined)


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
    return apps


def normalize_call_path(raw: str) -> str | None:
    """`/user/${id}` → /user/*；`/user/${id}/password?x=1` → /user/*/password。"""
    if not raw.startswith("/"):
        return None
    without_query = raw.split("?")[0]
    without_query = re.sub(r"\$\{[^}]*\}", "*", without_query)
    without_query = re.sub(r"\{[^}]*\}", "*", without_query)
    return without_query.rstrip("/") or "/"


def path_matches(guarded: str, actual: str) -> bool:
    g = guarded.strip("/").split("/")
    a = actual.strip("/").split("/")
    if len(g) != len(a):
        return False
    return all(x == "*" or y == "*" or x == y for x, y in zip(g, a))


def scan_app(app_dir: str, guarded: list[dict]) -> list[dict]:
    hits: list[dict] = []
    src = os.path.join(app_dir, "src")
    for path in iter_files(src, (".ts", ".tsx")):
        if ".test." in os.path.basename(path) or ".spec." in os.path.basename(path):
            continue
        text = io.open(path, encoding="utf-8", errors="replace").read()
        if "/" not in text:
            continue
        for m in CALL.finditer(text):
            verb = m.group(1)
            if verb == "request":
                continue
            method = METHOD_OF.get(verb)
            if not method:
                continue
            actual = normalize_call_path(m.group(3))
            if actual is None:
                continue
            for endpoint in guarded:
                if endpoint["method"] != method:
                    continue
                if path_matches(endpoint["path"], actual):
                    rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
                    hits.append({
                        "method": method,
                        "path": endpoint["path"],
                        "call": m.group(3),
                        "file": rel,
                        "guarded_at": endpoint["source"],
                    })
                    break
    return hits


def has_reauth_wiring(app_dir: str) -> bool:
    client = os.path.join(app_dir, "src", "api", "client.ts")
    if not os.path.isfile(client):
        # 允许 client.tsx 或在别处建客户端
        for candidate in ("client.tsx",):
            alt = os.path.join(app_dir, "src", "api", candidate)
            if os.path.isfile(alt):
                client = alt
                break
        else:
            return False
    text = io.open(client, encoding="utf-8", errors="replace").read()
    return "onReauthRequired" in text


def main() -> int:
    parser = argparse.ArgumentParser(description="二次认证接线门禁")
    parser.add_argument("--json", action="store_true", help="输出 JSON")
    args = parser.parse_args()

    guarded, unresolved = backend_guarded_endpoints()
    apps = frontend_apps()
    results = []
    failures = []

    for app_dir in apps:
        hits = scan_app(app_dir, guarded)
        if not hits:
            continue
        wired = has_reauth_wiring(app_dir)
        name = os.path.relpath(app_dir, FRONTEND).replace(os.sep, "/")
        results.append({"app": name, "wired": wired, "hits": hits})
        if not wired:
            failures.append(name)

    if args.json:
        print(json.dumps({
            "guarded_endpoints": guarded,
            "unresolved": unresolved,
            "apps": results,
            "failures": failures,
        }, ensure_ascii=False, indent=2))
        return 1 if (failures or unresolved or not guarded) else 0

    print("=" * 64)
    print(f"step-up 受保护端点 {len(guarded)} 个 / 命中应用 {len(results)} 个")
    for endpoint in guarded:
        print(f"  - {endpoint['method']:6s} {endpoint['path']}")
    print("-" * 64)
    for item in results:
        mark = "OK  " if item["wired"] else "FAIL"
        print(f"[{mark}] {item['app']}：{len(item['hits'])} 处调用")
        if not item["wired"]:
            for hit in item["hits"]:
                print(f"         {hit['method']} {hit['call']}  ({hit['file']})")
            print("         → src/api/client.ts 未注入 onReauthRequired，42801 会原样抛给业务层")
    print("-" * 64)
    # 解析不到端点不能当作「没有受保护端点」通过：注解被移除、或解析逻辑失效，
    # 两种情况下门禁都已失去意义，必须人工确认。
    if unresolved:
        print(f"FAIL：{len(unresolved)} 处 @RequireReauth 无法解析出 HTTP 路径（门禁不能覆盖）")
        for item in unresolved:
            print(f"       {item['source']}:{item['line']} 方法 {item['method']}"
                  " → 实现类无 Mapping 且 implements 里找不到同名方法")
        return 1
    if not guarded:
        print("FAIL：未解析到任何 @RequireReauth 受保护端点——注解可能已被移除，"
              "或解析逻辑失效；请人工确认后调整门禁")
        return 1
    if failures:
        print(f"FAIL {len(failures)} 个应用未接二次认证：{', '.join(failures)}")
        return 1
    print(f"通过：{len(results)} 个应用均已接入二次认证（覆盖 {len(guarded)} 个受保护端点）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
