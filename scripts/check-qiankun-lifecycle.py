#!/usr/bin/env python3
"""子应用 qiankun 接入静态门禁。

背景（真实事故，Wave 6.18 发现）：子应用都用 ``vite-plugin-qiankun`` 插件，但绝大多数
只声明了 ``export function mount`` / ``unmount``，**没有调用** ``renderWithQiankun`` 注册。
插件构建时会在产物 ``index.html`` 注入一段桥接脚本，从
``window.moudleQiankunAppLifeCycles[<name>]`` 取生命周期；未注册时该表为空，基座调用
``mountMicroApp`` 返回的 Promise **永不 resolve** —— 表现为白屏，控制台无任何报错。
20 个子应用里 14+ 个存在此问题，靠人工点页面才发现，故固化为门禁。

检查项：

- A（FAIL）：vite 配置含 ``vite-plugin-qiankun`` 的子应用，src 下必须调用 ``renderWithQiankun``；
- B（FAIL）：必须导出 ``mount`` 与 ``unmount``（插件桥接表的三件套缺一不可）；
- C（FAIL）：必须声明 ``base: '/<slug>'``（缺 base 时产物资源路径为 ``/assets/*``，
  在基座内按子应用前缀加载必然 404）；
- D（FAIL）：基座 ``apps.ts`` 已注册的子应用，其 ``activeRule`` 必须等于 vite ``base``
  （两者不一致时路由能激活但资源 404，或反之）；
- E（WARN）：带插件却未在基座注册（可能是独立站点，也可能是漏注册）。

用法：
    python scripts/check-qiankun-lifecycle.py
    python scripts/check-qiankun-lifecycle.py --verbose

退出码：0 通过（可能有 WARN）；1 存在 FAIL。
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
APPS_DIR = REPO_ROOT / "whale-frontend" / "platform-apps"
PLATFORM_REGISTRY = APPS_DIR / "whale-platform" / "src" / "qiankun" / "apps.ts"

# 目录名 → 期望的基座激活前缀默认由名字推导（whale-oa → /oa），以下为手工例外
SLUG_OVERRIDES: dict[str, str] = {
    "whale-platform": "",  # 基座自身不是子应用
}

# 不是子应用的应用（基座/工具库），跳过检查
SKIP_APPS = {"whale-platform", "whale-ui", "whale-form-designer", "whale-workflow-designer"}

BASE_RE = re.compile(r"base\s*:\s*['\"]([^'\"]*)['\"]")
# 基座注册表条目：name: 'whale-x' … activeRule: '/x'
REGISTRY_ENTRY_RE = re.compile(
    r"name:\s*'(?P<name>[\w-]+)'(?P<body>.*?)activeRule:\s*'(?P<rule>[^']*)'",
    re.S,
)


@dataclass
class AppInfo:
    """单个子应用的接入现状。"""

    name: str
    has_plugin: bool
    base: str | None
    has_register: bool
    exports: set[str]
    main_file: Path | None


def find_main(src: Path) -> Path | None:
    """定位入口文件（main.tsx / main.ts / index.tsx 三种写法都存在过）。"""
    for candidate in ("main.tsx", "main.ts", "index.tsx", "index.ts"):
        path = src / candidate
        if path.exists():
            return path
    return None


def collect_app(directory: Path) -> AppInfo:
    """读取单个子应用的接入要素。"""
    name = directory.name
    vite_config = directory / "vite.config.ts"
    vite_text = vite_config.read_text(encoding="utf-8", errors="replace") if vite_config.exists() else ""
    has_plugin = "vite-plugin-qiankun" in vite_text

    base_match = BASE_RE.search(vite_text)
    base = base_match.group(1) if base_match else None

    src = directory / "src"
    main_file = find_main(src)
    main_text = main_file.read_text(encoding="utf-8", errors="replace") if main_file else ""

    # 注册既可能写在 main，也可能抽到 module 里；整个 src 一起看，避免误报
    has_register = bool(main_text and re.search(r"renderWithQiankun\s*\(", main_text))
    if not has_register and src.exists():
        for path in src.rglob("*.ts*"):
            if path == main_file or path.name.endswith(".d.ts"):
                continue
            if re.search(r"renderWithQiankun\s*\(", path.read_text(encoding="utf-8", errors="replace")):
                has_register = True
                break

    exports: set[str] = set()
    for lifecycle in ("bootstrap", "mount", "unmount"):
        if re.search(rf"export\s+(?:async\s+)?function\s+{lifecycle}\b", main_text) or re.search(
            rf"export\s*\{{\s*[^}}]*\b{lifecycle}\b", main_text
        ):
            exports.add(lifecycle)

    return AppInfo(name, has_plugin, base, has_register, exports, main_file)


def parse_registry() -> dict[str, str]:
    """解析基座注册表：子应用名 → activeRule。"""
    if not PLATFORM_REGISTRY.exists():
        return {}
    text = PLATFORM_REGISTRY.read_text(encoding="utf-8", errors="replace")
    return {m.group("name"): m.group("rule") for m in REGISTRY_ENTRY_RE.finditer(text)}


def expected_base(name: str) -> str:
    """子应用期望的 base（默认由名字推导）。"""
    if name in SLUG_OVERRIDES:
        return SLUG_OVERRIDES[name]
    return "/" + name.replace("whale-", "", 1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verbose", action="store_true", help="逐条打印通过项")
    args = parser.parse_args()

    if not APPS_DIR.exists():
        print(f"FAIL: 子应用目录不存在 {APPS_DIR}")
        return 1

    registry = parse_registry()
    fails: list[str] = []
    warns: list[str] = []
    checked = 0

    for directory in sorted(p for p in APPS_DIR.iterdir() if p.is_dir()):
        if directory.name in SKIP_APPS:
            continue
        info = collect_app(directory)
        if not info.has_plugin:
            continue
        checked += 1

        if not info.has_register:
            fails.append(
                f"[A] {info.name}: 未调用 renderWithQiankun 注册 → 基座内 mount 永不 resolve（白屏无报错）"
            )
        missing = {"mount", "unmount"} - info.exports
        if missing:
            fails.append(f"[B] {info.name}: 入口未导出 {', '.join(sorted(missing))}")

        want = expected_base(info.name)
        if info.base is None:
            fails.append(f"[C] {info.name}: vite 未声明 base（期望 '{want}'），基座内资源路径会 404")
        elif info.base != want:
            fails.append(f"[C] {info.name}: vite base='{info.base}' 与期望 '{want}' 不一致")

        rule = registry.get(info.name)
        if rule is None:
            warns.append(f"[E] {info.name}: 带 qiankun 插件但基座 apps.ts 未注册（独立站点可忽略）")
        elif info.base and info.base != rule:
            fails.append(f"[D] {info.name}: activeRule='{rule}' 与 vite base='{info.base}' 不一致")

        if args.verbose:
            print(f"OK   {info.name} base={info.base} register={info.has_register} exports={sorted(info.exports)}")

    # 反向检查：注册表里有、磁盘上没有的子应用（重命名后遗留，基座加载必然 404）
    for name, rule in registry.items():
        if not (APPS_DIR / name).is_dir():
            fails.append(f"[F] 基座注册了 {name}（activeRule={rule}），但 platform-apps 下无该目录")

    for line in fails:
        print(f"FAIL {line}")
    for line in warns:
        print(f"WARN {line}")

    print(
        f"\n汇总：检查 {checked} 个带 qiankun 插件的子应用，"
        f"FAIL {len(fails)}，WARN {len(warns)}，基座注册 {len(registry)} 个"
    )
    return 0 if not fails else 1


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001 - 老版本 Python 无 reconfigure
        pass
    sys.exit(main())
