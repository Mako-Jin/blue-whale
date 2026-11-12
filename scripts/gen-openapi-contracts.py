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
#   - --status 打印覆盖表（谁上线了、谁还缺契约），用来规划镜像重建顺序
#
# 已知坑（2026-11 实测）：
#   auth 服务把 controller 映射上移到 `*Api` 接口并改成
#   `@RequestMapping(path = "/user/v{version}", version = "1")` 之后，springdoc
#   渲染出的路径会**丢掉 v{version} 段**（/user/v1/{id} → /user/{id}）。而运行时
#   两种写法都能命中（Spring 版本化对无版本请求回落到默认版本），所以不会报错，
#   只是契约与前端手写的 `/user/v1/...` 不一致。重新生成前先比对 diff，别把
#   版本段无声抹掉。
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
#
# 端口取自 deploy/backend/docker-compose.services.yml 的 SERVER_PORT；
# 覆盖面 = 所有挂了 whale-spring-boot-starter 的 MVC 服务。gateway 是 WebFlux
# 不经该 starter，无 /v3/api-docs；map 服务目前没有独立前端应用，未列。
# 服务镜像还是旧代码时（未含 springdoc）会拉取失败，用 --status 看谁还没上线。
CONTRACTS: dict[str, dict] = {
    # ---- 已出契约、已接线 ----
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
    # ---- 待镜像重建后生成（服务已编排、未在跑） ----
    "crm": {
        "port": 9007,
        "outputs": ["whale-frontend/platform-apps/whale-crm/src/api/generated/crm-schema.d.ts"],
    },
    "doc": {
        "port": 8095,
        "outputs": ["whale-frontend/platform-apps/whale-doc/src/api/generated/doc-schema.d.ts"],
    },
    "erp": {
        "port": 9001,
        "outputs": ["whale-frontend/platform-apps/whale-erp/src/api/generated/erp-schema.d.ts"],
    },
    "oa": {
        "port": 9004,
        "outputs": ["whale-frontend/platform-apps/whale-oa/src/api/generated/oa-schema.d.ts"],
    },
    "mes": {
        "port": 9002,
        "outputs": ["whale-frontend/platform-apps/whale-mes/src/api/generated/mes-schema.d.ts"],
    },
    "lms": {
        "port": 9008,
        "outputs": ["whale-frontend/platform-apps/whale-lms/src/api/generated/lms-schema.d.ts"],
    },
    "party": {
        "port": 9009,
        "outputs": ["whale-frontend/platform-apps/whale-party/src/api/generated/party-schema.d.ts"],
    },
    "pdm": {
        "port": 9003,
        "outputs": ["whale-frontend/platform-apps/whale-pdm/src/api/generated/pdm-schema.d.ts"],
    },
    "pm": {
        "port": 9005,
        "outputs": ["whale-frontend/platform-apps/whale-pm/src/api/generated/pm-schema.d.ts"],
    },
    "contract": {
        "port": 9006,
        "outputs": [
            "whale-frontend/platform-apps/whale-contract/src/api/generated/contract-schema.d.ts"
        ],
    },
    "mall": {
        "port": 9010,
        "outputs": ["whale-frontend/platform-apps/whale-mall/src/api/generated/mall-schema.d.ts"],
    },
    "form": {
        "port": 8084,
        "outputs": [
            "whale-frontend/platform-apps/whale-form-designer/src/api/generated/form-schema.d.ts"
        ],
    },
    "workflow": {
        "port": 8083,
        "outputs": [
            "whale-frontend/platform-apps/whale-workflow-designer/src/api/generated/workflow-schema.d.ts"
        ],
    },
    "im": {
        "port": 8091,
        "outputs": ["whale-frontend/platform-apps/whale-im/src/api/generated/im-schema.d.ts"],
    },
    "mail": {
        "port": 8099,
        "outputs": ["whale-frontend/platform-apps/whale-mail/src/api/generated/mail-schema.d.ts"],
    },
    "assistant": {
        "port": 8087,
        "outputs": [
            "whale-frontend/platform-apps/whale-assistant/src/api/generated/assistant-schema.d.ts"
        ],
    },
    "video": {
        "port": 8098,
        "outputs": ["whale-frontend/platform-apps/whale-video/src/api/generated/video-schema.d.ts"],
    },
    "music": {
        "port": 8096,
        "outputs": ["whale-frontend/platform-apps/whale-music/src/api/generated/music-schema.d.ts"],
    },
    "iot": {
        "port": 8088,
        "outputs": ["whale-frontend/platform-apps/whale-iot/src/api/generated/iot-schema.d.ts"],
    },
    "scene": {
        "port": 8097,
        "outputs": ["whale-frontend/platform-apps/whale-scene/src/api/generated/scene-schema.d.ts"],
    },
    # 运营端消费：archive（admin 的归档页）/ audit / alert / monitor
    "archive": {
        "port": 8093,
        "outputs": [
            "whale-frontend/admin/src/api/generated/archive-schema.d.ts",
            "whale-frontend/ops/src/api/generated/archive-schema.d.ts",
        ],
    },
    "audit": {
        "port": 9101,
        "outputs": ["whale-frontend/ops/src/api/generated/audit-schema.d.ts"],
    },
    "alert": {
        "port": 9103,
        "outputs": ["whale-frontend/ops/src/api/generated/alert-schema.d.ts"],
    },
    "monitor": {
        "port": 9102,
        "outputs": ["whale-frontend/ops/src/api/generated/monitor-schema.d.ts"],
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


def probe(url: str, timeout: float = 5) -> tuple[bool, str]:
    """轻量探测：服务是否已暴露 api-docs（不走代理、不重试）。"""
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(url, timeout=timeout) as resp:
            if resp.status != 200:
                return False, f"HTTP {resp.status}"
            data = json.loads(resp.read().decode("utf-8"))
        paths = len(data.get("paths") or {})
        if not paths:
            return False, "paths 为空（服务在跑但无 springdoc？）"
        return True, f"paths {paths}"
    except Exception as e:  # noqa: BLE001 - 仅用于汇总表
        return False, type(e).__name__


def print_status() -> None:
    """打印「服务 ↔ 契约产物」覆盖表，用于判断下一步该重建哪个镜像。"""
    reachable = missing_out = 0
    print(f"{'服务':10} {'端口':6} {'状态':22} 契约产物")
    for name, cfg in CONTRACTS.items():
        ok, detail = probe(f"http://127.0.0.1:{cfg['port']}/v3/api-docs")
        if ok:
            reachable += 1
        outs = list(cfg["outputs"])
        absent = [o for o in outs if not (ROOT / o).exists()]
        if absent:
            missing_out += 1
        mark = "OK" if ok and not absent else ("在跑未生成" if ok else "未上线")
        exists = "缺失" if absent else "已生成"
        print(f"{name:10} {cfg['port']:<6} {mark + ' / ' + detail:22} {exists} × {len(outs)}")
    print(
        f"\n汇总：{len(CONTRACTS)} 个服务，api-docs 可达 {reachable}，"
        f"契约产物待生成 {missing_out}"
    )
    print("未上线的服务需先 `scripts/dev/build-service-images.sh <svc>` 重建镜像"
          "（注意 Docker VM 余量 > 1G）。")


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
    ap.add_argument(
        "--status",
        action="store_true",
        help="只打印各服务 api-docs 可达性与契约产物覆盖表，不做生成",
    )
    args = ap.parse_args()

    if args.status:
        print_status()
        return 0

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
