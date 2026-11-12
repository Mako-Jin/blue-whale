#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""由城池布局单一数据源生成 whale-scene V4 迁移脚本。

数据源：whale-frontend/platform-apps/whale-world/src/world/city-layout.json
产物  ：whale-backend/whale-services/whale-scene/src/main/resources/db/migration/V4__scene_world_layout.sql

设计要点
--------
1. 三维布局只允许改 JSON，SQL 一律由本脚本生成，避免「前端坐标」与「库里坐标」
   两套数字各改各的（这正是本项目历史上最喜欢的静默失效模式）。
2. 生成物是幂等的：建表用 CREATE TABLE IF NOT EXISTS，种子用
   INSERT ... SELECT ... WHERE NOT EXISTS，符合 REQ-SEC-022（禁 DROP/TRUNCATE/DELETE）。
3. 三维扩展独立成表 sc_node_world，不 ALTER 2D 的 sc_scene_node，
   2D 服务与既有查询完全不知道它存在（零侵入）。
4. 三维场景使用独立 scene_key（bluewhale-city-3d），不往 2D 的 5001 场景里塞节点，
   2D 城池导航渲染结果不变。

用法
----
    python scripts/gen-world-layout-sql.py           # 生成/覆盖 SQL
    python scripts/gen-world-layout-sql.py --check   # 只比对，漂移时 rc=1（供 CI 门禁）
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAYOUT_PATH = ROOT / "whale-frontend/platform-apps/whale-world/src/world/city-layout.json"
OUT_PATH = (
    ROOT
    / "whale-backend/whale-services/whale-scene/src/main/resources/db/migration/V4__scene_world_layout.sql"
)

# 主键号段（与 V1 基线的 5001/51xx/52xx 错开，避免撞主键导致整条静默跳过）
SCENE_ID = 5301
NODE_ID_BASE = 5310
BINDING_ID_BASE = 5401
WORLD_ID_BASE = 5510
TENANT_ID = 1


def q(value: str) -> str:
    """SQL 单引号转义。"""
    return "'" + str(value).replace("'", "''") + "'"


def num(value: float, digits: int = 3) -> str:
    """定点格式化，保证生成物稳定（不随浮点表示漂移）。"""
    return f"{round(float(value), digits):.{digits}f}"


def plane(world: float, layout: dict) -> int:
    """世界坐标 → 2D 平面坐标（0–1000），与前端一致。"""
    scale = layout["scale"]
    return int(round(scale["planeOrigin"] + float(world) * scale["planePerUnit"]))


def parse_color(value: str | None) -> int | None:
    """#rrggbb → 十进制（前端 three.js 需要 number）。"""
    if not value:
        return None
    hexpart = value.lstrip("#")
    return int(hexpart, 16)


def build_sql(layout: dict) -> str:
    scene = layout["scene"]
    geometry = layout["geometry"]
    buildings = layout["buildings"]

    out: list[str] = []
    add = out.append

    add("-- " + "=" * 71)
    add("-- BlueWhale 蓝鲸城三维布局（whale-scene V4）")
    add("-- " + "=" * 71)
    add("-- 生成脚本 : scripts/gen-world-layout-sql.py（禁止手改本文件）")
    add("-- 数据源   : whale-frontend/platform-apps/whale-world/src/world/city-layout.json")
    add("-- 校验     : python scripts/gen-world-layout-sql.py --check（漂移即 FAIL）")
    add("--")
    add("-- 隔离口径 :")
    add(f"--   1) 三维场景使用独立 scene_key = {scene['key']}，不触碰 2D 的 5001 蓝鲸城；")
    add("--   2) 三维表现字段独立成表 sc_node_world，不 ALTER 2D 的 sc_scene_node；")
    add("--   3) 本脚本只新增行与表，不含 DROP / TRUNCATE / DELETE，可重复执行。")
    add("--")
    add("-- 换算口径 : 平面坐标 = planeOrigin + 世界坐标 × planePerUnit")
    add(f"--            世界 ±150 → 平面 0–1000（外城墙 {geometry['outerWall']} 单位 → 平面 "
        f"{plane(-geometry['outerWall'], layout)}–{plane(geometry['outerWall'], layout)}）")
    add("-- " + "=" * 71)
    add("")

    # ---- 1. 三维表现扩展表 ----
    add("-- ---------------------------------------------------------------------")
    add("-- 1. 场景节点三维表现扩展（一节点至多一行；2D 侧完全无感）")
    add("--    model_asset_id 为空表示先用前端程序化几何兜底，GLB 资产到位后回填。")
    add("-- ---------------------------------------------------------------------")
    add("CREATE TABLE IF NOT EXISTS sc_node_world (")
    add("    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',")
    add("    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',")
    add("    scene_id            BIGINT        NOT NULL                COMMENT '所属场景ID',")
    add("    node_id             BIGINT        NOT NULL                COMMENT '场景节点ID',")
    add("    model_key           VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '模型标识（GLB 资产键）',")
    add("    model_asset_id      BIGINT        DEFAULT NULL            COMMENT 'GLB 资产ID（whale-file），空为程序化几何',")
    add("    scale_x             DECIMAL(6,3)  NOT NULL DEFAULT 1.000  COMMENT 'X 缩放',")
    add("    scale_y             DECIMAL(6,3)  NOT NULL DEFAULT 1.000  COMMENT 'Y 缩放',")
    add("    scale_z             DECIMAL(6,3)  NOT NULL DEFAULT 1.000  COMMENT 'Z 缩放',")
    add("    rotation_y_deg      DECIMAL(7,2)  NOT NULL DEFAULT 0.00   COMMENT '绕 Y 轴旋转（度，正北为 0）',")
    add("    elevation           DECIMAL(7,2)  NOT NULL DEFAULT 0.00   COMMENT '离地高度（米）',")
    add("    collider_half_x     DECIMAL(7,2)  NOT NULL DEFAULT 0.00   COMMENT '碰撞体 X 半长（米，0 表示不参与碰撞）',")
    add("    collider_half_z     DECIMAL(7,2)  NOT NULL DEFAULT 0.00   COMMENT '碰撞体 Z 半长（米）',")
    add("    roof_color          INT           DEFAULT NULL            COMMENT '屋顶色（0xRRGGBB 十进制）',")
    add("    body_color          INT           DEFAULT NULL            COMMENT '墙身色（0xRRGGBB 十进制）',")
    add("    lod_bias            DECIMAL(4,2)  NOT NULL DEFAULT 1.00   COMMENT 'LOD 偏置（>1 更早降级）',")
    add("    portal_kind         VARCHAR(16)   NOT NULL DEFAULT 'NONE' COMMENT '传送门类型：NONE / SCENE / SUBAPP / EXTERNAL',")
    add("    portal_x            DECIMAL(9,3)  DEFAULT NULL            COMMENT '传送门世界坐标 X',")
    add("    portal_z            DECIMAL(9,3)  DEFAULT NULL            COMMENT '传送门世界坐标 Z',")
    add("    portal_color        INT           DEFAULT NULL            COMMENT '传送门光色',")
    add("    portal_label        VARCHAR(64)   DEFAULT NULL            COMMENT '传送门主标签',")
    add("    portal_sublabel     VARCHAR(128)  DEFAULT NULL            COMMENT '传送门副标签',")
    add("    portal_main         TINYINT       NOT NULL DEFAULT 0      COMMENT '是否主传送门：1 是 / 0 否',")
    add("    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',")
    add("    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',")
    add("    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',")
    add("    PRIMARY KEY (id),")
    add("    UNIQUE KEY uk_sc_node_world (tenant_id, node_id),")
    add("    KEY idx_sc_node_world_scene (tenant_id, scene_id)")
    add(") COMMENT='场景节点三维表现扩展';")
    add("")

    # ---- 2. 场景 ----
    add("-- ---------------------------------------------------------------------")
    add("-- 2. 三维场景（独立 scene_key，与 2D 场景 5001 并行存在）")
    add("-- ---------------------------------------------------------------------")
    add("INSERT INTO sc_scene (id, tenant_id, scene_key, name, description, status, layout_version, published_at, deleted)")
    add("SELECT %d, %d, %s, %s, %s, 'PUBLISHED', %d, CURRENT_TIMESTAMP, 0"
        % (SCENE_ID, TENANT_ID, q(scene["key"]), q(scene["name"]), q(scene["description"]), scene["version"]))
    add("FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene WHERE tenant_id = %d AND scene_key = %s);"
        % (TENANT_ID, q(scene["key"])))
    add("")

    # ---- 3. 节点 ----
    add("-- ---------------------------------------------------------------------")
    add("-- 3. 节点树（区域 → 皇城建筑 → 八方宫殿）")
    add("--    coordinate_z 存离地高度取整，三维精确高度见 sc_node_world.elevation。")
    add("-- ---------------------------------------------------------------------")
    for idx, b in enumerate(buildings):
        node_id = NODE_ID_BASE + idx
        parent = "NULL" if b.get("parentKey") is None else q(b["parentKey"])
        add("-- %s（%s）" % (b["name"], b["nodeKey"]))
        add("INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,")
        add("                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)")
        add("SELECT %d, %d, %d, %s, %s, %s, %s, %s," % (
            node_id, TENANT_ID, SCENE_ID, q(b["nodeKey"]), parent, q(b["nodeType"]), q(b["name"]),
            q(b.get("icon") or "")))
        add("       %d, %d, %d, %d, 'ENABLED', 0" % (
            plane(b["x"], layout), plane(b["z"], layout), int(round(float(b.get("y") or 0))),
            b.get("sortNo", idx + 1)))
        add("FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = %d);" % node_id)
        add("")

    # ---- 4. 二维入口绑定 ----
    add("-- ---------------------------------------------------------------------")
    add("-- 4. 二维入口绑定（三维宫殿 → 既有 2D 子应用路由，业务系统一行不改）")
    add("-- ---------------------------------------------------------------------")
    binding_seq = 0
    for idx, b in enumerate(buildings):
        entry = b.get("entry")
        if not entry:
            continue
        binding_seq += 1
        binding_id = BINDING_ID_BASE + binding_seq
        node_id = NODE_ID_BASE + idx
        perm = "NULL" if entry.get("permission") is None else q(entry["permission"])
        add("-- %s → %s" % (b["name"], entry["path"]))
        add("INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)")
        add("SELECT %d, %d, %d, %s, %s, %s, 1, 0" % (
            binding_id, TENANT_ID, node_id, q(entry["type"]), q(entry["path"]), perm))
        add("FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = %d);" % binding_id)
        add("")

    # ---- 5. 三维表现 ----
    add("-- ---------------------------------------------------------------------")
    add("-- 5. 三维表现（模型键 / 变换 / 碰撞体 / 配色 / 传送门）")
    add("-- ---------------------------------------------------------------------")
    for idx, b in enumerate(buildings):
        node_id = NODE_ID_BASE + idx
        world_id = WORLD_ID_BASE + idx
        scale = float(b.get("scale") or 1.0)
        col = b.get("collider") or {}
        roof = parse_color(b.get("roofColor"))
        body = parse_color(b.get("bodyColor"))
        portal = b.get("portal") or {}
        portal_kind = "NONE"
        if portal:
            portal_kind = "SUBAPP" if b.get("entry") else "SCENE"
        add("-- %s" % b["name"])
        add("INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,")
        add("                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,")
        add("                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,")
        add("                            portal_kind, portal_x, portal_z, portal_color,")
        add("                            portal_label, portal_sublabel, portal_main, deleted)")
        add("SELECT %d, %d, %d, %d, %s, NULL," % (
            world_id, TENANT_ID, SCENE_ID, node_id, q(b.get("modelKey") or "")))
        add("       %s, %s, %s, %s, %s," % (
            num(scale), num(scale), num(scale), num(b.get("rotationDeg") or 0, 2), num(b.get("y") or 0, 2)))
        add("       %s, %s, %s, %s, 1.00," % (
            num(col.get("halfX") or 0, 2), num(col.get("halfZ") or 0, 2),
            "NULL" if roof is None else roof, "NULL" if body is None else body))
        add("       %s, %s, %s, %s," % (
            q(portal_kind),
            "NULL" if not portal else num(portal["x"]),
            "NULL" if not portal else num(portal["z"]),
            "NULL" if not portal else parse_color(portal.get("color"))))
        add("       %s, %s, %s, 0" % (
            "NULL" if not portal else q(portal["label"]),
            "NULL" if not portal else q(portal["subLabel"]),
            1 if portal.get("main") else 0))
        add("FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = %d);" % world_id)
        add("")

    add("-- " + "=" * 71)
    add("-- 统计：场景 1 / 节点 %d / 入口绑定 %d / 三维表现 %d" % (len(buildings), binding_seq, len(buildings)))
    add("-- " + "=" * 71)
    add("")
    return "\n".join(out)


def validate(sql: str, layout: dict) -> list[str]:
    """静态不变量校验：破坏性语句 / 幂等守卫 / 节点与三维行数配平。

    SQL 语法本身由 MySQL 在迁移时兜底；这里守的是「语义契约」，
    因为幂等与禁破坏性语句是本仓库的硬约定（REQ-SEC-022），
    而幂等守卫一旦漏写，重跑迁移就是主键冲突或脏数据。
    """
    problems: list[str] = []
    lines = sql.split("\n")

    for lineno, line in enumerate(lines, start=1):
        statement = line.strip().upper()
        if statement.startswith("--") or not statement:
            continue
        for forbidden in ("DROP ", "TRUNCATE ", "DELETE "):
            if statement.startswith(forbidden):
                problems.append(f"第 {lineno} 行含破坏性语句 {forbidden.strip()}")

    # 每个 INSERT 块必须以 WHERE NOT EXISTS 收尾（幂等守卫）
    insert_blocks = 0
    guarded = 0
    for line in lines:
        if line.upper().startswith("INSERT INTO"):
            insert_blocks += 1
        elif line.strip().upper().startswith("FROM DUAL WHERE NOT EXISTS"):
            guarded += 1
    if insert_blocks != guarded:
        problems.append(f"INSERT 块 {insert_blocks} 个，但幂等守卫只有 {guarded} 个")

    if not any("CREATE TABLE IF NOT EXISTS sc_node_world" in line for line in lines):
        problems.append("缺少 sc_node_world 建表语句")

    # 行数配平：每个建筑各一条节点行与一条三维表现行
    expected = len(layout["buildings"])
    node_rows = sum(1 for line in lines if line.startswith("INSERT INTO sc_scene_node"))
    world_rows = sum(1 for line in lines if line.startswith("INSERT INTO sc_node_world"))
    if node_rows != expected:
        problems.append(f"节点行数 {node_rows} 与布局建筑数 {expected} 不配平")
    if world_rows != expected:
        problems.append(f"三维表现行数 {world_rows} 与布局建筑数 {expected} 不配平")

    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description="由 city-layout.json 生成 whale-scene V4 迁移脚本")
    parser.add_argument("--check", action="store_true", help="只比对生成物是否最新，漂移返回 1")
    args = parser.parse_args()

    if not LAYOUT_PATH.exists():
        print("FAIL 找不到布局数据源: %s" % LAYOUT_PATH, file=sys.stderr)
        return 2

    layout = json.loads(LAYOUT_PATH.read_text(encoding="utf-8"))
    sql = build_sql(layout).replace("\r\n", "\n")

    if args.check:
        if not OUT_PATH.exists():
            print("FAIL 迁移脚本不存在，请运行 python scripts/gen-world-layout-sql.py", file=sys.stderr)
            return 1
        current = OUT_PATH.read_text(encoding="utf-8").replace("\r\n", "\n")
        if current != sql:
            print("FAIL 迁移脚本与 city-layout.json 不一致（已漂移）", file=sys.stderr)
            print("     修复：python scripts/gen-world-layout-sql.py", file=sys.stderr)
            return 1
        problems = validate(current, layout)
        if problems:
            print("FAIL 迁移脚本不满足不变量：", file=sys.stderr)
            for item in problems:
                print("     - " + item, file=sys.stderr)
            return 1
        print("PASS 三维布局迁移脚本与数据源一致（节点 %d）" % len(layout["buildings"]))
        return 0

    problems = validate(sql, layout)
    if problems:
        print("FAIL 生成结果不满足不变量：", file=sys.stderr)
        for item in problems:
            print("     - " + item, file=sys.stderr)
        return 1

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(sql, encoding="utf-8", newline="\n")
    print("生成完成: %s" % OUT_PATH.as_posix())
    print("节点 %d 个，入口绑定 %d 个" % (
        len(layout["buildings"]),
        sum(1 for b in layout["buildings"] if b.get("entry")),
    ))
    return 0


if __name__ == "__main__":
    sys.exit(main())
