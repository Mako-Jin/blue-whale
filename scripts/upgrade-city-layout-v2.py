# -*- coding: utf-8 -*-
"""把 city-layout.json 从 v1（8 坊 / 92-48-22）升级到 v2（12 坊 + 9 殿 / 110-58-30）。

背景
----
v1 的城郭只能容纳 8 个业务子系统，而平台实际有 21 个服务 + 10 个业务应用。
v2 按《2026-11-19-3d-capital-master-plan.md》扩容：

* 外城 92 → 110，内城 48 → 58，皇城 22 → 30，环路 68 → 76；
* 皇城从「1 主殿 + 2 配殿」扩成「1 主殿 + 8 中枢配殿」＝ 9 殿；
* 内城环带从 8 坊扩成 12 坊（每象限 3 坊，四个正方位留给御道）。

**顺序契约（务必遵守）**
----------------------
``gen-world-layout-sql.py`` 用 ``NODE_ID_BASE + 数组下标`` 作主键。因此本脚本
**只做原地改写 + 末尾追加，绝不删除或重排既有节点**——一旦删除中间节点，
后续所有节点主键整体前移，幂等守卫（``WHERE NOT EXISTS (id = ...)``）会认为
「这些 ID 不存在」而重复插入，库里就会出现同一个 node_key 的多个副本。

典籍阁 / 玉衡阁 因此不删，改为南广场两角的小阁（scale 0.5），给 8 中枢殿让位。

用法
----
    python scripts/upgrade-city-layout-v2.py            # 就地升级（幂等）
    python scripts/upgrade-city-layout-v2.py --check    # 只校验是否已是 v2
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAYOUT = ROOT / "whale-frontend/platform-apps/whale-world/src/world/city-layout.json"

# ---------------------------------------------------------------------------
# 1. 城郭几何
# ---------------------------------------------------------------------------
GEOMETRY_V2 = {
    "outerWall": 110,
    "innerWall": 58,
    "royalWall": 30,
    "ringRoadRadius": 76,
    "gateHalfWidth": {"outer": 8.5, "inner": 6.5, "royal": 5.5},
    "wallThickness": {"outer": 2, "inner": 1.5, "royal": 1.6},
}

SCALE_V2 = {
    "planePerUnit": 3.3333,
    "planeOrigin": 500,
    "comment": "世界坐标 ±150 映射到平面 0–1000；OUTER=110 落在 133–867，两侧留出外城景观余量",
}

# ---------------------------------------------------------------------------
# 2. 内城 12 坊（方位角从正北顺时针，15° 起每 30°，四个正方位留给御道）
# ---------------------------------------------------------------------------
RING_R = 76.0
PORTAL_R = 62.0
RING_START_DEG = 15.0
RING_STEP_DEG = 30.0

#: 坊定义，顺序即绕城顺时针一圈的顺序；每象限 3 坊。
DISTRICTS = [
    # 东南象限
    ("hubu-hall", "户部", "ERP · 企业资源", "erp", "/erp", "sys:erp:item:list"),
    ("gongbu-hall", "工部", "MES · 智能制造", "mes", "/mes", "sys:mes:workorder:list"),
    ("libu-hall", "吏部", "OA · 协同办公", "oa", "/oa", "sys:oa:leave:list"),
    # 东北象限
    ("libu-ritual-hall", "礼部", "CRM · 客户经营", "crm", "/crm", "sys:crm:browse"),
    ("bingbu-hall", "兵部", "IoT · 物联感知", "iot", "/iot", "sys:iot:device"),
    ("hanlin-academy", "翰林院", "DOC · 文档知识", "doc", "/doc", "sys:doc:space:list"),
    # 西北象限
    ("xingtan-hall", "杏坛", "LMS · 学习培训", "lms", "/lms", "sys:lms:browse"),
    ("shibosi-hall", "市舶司", "MALL · 商城交易", "mall", "/mall", "sys:mall:browse"),
    ("jiangzuo-bureau", "将作监", "PDM · 产品数据", "pdm", "/pdm", "sys:pdm:bom:list"),
    # 西南象限
    ("yingzao-bureau", "营造司", "PM · 工程项目", "pm", "/pm", "sys:pm:item:list"),
    ("mengfu-hall", "盟府", "CONTRACT · 合同印章", "contract", "/contract", "sys:contract:list"),
    ("xuanfu-court", "宣抚院", "PARTY · 党务党建", "party", "/party", "sys:party:browse"),
]

# ---------------------------------------------------------------------------
# 3. 皇城 9 中枢殿（主殿 + 8 配殿，东西两列各 4 座）
# ---------------------------------------------------------------------------
ROYAL_COLUMN_X = 20.5
ROYAL_COLUMN_Z = (-21.0, -7.0, 7.0, 21.0)

#: 8 配殿：(nodeKey, 名称, 服务副标题, icon, 列符号, z)
#: 目前只定位不接入（S5 才加 entry/portal），所以不带 entry。
ROYAL_SIDE_HALLS = [
    ("shumi-hall", "枢密殿", "AUTH · 权限认证", "auth", -1, ROYAL_COLUMN_Z[0]),
    ("duzhi-hall", "度支殿", "PAY · 支付结算", "pay", -1, ROYAL_COLUMN_Z[1]),
    ("cangshu-hall", "藏书殿", "FILE · 文件存储", "file", -1, ROYAL_COLUMN_Z[2]),
    ("shichen-hall", "时辰殿", "JOB · 定时任务", "job", -1, ROYAL_COLUMN_Z[3]),
    ("zhengshi-hall", "政事殿", "WORKFLOW · 工作流", "workflow", 1, ROYAL_COLUMN_Z[0]),
    ("kaogong-hall", "考功殿", "FORM · 表单引擎", "form", 1, ROYAL_COLUMN_Z[1]),
    ("tongzheng-hall", "通政殿", "MESSAGE · 消息通知", "message", 1, ROYAL_COLUMN_Z[2]),
    ("qiju-hall", "起居殿", "AUDIT · 审计留痕", "audit", 1, ROYAL_COLUMN_Z[3]),
]


def ring_point(radius: float, deg: float) -> tuple[float, float]:
    """方位角（正北为 0，顺时针）→ 世界坐标 (x, z)。

    世界 -z 为北，故 z 取 cos 正值时朝南，与 v1 的取法一致。
    """
    rad = math.radians(deg)
    return round(radius * math.sin(rad), 3), round(radius * math.cos(rad), 3)


def ring_yaw(deg: float) -> float:
    """朝向圆心的旋转角（度）。v1 的 22.5° 对应 -157.5°，即 -180 + 方位角。"""
    return round(-180.0 + deg, 1)


def upgrade(data: dict) -> dict:
    """就地改写 data 并返回；对已是 v2 的数据重复调用结果不变。"""
    data["scene"]["version"] = 2
    data["scene"]["description"] = (
        "蓝鲸城三维主场景：皇城·内城·外城三重城郭，皇城九中枢殿对应平台服务，"
        "内城十二坊对应业务子系统，外城诸司对应工具与内容服务"
    )
    data["geometry"] = GEOMETRY_V2
    data["scale"] = SCALE_V2

    buildings = data["buildings"]
    by_key = {b["nodeKey"]: b for b in buildings}

    # --- 3.1 皇城主殿：下移让出南广场，尺寸收敛到与新皇城相称 ---
    main = by_key["shushu-hall"]
    main["x"], main["z"] = 0, -8
    main["w"], main["d"], main["scale"] = 14, 11, 1.0
    main["portal"]["x"], main["portal"]["z"] = 0, 4
    main["collider"] = {"halfX": 11.5, "halfZ": 10.0}

    # --- 3.2 典籍阁 / 玉衡阁：移到南广场两角，缩成小阁（保留主键与顺序） ---
    for key, sx in (("dianji-pavilion", -1), ("yuheng-pavilion", 1)):
        pav = by_key[key]
        pav["x"], pav["z"] = round(sx * 10.5, 3), 20.0
        pav["w"], pav["d"], pav["scale"] = 6, 5, 0.5
        pav["rotationDeg"] = 0.0
        pav["collider"] = {"halfX": 3.8, "halfZ": 3.5}

    # --- 3.3 内城 12 坊：按方位角重算坐标、朝向与传送门 ---
    for idx, (key, name, sub, icon, path, perm) in enumerate(DISTRICTS):
        deg = RING_START_DEG + idx * RING_STEP_DEG
        x, z = ring_point(RING_R, deg)
        px, pz = ring_point(PORTAL_R, deg)

        node = by_key.get(key)
        if node is None:
            node = {
                "nodeKey": key,
                "parentKey": "inner-city",
                "nodeType": "BUILDING",
                "name": name,
                "sub": sub,
                "icon": icon,
                "x": x,
                "z": z,
                "y": 0,
                "w": 9,
                "d": 7,
                "scale": 0.95,
                "rotationDeg": ring_yaw(deg),
                "roofColor": "#2f6b5f",
                "bodyColor": "#8f3a2c",
                "modelKey": "palace-hall",
                "collider": {"halfX": 10, "halfZ": 9},
                "sortNo": 0,
                "entry": {"type": "SYSTEM", "path": path, "permission": perm},
                "portal": {
                    "x": px,
                    "z": pz,
                    "color": "#6fe3ff",
                    "label": name,
                    "subLabel": sub,
                },
            }
            buildings.append(node)
            by_key[key] = node
        else:
            node["x"], node["z"] = x, z
            node["rotationDeg"] = ring_yaw(deg)
            node["entry"] = {"type": "SYSTEM", "path": path, "permission": perm}
            node["portal"] = {
                "x": px,
                "z": pz,
                "color": "#6fe3ff",
                "label": name,
                "subLabel": sub,
            }

    # --- 3.4 皇城 8 配殿：追加到末尾（S5 才接入，此处只定位） ---
    # 碰撞盒按台基实际外扩量算：createPalace 的台基是 (w+9) × (d+9) 再乘 scale，
    # 取其一倍（而非 0.9 倍）即可贴住墙根。这里若沿用旧的 10×9 常数，
    # 东西两列间距会被吃到只剩 0.7m，玩家挤不过去。
    for key, name, sub, icon, side, z in ROYAL_SIDE_HALLS:
        w, d, scale = 8, 6, 0.72
        collider = {
            "halfX": round((w + 9) / 2 * scale, 1),
            "halfZ": round((d + 9) / 2 * scale, 1),
        }
        if key in by_key:
            node = by_key[key]
            node["x"], node["z"] = round(side * ROYAL_COLUMN_X, 3), z
            node["w"], node["d"], node["scale"] = w, d, scale
            node["collider"] = collider
            continue
        node = {
            "nodeKey": key,
            "parentKey": "royal-city",
            "nodeType": "BUILDING",
            "name": name,
            "sub": sub,
            "icon": icon,
            "x": round(side * ROYAL_COLUMN_X, 3),
            "z": z,
            "y": 0,
            "w": w,
            "d": d,
            "scale": scale,
            "rotationDeg": 0.0,
            "roofColor": "#3c6b5a",
            "bodyColor": "#8a4a30",
            "modelKey": "pavilion-side",
            "collider": {
                "halfX": round((w + 9) / 2 * scale, 1),
                "halfZ": round((d + 9) / 2 * scale, 1),
            },
        }
        buildings.append(node)
        by_key[key] = node

    # --- 3.5 sortNo 重排（树序：外城 → 内城 → 皇城 → 各坊 → 各殿） ---
    order = ["outer-city", "inner-city", "royal-city", "shushu-hall",
             "dianji-pavilion", "yuheng-pavilion"]
    order += [d[0] for d in DISTRICTS]
    order += [h[0] for h in ROYAL_SIDE_HALLS]
    missing = [k for k in by_key if k not in order]
    if missing:
        raise SystemExit("存在未登记的节点，拒绝重排 sortNo：%s" % missing)
    for i, key in enumerate(order):
        by_key[key]["sortNo"] = i + 1

    # 保持数组顺序不变（主键契约），但确保新节点已在末尾
    return data


def validate(data: dict) -> list[str]:
    """v2 不变量校验。"""
    errs: list[str] = []
    g = data["geometry"]
    if (g["outerWall"], g["innerWall"], g["royalWall"]) != (110, 58, 30):
        errs.append("geometry 未达到 110/58/30：%s" % g)

    buildings = data["buildings"]
    keys = [b["nodeKey"] for b in buildings]
    if len(keys) != len(set(keys)):
        errs.append("nodeKey 有重复")

    districts = [
        b for b in buildings
        if b.get("parentKey") == "inner-city" and b.get("nodeType") == "BUILDING"
    ]
    if len(districts) != 12:
        errs.append("内城坊数应为 12，实际 %d" % len(districts))

    royal = [
        b for b in buildings
        if b.get("parentKey") == "royal-city" and b.get("nodeType") == "BUILDING"
    ]
    if len(royal) != 11:
        errs.append("皇城节点应为 11（1 主殿 + 8 中枢殿 + 2 小阁），实际 %d" % len(royal))

    # 坊间最小间距（按台基外接尺寸估）
    ring = sorted(districts, key=lambda b: math.atan2(b["x"], b["z"]))
    for a, b in zip(ring, ring[1:] + ring[:1]):
        dist = math.hypot(a["x"] - b["x"], a["z"] - b["z"])
        if dist < 12:
            errs.append("坊间距过近：%s ↔ %s = %.2fm" % (a["nodeKey"], b["nodeKey"], dist))

    # 台基不得压内城墙，也不得压外城墙
    for b in districts:
        r = math.hypot(b["x"], b["z"])
        outer_edge = r + (b["d"] + 9) / 2 * b.get("scale", 1)
        inner_edge = r - (b["d"] + 9) / 2 * b.get("scale", 1)
        if inner_edge <= g["innerWall"] + g["wallThickness"]["inner"]:
            errs.append("%s 台基压到内城墙（内缘 %.1f）" % (b["nodeKey"], inner_edge))
        if outer_edge >= g["outerWall"] - g["wallThickness"]["outer"]:
            errs.append("%s 台基压到外城墙（外缘 %.1f）" % (b["nodeKey"], outer_edge))

    return errs


def main() -> int:
    parser = argparse.ArgumentParser(description="升级 city-layout.json 到 v2")
    parser.add_argument("--check", action="store_true", help="只校验，不写文件")
    args = parser.parse_args()

    data = json.loads(LAYOUT.read_text(encoding="utf-8"))

    if args.check:
        errs = validate(data)
        if errs:
            print("FAIL 布局未达 v2：")
            for e in errs:
                print("  - " + e)
            return 1
        print("PASS 布局已是 v2（节点 %d / 皇城 %d / 内城 %d）" % (
            len(data["buildings"]),
            sum(1 for b in data["buildings"] if b.get("parentKey") == "royal-city"),
            sum(1 for b in data["buildings"] if b.get("parentKey") == "inner-city"),
        ))
        return 0

    upgraded = upgrade(data)
    errs = validate(upgraded)
    if errs:
        print("FAIL 升级后校验未通过，未写入文件：")
        for e in errs:
            print("  - " + e)
        return 1

    LAYOUT.write_text(
        json.dumps(upgraded, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print("PASS 已写入 %s（节点 %d）" % (LAYOUT.relative_to(ROOT), len(upgraded["buildings"])))
    for b in upgraded["buildings"]:
        if b.get("parentKey") in ("royal-city", "inner-city"):
            print("  %-20s (%.1f, %.1f)" % (b["nodeKey"], b["x"], b["z"]))
    print("")
    print("下一步（JSON 变了 SQL 就漂移，必须重新生成）：")
    print("  python scripts/gen-world-layout-sql.py")
    print("  python scripts/gen-world-layout-sql.py --check   # CI 门禁")
    print("  node scripts/check-city-layout.mjs               # 几何自检")
    return 0


if __name__ == "__main__":
    sys.exit(main())
