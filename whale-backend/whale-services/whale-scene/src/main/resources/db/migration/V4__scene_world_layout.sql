-- =======================================================================
-- BlueWhale 蓝鲸城三维布局（whale-scene V4）
-- =======================================================================
-- 生成脚本 : scripts/gen-world-layout-sql.py（禁止手改本文件）
-- 数据源   : whale-frontend/platform-apps/whale-world/src/world/city-layout.json
-- 校验     : python scripts/gen-world-layout-sql.py --check（漂移即 FAIL）
--
-- 隔离口径 :
--   1) 三维场景使用独立 scene_key = bluewhale-city-3d，不触碰 2D 的 5001 蓝鲸城；
--   2) 三维表现字段独立成表 sc_node_world，不 ALTER 2D 的 sc_scene_node；
--   3) 本脚本只新增行与表，不含 DROP / TRUNCATE / DELETE，可重复执行。
--
-- 换算口径 : 平面坐标 = planeOrigin + 世界坐标 × planePerUnit
--            世界 ±150 → 平面 0–1000（外城墙 92 单位 → 平面 193–807）
-- =======================================================================

-- ---------------------------------------------------------------------
-- 1. 场景节点三维表现扩展（一节点至多一行；2D 侧完全无感）
--    model_asset_id 为空表示先用前端程序化几何兜底，GLB 资产到位后回填。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sc_node_world (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    scene_id            BIGINT        NOT NULL                COMMENT '所属场景ID',
    node_id             BIGINT        NOT NULL                COMMENT '场景节点ID',
    model_key           VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '模型标识（GLB 资产键）',
    model_asset_id      BIGINT        DEFAULT NULL            COMMENT 'GLB 资产ID（whale-file），空为程序化几何',
    scale_x             DECIMAL(6,3)  NOT NULL DEFAULT 1.000  COMMENT 'X 缩放',
    scale_y             DECIMAL(6,3)  NOT NULL DEFAULT 1.000  COMMENT 'Y 缩放',
    scale_z             DECIMAL(6,3)  NOT NULL DEFAULT 1.000  COMMENT 'Z 缩放',
    rotation_y_deg      DECIMAL(7,2)  NOT NULL DEFAULT 0.00   COMMENT '绕 Y 轴旋转（度，正北为 0）',
    elevation           DECIMAL(7,2)  NOT NULL DEFAULT 0.00   COMMENT '离地高度（米）',
    collider_half_x     DECIMAL(7,2)  NOT NULL DEFAULT 0.00   COMMENT '碰撞体 X 半长（米，0 表示不参与碰撞）',
    collider_half_z     DECIMAL(7,2)  NOT NULL DEFAULT 0.00   COMMENT '碰撞体 Z 半长（米）',
    roof_color          INT           DEFAULT NULL            COMMENT '屋顶色（0xRRGGBB 十进制）',
    body_color          INT           DEFAULT NULL            COMMENT '墙身色（0xRRGGBB 十进制）',
    lod_bias            DECIMAL(4,2)  NOT NULL DEFAULT 1.00   COMMENT 'LOD 偏置（>1 更早降级）',
    portal_kind         VARCHAR(16)   NOT NULL DEFAULT 'NONE' COMMENT '传送门类型：NONE / SCENE / SUBAPP / EXTERNAL',
    portal_x            DECIMAL(9,3)  DEFAULT NULL            COMMENT '传送门世界坐标 X',
    portal_z            DECIMAL(9,3)  DEFAULT NULL            COMMENT '传送门世界坐标 Z',
    portal_color        INT           DEFAULT NULL            COMMENT '传送门光色',
    portal_label        VARCHAR(64)   DEFAULT NULL            COMMENT '传送门主标签',
    portal_sublabel     VARCHAR(128)  DEFAULT NULL            COMMENT '传送门副标签',
    portal_main         TINYINT       NOT NULL DEFAULT 0      COMMENT '是否主传送门：1 是 / 0 否',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sc_node_world (tenant_id, node_id),
    KEY idx_sc_node_world_scene (tenant_id, scene_id)
) COMMENT='场景节点三维表现扩展';

-- ---------------------------------------------------------------------
-- 2. 三维场景（独立 scene_key，与 2D 场景 5001 并行存在）
-- ---------------------------------------------------------------------
INSERT INTO sc_scene (id, tenant_id, scene_key, name, description, status, layout_version, published_at, deleted)
SELECT 5301, 1, 'bluewhale-city-3d', '蓝鲸城（三维）', '蓝鲸城三维主场景：皇城·内城·外城三重城郭，八方宫殿对应业务子系统', 'PUBLISHED', 1, CURRENT_TIMESTAMP, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene WHERE tenant_id = 1 AND scene_key = 'bluewhale-city-3d');

-- ---------------------------------------------------------------------
-- 3. 节点树（区域 → 皇城建筑 → 八方宫殿）
--    coordinate_z 存离地高度取整，三维精确高度见 sc_node_world.elevation。
-- ---------------------------------------------------------------------
-- 外城（outer-city）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5310, 1, 5301, 'outer-city', NULL, 'REGION', '外城', 'city',
       500, 500, 0, 1, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5310);

-- 内城（inner-city）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5311, 1, 5301, 'inner-city', 'outer-city', 'REGION', '内城', 'city',
       500, 500, 0, 2, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5311);

-- 皇城（royal-city）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5312, 1, 5301, 'royal-city', 'inner-city', 'REGION', '皇城', 'crown',
       500, 500, 0, 3, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5312);

-- 数枢殿（shushu-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5313, 1, 5301, 'shushu-hall', 'royal-city', 'BUILDING', '数枢殿', 'data',
       500, 493, 0, 4, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5313);

-- 典籍阁（dianji-pavilion）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5314, 1, 5301, 'dianji-pavilion', 'royal-city', 'BUILDING', '典籍阁', 'book',
       450, 540, 0, 5, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5314);

-- 玉衡阁（yuheng-pavilion）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5315, 1, 5301, 'yuheng-pavilion', 'royal-city', 'BUILDING', '玉衡阁', 'compass',
       550, 540, 0, 6, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5315);

-- 户部（hubu-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5316, 1, 5301, 'hubu-hall', 'inner-city', 'BUILDING', '户部', 'erp',
       587, 709, 0, 10, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5316);

-- 工部（gongbu-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5317, 1, 5301, 'gongbu-hall', 'inner-city', 'BUILDING', '工部', 'mes',
       709, 587, 0, 11, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5317);

-- 吏部（libu-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5318, 1, 5301, 'libu-hall', 'inner-city', 'BUILDING', '吏部', 'oa',
       709, 413, 0, 12, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5318);

-- 礼部（libu-ritual-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5319, 1, 5301, 'libu-ritual-hall', 'inner-city', 'BUILDING', '礼部', 'crm',
       587, 291, 0, 13, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5319);

-- 兵部（bingbu-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5320, 1, 5301, 'bingbu-hall', 'inner-city', 'BUILDING', '兵部', 'iot',
       413, 291, 0, 14, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5320);

-- 翰林院（hanlin-academy）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5321, 1, 5301, 'hanlin-academy', 'inner-city', 'BUILDING', '翰林院', 'doc',
       291, 413, 0, 15, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5321);

-- 杏坛（xingtan-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5322, 1, 5301, 'xingtan-hall', 'inner-city', 'BUILDING', '杏坛', 'lms',
       291, 587, 0, 16, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5322);

-- 市舶司（shibosi-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5323, 1, 5301, 'shibosi-hall', 'inner-city', 'BUILDING', '市舶司', 'mall',
       413, 709, 0, 17, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5323);

-- ---------------------------------------------------------------------
-- 4. 二维入口绑定（三维宫殿 → 既有 2D 子应用路由，业务系统一行不改）
-- ---------------------------------------------------------------------
-- 数枢殿 → /portal
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5402, 1, 5313, 'SYSTEM', '/portal', NULL, 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5402);

-- 户部 → /erp
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5403, 1, 5316, 'SYSTEM', '/erp', 'sys:erp:item:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5403);

-- 工部 → /mes
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5404, 1, 5317, 'SYSTEM', '/mes', 'sys:mes:workorder:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5404);

-- 吏部 → /oa
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5405, 1, 5318, 'SYSTEM', '/oa', 'sys:oa:leave:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5405);

-- 礼部 → /crm
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5406, 1, 5319, 'SYSTEM', '/crm', 'sys:crm:browse', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5406);

-- 兵部 → /iot
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5407, 1, 5320, 'SYSTEM', '/iot', 'sys:iot:device', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5407);

-- 翰林院 → /doc
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5408, 1, 5321, 'KNOWLEDGE', '/doc', 'sys:doc:space:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5408);

-- 杏坛 → /lms
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5409, 1, 5322, 'SYSTEM', '/lms', 'sys:lms:browse', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5409);

-- 市舶司 → /mall
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5410, 1, 5323, 'SYSTEM', '/mall', 'sys:mall:browse', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5410);

-- ---------------------------------------------------------------------
-- 5. 三维表现（模型键 / 变换 / 碰撞体 / 配色 / 传送门）
-- ---------------------------------------------------------------------
-- 外城
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5510, 1, 5301, 5310, 'region-plain', NULL,
       1.000, 1.000, 1.000, 0.00, 0.00,
       0.00, 0.00, NULL, NULL, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5510);

-- 内城
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5511, 1, 5301, 5311, 'region-inner', NULL,
       1.000, 1.000, 1.000, 0.00, 0.00,
       0.00, 0.00, NULL, NULL, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5511);

-- 皇城
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5512, 1, 5301, 5312, 'region-royal', NULL,
       1.000, 1.000, 1.000, 0.00, 0.00,
       0.00, 0.00, NULL, NULL, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5512);

-- 数枢殿
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5513, 1, 5301, 5313, 'hall-royal', NULL,
       1.220, 1.220, 1.220, 0.00, 0.00,
       15.00, 13.00, 13214010, 9383716, 1.00,
       'SUBAPP', 0.000, 30.000, 16765286,
       '数枢殿', '核心数据 · 皇城', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5513);

-- 典籍阁
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5514, 1, 5301, 5314, 'pavilion-side', NULL,
       0.720, 0.720, 0.720, -28.60, 0.00,
       6.00, 5.50, 3959642, 9062960, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5514);

-- 玉衡阁
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5515, 1, 5301, 5315, 'pavilion-side', NULL,
       0.720, 0.720, 0.720, 28.60, 0.00,
       6.00, 5.50, 3959642, 9062960, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5515);

-- 户部
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5516, 1, 5301, 5316, 'palace-hall', NULL,
       0.950, 0.950, 0.950, -157.50, 0.00,
       10.00, 9.00, 3107679, 9386540, 1.00,
       'SUBAPP', 20.089, 48.502, 7332863,
       '户部', 'ERP · 企业资源', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5516);

-- 工部
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5517, 1, 5301, 5317, 'palace-hall', NULL,
       0.950, 0.950, 0.950, -112.50, 0.00,
       10.00, 9.00, 4153208, 8141616, 1.00,
       'SUBAPP', 48.502, 20.089, 7332863,
       '工部', 'MES · 智能制造', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5517);

-- 吏部
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5518, 1, 5301, 5318, 'palace-hall', NULL,
       0.950, 0.950, 0.950, -67.50, 0.00,
       10.00, 9.00, 4877194, 9386540, 1.00,
       'SUBAPP', 48.502, -20.089, 7332863,
       '吏部', 'OA · 协同办公', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5518);

-- 礼部
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5519, 1, 5301, 5319, 'palace-hall', NULL,
       0.950, 0.950, 0.950, -22.50, 0.00,
       10.00, 9.00, 7031416, 9059124, 1.00,
       'SUBAPP', 20.089, -48.502, 7332863,
       '礼部', 'CRM · 客户经营', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5519);

-- 兵部
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5520, 1, 5301, 5320, 'palace-hall', NULL,
       0.950, 0.950, 0.950, 22.50, 0.00,
       10.00, 9.00, 4871520, 7289392, 1.00,
       'SUBAPP', -20.089, -48.502, 7332863,
       '兵部', 'IoT · 物联感知', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5520);

-- 翰林院
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5521, 1, 5301, 5321, 'palace-hall', NULL,
       0.950, 0.950, 0.950, 67.50, 0.00,
       10.00, 9.00, 2906706, 9062960, 1.00,
       'SUBAPP', -48.502, -20.089, 7332863,
       '翰林院', 'DOC · 文档知识', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5521);

-- 杏坛
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5522, 1, 5301, 5322, 'palace-hall', NULL,
       0.950, 0.950, 0.950, 112.50, 0.00,
       10.00, 9.00, 9067054, 9386540, 1.00,
       'SUBAPP', -48.502, 20.089, 7332863,
       '杏坛', 'LMS · 学习培训', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5522);

-- 市舶司
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5523, 1, 5301, 5323, 'palace-hall', NULL,
       0.950, 0.950, 0.950, 157.50, 0.00,
       10.00, 9.00, 13214010, 9059124, 1.00,
       'SUBAPP', -20.089, 48.502, 7332863,
       '市舶司', 'MALL · 商城交易', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5523);

-- =======================================================================
-- 统计：场景 1 / 节点 14 / 入口绑定 9 / 三维表现 14
-- =======================================================================
