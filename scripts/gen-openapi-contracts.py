#!/usr/bin/env python3
# ============================================================
# 批量生成前端 OpenAPI TS 契约（gen-openapi-contracts.py）
#
# 背景：
#   P2-3 契约自动化原只有 whale-auth 试点（admin 的 auth-schema.d.ts）。
#   Wave 6.34 起 whale-spring-boot-starter 统一挂 springdoc，
#   所有 MVC 服务默认暴露 /v3/api-docs；本脚本负责批量拉取并用
#   openapi-typescript 生成 <service>-schema.d.ts 到各前端应用。
#
# 用法：
#   python scripts/gen-openapi-contracts.py                 # 全部
#   python scripts/gen-openapi-contracts.py --only twin,agent
#   python scripts/gen-openapi-contracts.py --entry test=http://127.0.0.1:9299/v3/api-docs,whale-frontend/tmp-schema.d.ts
#
# 前置：
#   1) 目标服务已用含 springdoc 的镜像启动（改公共 starter 后必须重建镜像，restart 不带新代码）
#   2) whale-frontend 依赖已安装（pnpm install），admin 里有 openapi-typescript
#
# 说明：
#   - 生成产物是 .d.ts 类型声明，仅当被 import 才参与编译；接线由 check-api-path-drift.py 兜底
#   - 生成需要服务在跑，无法进 CI；CI 侧由路径漂移门禁守住「路径存在性」这一层
# ============================================================
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FRONTEND = ROOT / "whale-frontend"

# 服务名 -> 端口 + 输出路径列表（相对仓库根，同一 JSON 可发多个消费方）
CONTRACTS: dict[str, dict] = {
    "auth": {
        "port": 8081,
        "outputs": ["whale-frontend/admin/src/api/generated/auth-schema.d.ts"],
    },
    "twin": {
        "port": 8089,
        "outputs": [
            "whale-frontend/platform-apps/whale-twin/src/api/generated/twin-schema.d.ts"
        ],
    },
    "agent": {
        # LLM 网关型服务：SSE 流式端点在 OpenAPI 里只体现请求契约
        "port": 8090,
        "outputs": [
            "whale-frontend/platform-apps/whale-agent/src/api/generated/agent-schema.d.ts"
        ],
    },
    "message": {
        "port": 8094,
        "outputs": [
            "whale-frontend/admin/src/api/generated/message-schema.d.ts",
            "whale-frontend/platform-apps/whale-portal/src/api/generated/message-schema.d.ts",
        ],
    },
    "file": {
        "port": 8085,
        "outputs": ["whale-frontend/admin/src/api/generated/file-schema.d.ts"],
    },
    "job": {
        "port": 8082,
        "outputs": ["whale-frontend/admin/src/api/generated/job-schema.d.ts"],
    },
    "pay": {
        "port": 8086,
        "outputs": ["whale-frontend/admin/src/api/generated/pay-schema.d.ts"],
    },
}


def fetch_docs(url: str, retries: int = 2) -> dict:
    """拉取并校验 api-docs JSON；paths 为空视为失败（服务在跑但没扫到 controller）。

    强制不走系统代理：本机 127.0.0.1 直连，避免环境变量里的 HTTP 代理
    把连不上的端口报成 502 而不是 connection refused。
    """
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    last_err: Exception | None = None
    for attempt in range(retries + 1):
        try:
            with opener.open(url, timeout=15) as resp:
                if resp.status != 200:
                    raise RuntimeError(f"HTTP {resp.status}")
                data = json.loads(resp.read().decode("utf-8"))
            if "openapi" not in data:
                raise RuntimeError("响应不是 OpenAPI 文档（缺 openapi 字段）")
            if not data.get("paths"):
                raise RuntimeError("paths 为空：springdoc 在跑但没扫到任何 controller")
            return data
        except (urllib.error.URLError, urllib.error.HTTPError, RuntimeError, json.JSONDecodeError) as e:
            last_err = e
            if attempt < retries:
                time.sleep(3)
    raise RuntimeError(f"拉取 {url} 失败：{last_err}")


def find_generator() -> list[str]:
    """定位 openapi-typescript CLI（统一用 admin 的 devDependency 版本）。

    Windows 上 pnpm 是 pnpm.cmd，subprocess 直接传 "pnpm" 会 FileNotFoundError，
    必须经 shutil.which 解析真实可执行文件。
    """
    pnpm = shutil.which("pnpm")
    if not pnpm:
        raise RuntimeError("找不到 pnpm：请确认 pnpm 在 PATH 中")
    cmd = [pnpm, "-C", str(FRONTEND / "admin"), "exec", "openapi-typescript", "--version"]
    try:
        subprocess.run(cmd, capture_output=True, check=True, text=True, timeout=60)
    except (subprocess.CalledProcessError, FileNotFoundError, subprocess.TimeoutExpired) as e:
        raise RuntimeError(
            "openapi-typescript 不可用：请先在 whale-frontend 执行 pnpm install"
        ) from e
    return [pnpm, "-C", str(FRONTEND / "admin"), "exec", "openapi-typescript"]


def generate(name: str, url: str, outputs: list[str], generator: list[str]) -> None:
    print(f"→ {name}  ({url})")
    docs = fetch_docs(url)
    print(f"  paths: {len(docs['paths'])}  openapi: {docs['openapi']}")
    with tempfile.NamedTemporaryFile(
        mode="w", suffix=".json", delete=False, encoding="utf-8"
    ) as f:
        json.dump(docs, f, ensure_ascii=False)
        tmp = Path(f.name)
    try:
        for rel in outputs:
            out = ROOT / rel
            out.parent.mkdir(parents=True, exist_ok=True)
            subprocess.run(
                generator + [str(tmp), "-o", str(out)],
                check=True,
                capture_output=True,
                text=True,
                timeout=120,
            )
            print(f"  ✓ {rel}")
    finally:
        tmp.unlink(missing_ok=True)


def main() -> int:
    ap = argparse.ArgumentParser(description="批量生成前端 OpenAPI TS 契约")
    ap.add_argument(
        "--only", default="", help="只处理指定服务名（逗号分隔），默认全部"
    )
    ap.add_argument(
        "--entry",
        action="append",
        default=[],
        metavar="NAME=URL,OUT[,OUT...]",
        help="临时追加契约项（负向验证/一次性生成用）；URL 需含完整路径",
    )
    args = ap.parse_args()

    jobs: dict[str, tuple[str, list[str]]] = {}
    only = {s.strip() for s in args.only.split(",") if s.strip()}
    for name, cfg in CONTRACTS.items():
        if only and name not in only:
            continue
        jobs[name] = (
            f"http://127.0.0.1:{cfg['port']}/v3/api-docs",
            list(cfg["outputs"]),
        )
    for raw in args.entry:
        try:
            name, rest = raw.split("=", 1)
            parts = [p for p in rest.split(",") if p]
            url, outs = parts[0], parts[1:]
            if not outs:
                raise ValueError("至少要有一个输出路径")
        except ValueError as e:
            print(f"[FAIL] --entry 格式错误: {raw}（{e}）")
            return 1
        jobs[f"entry:{name}"] = (url, outs)

    if not jobs:
        print("没有要处理的契约项")
        return 0

    try:
        generator = find_generator()
    except RuntimeError as e:
        print(f"[FAIL] {e}")
        return 1

    failed: list[str] = []
    for name, (url, outs) in jobs.items():
        try:
            generate(name, url, outs, generator)
        except (RuntimeError, subprocess.CalledProcessError) as e:
            err = e
            if isinstance(e, subprocess.CalledProcessError):
                err = RuntimeError((e.stderr or e.stdout or "").strip()[:300])
            print(f"  [FAIL] {name}: {err}")
            failed.append(name)

    if failed:
        print(f"\n完成：{len(jobs) - len(failed)}/{len(jobs)} 成功，失败：{', '.join(failed)}")
        return 1
    print(f"\n完成：{len(jobs)}/{len(jobs)} 全部成功")
    return 0


if __name__ == "__main__":
    sys.exit(main())
