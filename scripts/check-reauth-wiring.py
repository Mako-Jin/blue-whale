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


def backend_guarded_endpoints() -> list[dict]:
    """返回 [{method, path, source}]，path 形如 /user/{id}。"""
    out: list[dict] = []
    for path in iter_files(BACKEND, (".java",)):
        if "/src/main/" not in path.replace(os.sep, "/"):
            continue
        text = io.open(path, encoding="utf-8", errors="replace").read()
        if "@RequireReauth" not in text:
            continue
        rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
        class_prefix = ""
        cm = CLASS_MAPPING.search(text)
        if cm:
            class_prefix = cm.group(1)
        for hit in REQUIRE_REAUTH.finditer(text):
            # 注解写在方法上：向前找最近的 @XxxMapping("...")
            window = text[max(0, hit.start() - 1200): hit.start()]
            matches = list(METHOD_MAPPING.finditer(window))
            if not matches:
                # 解析不到方法映射（javadoc / 配置类 / 拦截器）→ 不是端点，跳过
                continue
            last = matches[-1]
            out.append({
                "method": last.group(1).upper(),
                "path": make_pattern(class_prefix, last.group(2)),
                "source": rel,
            })
    return out


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

    guarded = backend_guarded_endpoints()
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
            "apps": results,
            "failures": failures,
        }, ensure_ascii=False, indent=2))
        return 1 if failures else 0

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
    if failures:
        print(f"FAIL {len(failures)} 个应用未接二次认证：{', '.join(failures)}")
        return 1
    print(f"通过：{len(results)} 个应用均已接入二次认证")
    return 0


if __name__ == "__main__":
    sys.exit(main())
