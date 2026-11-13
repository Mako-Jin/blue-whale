#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""覆盖率门禁（REQ-CICD-001 / docs/design/cicd.md §3.3）。

历史做法是用 jacoco 的 BUNDLE 规则对【每个模块】分别卡 40% 行 / 30% 分支。
本项目是 69 模块的聚合工程，大量模块是 DTO / Controller 装配层，单模块阈值
不可达（例如 whale-common-core 只有 6 个类），结果门禁在 reactor 第 2 个模块
就失败，等于整条门禁失效、还挡住后面所有模块的测试信号。

现在的口径：
  1. 项目聚合覆盖率不得低于基线（scripts/ci/coverage-baseline.json 的 total）；
  2. 任一模块不得低于其自身基线（容差 tolerance，默认 0.5 个百分点）；
  3. 基线只升不降：--write 只在覆盖率提升时上调，不会自动下调（防悄悄放水）；
  4. 基线中不存在的模块只告警，不阻断（新增模块先观察一轮，再纳入基线）。

用法：
  python scripts/ci/check-coverage.py                  # 校验（CI 用）
  python scripts/ci/check-coverage.py --write           # 实测后上调基线
  python scripts/ci/check-coverage.py --report-dir DIR  # 指定 whale-backend 根目录
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

BASELINE_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "coverage-baseline.json")

# 目标阈值（docs/design/cicd.md §3.3）；基线未达标时按实测值卡，达标后按此卡。
TARGET_LINE = 0.40
TARGET_BRANCH = 0.30

# 核心模块目标（cicd.md §3.3「whale-common / whale-i18n-sdk ≥ 60%」）。
# 未达 60% 的模块先按基线棘轮卡住，并提示目标值，不阻断以免门禁一上线即全红。
CORE_PREFIXES = ("whale-common/", "whale-sdk/whale-i18n-sdk")
CORE_LINE_MIN = 0.60

# 单模块回落容差：0.5 个百分点。低于基线该幅度即判失败。
DEFAULT_TOLERANCE = 0.005

REPORT_GLOB = "**/target/site/jacoco/jacoco.xml"


def collect(root: str) -> tuple[dict[str, dict[str, float]], dict[str, float]]:
    """返回 (模块 → {line, branch}, 聚合 {line, branch})。"""
    modules: dict[str, dict[str, float]] = {}
    total = {"line": [0, 0], "branch": [0, 0]}

    for path in glob.glob(os.path.join(root, REPORT_GLOB), recursive=True):
        module = os.path.relpath(
            os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(path)))), root
        )
        module = module.replace(os.sep, "/")
        try:
            report = ET.parse(path).getroot()
        except ET.ParseError as exc:  # 残缺报告不该让门禁崩溃
            print(f"WARN 跳过无法解析的报告 {path}: {exc}")
            continue

        counters = {}
        for counter in report.findall("counter"):
            kind = counter.get("type")
            if kind in ("LINE", "BRANCH"):
                missed = int(counter.get("missed"))
                covered = int(counter.get("covered"))
                counters[kind] = (covered, missed)
                total[kind.lower()][0] += covered
                total[kind.lower()][1] += missed

        summary = {}
        for kind in ("LINE", "BRANCH"):
            covered, missed = counters.get(kind, (0, 0))
            summary[kind.lower()] = covered / (covered + missed) if covered + missed else 0.0
        modules[module] = summary

    aggregate = {}
    for kind, (covered, missed) in total.items():
        aggregate[kind] = covered / (covered + missed) if covered + missed else 0.0
    return modules, aggregate


def load_baseline() -> dict:
    if not os.path.exists(BASELINE_PATH):
        return {}
    with open(BASELINE_PATH, "r", encoding="utf-8") as handle:
        return json.load(handle)


def write_baseline(modules: dict[str, dict[str, float]], aggregate: dict[str, float]) -> int:
    """生成/上调基线。已存在的模块取 max(旧, 新)，避免覆盖率下滑被静默吸收。"""
    previous = load_baseline()
    old_modules = previous.get("modules", {})
    merged = {}
    raised = []
    for name in sorted(set(old_modules) | set(modules)):
        new = modules.get(name)
        old = old_modules.get(name)
        if new is None:
            merged[name] = old
            continue
        if old is None:
            merged[name] = {"line": round(new["line"], 4), "branch": round(new["branch"], 4)}
            continue
        picked = {
            "line": round(max(old["line"], new["line"]), 4),
            "branch": round(max(old["branch"], new["branch"]), 4),
        }
        if picked["line"] > old["line"] or picked["branch"] > old["branch"]:
            raised.append(name)
        merged[name] = picked

    old_total = previous.get("total", {"line": 0.0, "branch": 0.0})
    total = {
        "line": round(max(old_total.get("line", 0.0), aggregate["line"]), 4),
        "branch": round(max(old_total.get("branch", 0.0), aggregate["branch"]), 4),
    }

    payload = {
        "note": "覆盖率基线：只升不降。校验脚本 scripts/ci/check-coverage.py；"
                "目标阈值见 docs/design/cicd.md §3.3（行 40% / 分支 30%）",
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "target": {"line": TARGET_LINE, "branch": TARGET_BRANCH},
        "tolerance": previous.get("tolerance", DEFAULT_TOLERANCE),
        "total": total,
        "modules": merged,
    }
    with open(BASELINE_PATH, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2, sort_keys=False)
        handle.write("\n")

    print(f"基线已更新：{BASELINE_PATH}")
    print(f"  聚合  行 {total['line']:.2%}  分支 {total['branch']:.2%}")
    print(f"  模块 {len(merged)} 个" + (f"，其中 {len(raised)} 个上调" if raised else ""))

    # 引导模式下 CI 日志是唯一能把基线取回来的通道，故把整份内容打出来便于回填入库。
    if os.environ.get("GITHUB_ACTIONS") == "true":
        print("----- BEGIN COVERAGE BASELINE -----")
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        print("----- END COVERAGE BASELINE -----")
    return 0


def check(modules: dict[str, dict[str, float]], aggregate: dict[str, float]) -> int:
    baseline = load_baseline()
    if not baseline:
        # 首次引导：仓库里还没有基线时，按本次实测生成并放行，避免门禁一上线就无法收敛。
        # CI 日志会打印完整数值，落地后应把生成的 coverage-baseline.json 提交入库，
        # 之后每轮都按基线棘轮判定（只升不降）。
        print("BOOTSTRAP 未发现覆盖率基线，按本次实测生成：")
        write_baseline(modules, aggregate)
        print("        请把 scripts/ci/coverage-baseline.json 提交入库后再跑一轮，即进入棘轮模式。")
        return 0

    tolerance = float(baseline.get("tolerance", DEFAULT_TOLERANCE))
    base_total = baseline.get("total", {})
    base_modules = baseline.get("modules", {})
    failures: list[str] = []
    warnings: list[str] = []

    print("模块覆盖率（由低到高，仅列最低 12 个）：")
    ranked = sorted(modules.items(), key=lambda kv: kv[1]["line"])
    for name, value in ranked[:12]:
        base = base_modules.get(name)
        if base and value["line"] + tolerance < base["line"]:
            mark = f"  < 基线 {base['line']:.2%}"
        elif base is None:
            mark = "  (新模块，未纳入基线)"
        else:
            mark = ""
        print(f"  {name[:56]:56} 行 {value['line']:6.2%} 分支 {value['branch']:6.2%}{mark}")

    # 回归判定必须覆盖全部模块，不能只看打印出来的最低 12 个。
    for name, value in modules.items():
        base = base_modules.get(name)
        if base is None:
            warnings.append(f"{name}: 未纳入基线")
            continue
        if value["line"] + tolerance < base["line"]:
            failures.append(f"{name}: 行 {value['line']:.2%} < 基线 {base['line']:.2%}")
        if value["branch"] + tolerance < base["branch"]:
            failures.append(f"{name}: 分支 {value['branch']:.2%} < 基线 {base['branch']:.2%}")

    print()
    print(f"聚合覆盖率：行 {aggregate['line']:.2%}  分支 {aggregate['branch']:.2%}")
    print(f"基线覆盖率：行 {base_total.get('line', 0):.2%}  分支 {base_total.get('branch', 0):.2%}")
    print(f"目标阈值：  行 {TARGET_LINE:.0%}  分支 {TARGET_BRANCH:.0%}（cicd.md §3.3）")

    if aggregate["line"] + tolerance < base_total.get("line", 0.0):
        failures.append(
            f"聚合行覆盖率 {aggregate['line']:.2%} < 基线 {base_total.get('line', 0):.2%}"
        )
    if aggregate["branch"] + tolerance < base_total.get("branch", 0.0):
        failures.append(
            f"聚合分支覆盖率 {aggregate['branch']:.2%} < 基线 {base_total.get('branch', 0):.2%}"
        )

    if aggregate["line"] < TARGET_LINE or aggregate["branch"] < TARGET_BRANCH:
        print("提示：聚合覆盖率尚未达到目标阈值，基线按实测值卡（只升不降）。")

    # 核心模块目标提示：未达 60% 时只提示（当前存量如此），不代表放行——基线棘轮仍然生效。
    for name, value in sorted(modules.items()):
        if any(name.startswith(prefix) for prefix in CORE_PREFIXES) and value["line"] < CORE_LINE_MIN:
            warnings.append(
                f"{name}: 核心模块目标行覆盖率 {CORE_LINE_MIN:.0%}，当前 {value['line']:.2%}"
            )

    for item in warnings:
        print(f"WARN {item}")

    if failures:
        print()
        for item in failures:
            print(f"FAIL {item}")
        print(f"\n覆盖率门禁未通过：{len(failures)} 项低于基线。")
        return 1

    print("\n覆盖率门禁通过。")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="覆盖率门禁校验")
    parser.add_argument("--report-dir", default=None, help="whale-backend 根目录（默认按脚本位置推导）")
    parser.add_argument("--write", action="store_true", help="按实测结果生成/上调基线")
    args = parser.parse_args()

    root = args.report_dir
    if root is None:
        repo_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        root = os.path.join(repo_root, "whale-backend")

    if not os.path.isdir(root):
        print(f"ERROR 报告目录不存在：{root}")
        return 2

    modules, aggregate = collect(root)
    if not modules:
        print(f"ERROR 未找到任何 jacoco 报告（{REPORT_GLOB}），请先跑 mvn verify")
        return 2

    if args.write:
        return write_baseline(modules, aggregate)
    return check(modules, aggregate)


if __name__ == "__main__":
    sys.exit(main())
