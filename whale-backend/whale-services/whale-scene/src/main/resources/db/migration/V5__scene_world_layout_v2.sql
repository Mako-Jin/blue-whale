-- =======================================================================
-- BlueWhale 蓝鲸城三维布局 v2（whale-scene V5）
-- =======================================================================
-- 生成脚本 : scripts/gen-world-layout-sql.py（禁止手改本文件）
-- 数据源   : whale-frontend/platform-apps/whale-world/src/world/city-layout.json
-- 校验     : python scripts/gen-world-layout-sql.py --check（漂移即 FAIL）
--
-- 与 V4 的关系 :
--   V4 是 v1 布局（8 坊 / 92-48-22），已冻结，不做任何修改；
--   本脚本是 v2 布局（12 坊 + 皇城 9 殿 / 110-58-30）的增量迁移。
--   每条「INSERT ... WHERE NOT EXISTS」负责补新节点，紧随其后的
--   「UPDATE ... WHERE node_key = ...」负责把既有节点的坐标改到位。
--   因此全新库（V1→V5）与老库（V4→V5）最终状态完全一致。
--
-- 隔离口径 :
--   1) 三维场景使用独立 scene_key = bluewhale-city-3d，不触碰 2D 的 5001 蓝鲸城；
--   2) 三维表现字段独立成表 sc_node_world，不 ALTER 2D 的 sc_scene_node；
--   3) 本脚本只新增/更新行与表，不含 DROP / TRUNCATE / DELETE，可重复执行。
--
-- 换算口径 : 平面坐标 = planeOrigin + 世界坐标 × planePerUnit
--            世界 ±150 → 平面 0–1000（外城墙 110 单位 → 平面 133–867）
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
SELECT 5301, 1, 'bluewhale-city-3d', '蓝鲸城（三维）', '蓝鲸城三维主场景：皇城·内城·外城三重城郭，皇城九中枢殿对应平台服务，内城十二坊对应业务子系统，外城诸司对应工具与内容服务', 'PUBLISHED', 2, CURRENT_TIMESTAMP, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene WHERE tenant_id = 1 AND scene_key = 'bluewhale-city-3d');

UPDATE sc_scene SET name = '蓝鲸城（三维）', description = '蓝鲸城三维主场景：皇城·内城·外城三重城郭，皇城九中枢殿对应平台服务，内城十二坊对应业务子系统，外城诸司对应工具与内容服务', layout_version = 2, published_at = CURRENT_TIMESTAMP
 WHERE tenant_id = 1 AND scene_key = 'bluewhale-city-3d' AND deleted = 0;

-- ---------------------------------------------------------------------
-- 3. 节点树（区域 → 皇城九殿 → 内城十二坊）
--    coordinate_z 存离地高度取整，三维精确高度见 sc_node_world.elevation。
--    每条 INSERT 之后紧跟同一条 UPDATE：新库走 INSERT，老库走 UPDATE。
-- ---------------------------------------------------------------------
-- 外城（outer-city）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5310, 1, 5301, 'outer-city', NULL, 'REGION', '外城', 'city',
       500, 500, 0, 1, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5310);
UPDATE sc_scene_node SET parent_key = NULL, node_type = 'REGION', name = '外城', icon = 'city',
       coordinate_x = 500, coordinate_y = 500, coordinate_z = 0, sort_no = 1,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'outer-city';

-- 内城（inner-city）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5311, 1, 5301, 'inner-city', 'outer-city', 'REGION', '内城', 'city',
       500, 500, 0, 2, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5311);
UPDATE sc_scene_node SET parent_key = 'outer-city', node_type = 'REGION', name = '内城', icon = 'city',
       coordinate_x = 500, coordinate_y = 500, coordinate_z = 0, sort_no = 2,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'inner-city';

-- 皇城（royal-city）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5312, 1, 5301, 'royal-city', 'inner-city', 'REGION', '皇城', 'crown',
       500, 500, 0, 3, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5312);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'REGION', name = '皇城', icon = 'crown',
       coordinate_x = 500, coordinate_y = 500, coordinate_z = 0, sort_no = 3,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'royal-city';

-- 数枢殿（shushu-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5313, 1, 5301, 'shushu-hall', 'royal-city', 'BUILDING', '数枢殿', 'data',
       500, 473, 0, 4, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5313);
UPDATE sc_scene_node SET parent_key = 'royal-city', node_type = 'BUILDING', name = '数枢殿', icon = 'data',
       coordinate_x = 500, coordinate_y = 473, coordinate_z = 0, sort_no = 4,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'shushu-hall';

-- 典籍阁（dianji-pavilion）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5314, 1, 5301, 'dianji-pavilion', 'royal-city', 'BUILDING', '典籍阁', 'book',
       465, 567, 0, 5, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5314);
UPDATE sc_scene_node SET parent_key = 'royal-city', node_type = 'BUILDING', name = '典籍阁', icon = 'book',
       coordinate_x = 465, coordinate_y = 567, coordinate_z = 0, sort_no = 5,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'dianji-pavilion';

-- 玉衡阁（yuheng-pavilion）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5315, 1, 5301, 'yuheng-pavilion', 'royal-city', 'BUILDING', '玉衡阁', 'compass',
       535, 567, 0, 6, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5315);
UPDATE sc_scene_node SET parent_key = 'royal-city', node_type = 'BUILDING', name = '玉衡阁', icon = 'compass',
       coordinate_x = 535, coordinate_y = 567, coordinate_z = 0, sort_no = 6,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'yuheng-pavilion';

-- 户部（hubu-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5316, 1, 5301, 'hubu-hall', 'inner-city', 'BUILDING', '户部', 'erp',
       566, 745, 0, 7, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5316);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'BUILDING', name = '户部', icon = 'erp',
       coordinate_x = 566, coordinate_y = 745, coordinate_z = 0, sort_no = 7,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'hubu-hall';

-- 工部（gongbu-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5317, 1, 5301, 'gongbu-hall', 'inner-city', 'BUILDING', '工部', 'mes',
       679, 679, 0, 8, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5317);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'BUILDING', name = '工部', icon = 'mes',
       coordinate_x = 679, coordinate_y = 679, coordinate_z = 0, sort_no = 8,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'gongbu-hall';

-- 吏部（libu-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5318, 1, 5301, 'libu-hall', 'inner-city', 'BUILDING', '吏部', 'oa',
       745, 566, 0, 9, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5318);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'BUILDING', name = '吏部', icon = 'oa',
       coordinate_x = 745, coordinate_y = 566, coordinate_z = 0, sort_no = 9,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'libu-hall';

-- 礼部（libu-ritual-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5319, 1, 5301, 'libu-ritual-hall', 'inner-city', 'BUILDING', '礼部', 'crm',
       745, 434, 0, 10, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5319);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'BUILDING', name = '礼部', icon = 'crm',
       coordinate_x = 745, coordinate_y = 434, coordinate_z = 0, sort_no = 10,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'libu-ritual-hall';

-- 兵部（bingbu-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5320, 1, 5301, 'bingbu-hall', 'inner-city', 'BUILDING', '兵部', 'iot',
       679, 321, 0, 11, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5320);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'BUILDING', name = '兵部', icon = 'iot',
       coordinate_x = 679, coordinate_y = 321, coordinate_z = 0, sort_no = 11,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'bingbu-hall';

-- 翰林院（hanlin-academy）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5321, 1, 5301, 'hanlin-academy', 'inner-city', 'BUILDING', '翰林院', 'doc',
       566, 255, 0, 12, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5321);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'BUILDING', name = '翰林院', icon = 'doc',
       coordinate_x = 566, coordinate_y = 255, coordinate_z = 0, sort_no = 12,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'hanlin-academy';

-- 杏坛（xingtan-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5322, 1, 5301, 'xingtan-hall', 'inner-city', 'BUILDING', '杏坛', 'lms',
       434, 255, 0, 13, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5322);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'BUILDING', name = '杏坛', icon = 'lms',
       coordinate_x = 434, coordinate_y = 255, coordinate_z = 0, sort_no = 13,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'xingtan-hall';

-- 市舶司（shibosi-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5323, 1, 5301, 'shibosi-hall', 'inner-city', 'BUILDING', '市舶司', 'mall',
       321, 321, 0, 14, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5323);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'BUILDING', name = '市舶司', icon = 'mall',
       coordinate_x = 321, coordinate_y = 321, coordinate_z = 0, sort_no = 14,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'shibosi-hall';

-- 将作监（jiangzuo-bureau）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5324, 1, 5301, 'jiangzuo-bureau', 'inner-city', 'BUILDING', '将作监', 'pdm',
       255, 434, 0, 15, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5324);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'BUILDING', name = '将作监', icon = 'pdm',
       coordinate_x = 255, coordinate_y = 434, coordinate_z = 0, sort_no = 15,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'jiangzuo-bureau';

-- 营造司（yingzao-bureau）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5325, 1, 5301, 'yingzao-bureau', 'inner-city', 'BUILDING', '营造司', 'pm',
       255, 566, 0, 16, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5325);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'BUILDING', name = '营造司', icon = 'pm',
       coordinate_x = 255, coordinate_y = 566, coordinate_z = 0, sort_no = 16,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'yingzao-bureau';

-- 盟府（mengfu-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5326, 1, 5301, 'mengfu-hall', 'inner-city', 'BUILDING', '盟府', 'contract',
       321, 679, 0, 17, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5326);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'BUILDING', name = '盟府', icon = 'contract',
       coordinate_x = 321, coordinate_y = 679, coordinate_z = 0, sort_no = 17,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'mengfu-hall';

-- 宣抚院（xuanfu-court）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5327, 1, 5301, 'xuanfu-court', 'inner-city', 'BUILDING', '宣抚院', 'party',
       434, 745, 0, 18, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5327);
UPDATE sc_scene_node SET parent_key = 'inner-city', node_type = 'BUILDING', name = '宣抚院', icon = 'party',
       coordinate_x = 434, coordinate_y = 745, coordinate_z = 0, sort_no = 18,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'xuanfu-court';

-- 枢密殿（shumi-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5328, 1, 5301, 'shumi-hall', 'royal-city', 'BUILDING', '枢密殿', 'auth',
       432, 430, 0, 19, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5328);
UPDATE sc_scene_node SET parent_key = 'royal-city', node_type = 'BUILDING', name = '枢密殿', icon = 'auth',
       coordinate_x = 432, coordinate_y = 430, coordinate_z = 0, sort_no = 19,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'shumi-hall';

-- 度支殿（duzhi-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5329, 1, 5301, 'duzhi-hall', 'royal-city', 'BUILDING', '度支殿', 'pay',
       432, 477, 0, 20, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5329);
UPDATE sc_scene_node SET parent_key = 'royal-city', node_type = 'BUILDING', name = '度支殿', icon = 'pay',
       coordinate_x = 432, coordinate_y = 477, coordinate_z = 0, sort_no = 20,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'duzhi-hall';

-- 藏书殿（cangshu-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5330, 1, 5301, 'cangshu-hall', 'royal-city', 'BUILDING', '藏书殿', 'file',
       432, 523, 0, 21, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5330);
UPDATE sc_scene_node SET parent_key = 'royal-city', node_type = 'BUILDING', name = '藏书殿', icon = 'file',
       coordinate_x = 432, coordinate_y = 523, coordinate_z = 0, sort_no = 21,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'cangshu-hall';

-- 时辰殿（shichen-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5331, 1, 5301, 'shichen-hall', 'royal-city', 'BUILDING', '时辰殿', 'job',
       432, 570, 0, 22, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5331);
UPDATE sc_scene_node SET parent_key = 'royal-city', node_type = 'BUILDING', name = '时辰殿', icon = 'job',
       coordinate_x = 432, coordinate_y = 570, coordinate_z = 0, sort_no = 22,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'shichen-hall';

-- 政事殿（zhengshi-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5332, 1, 5301, 'zhengshi-hall', 'royal-city', 'BUILDING', '政事殿', 'workflow',
       568, 430, 0, 23, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5332);
UPDATE sc_scene_node SET parent_key = 'royal-city', node_type = 'BUILDING', name = '政事殿', icon = 'workflow',
       coordinate_x = 568, coordinate_y = 430, coordinate_z = 0, sort_no = 23,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'zhengshi-hall';

-- 考功殿（kaogong-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5333, 1, 5301, 'kaogong-hall', 'royal-city', 'BUILDING', '考功殿', 'form',
       568, 477, 0, 24, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5333);
UPDATE sc_scene_node SET parent_key = 'royal-city', node_type = 'BUILDING', name = '考功殿', icon = 'form',
       coordinate_x = 568, coordinate_y = 477, coordinate_z = 0, sort_no = 24,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'kaogong-hall';

-- 通政殿（tongzheng-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5334, 1, 5301, 'tongzheng-hall', 'royal-city', 'BUILDING', '通政殿', 'message',
       568, 523, 0, 25, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5334);
UPDATE sc_scene_node SET parent_key = 'royal-city', node_type = 'BUILDING', name = '通政殿', icon = 'message',
       coordinate_x = 568, coordinate_y = 523, coordinate_z = 0, sort_no = 25,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'tongzheng-hall';

-- 起居殿（qiju-hall）
INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, coordinate_z, sort_no, status, deleted)
SELECT 5335, 1, 5301, 'qiju-hall', 'royal-city', 'BUILDING', '起居殿', 'audit',
       568, 570, 0, 26, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5335);
UPDATE sc_scene_node SET parent_key = 'royal-city', node_type = 'BUILDING', name = '起居殿', icon = 'audit',
       coordinate_x = 568, coordinate_y = 570, coordinate_z = 0, sort_no = 26,
       status = 'ENABLED', deleted = 0
 WHERE tenant_id = 1 AND scene_id = 5301 AND node_key = 'qiju-hall';

-- ---------------------------------------------------------------------
-- 4. 二维入口绑定（三维宫殿 → 既有 2D 子应用路由，业务系统一行不改）
-- ---------------------------------------------------------------------
-- 数枢殿 → /portal
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5402, 1, 5313, 'SYSTEM', '/portal', NULL, 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5402);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/portal', b.permission_code = NULL, b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'shushu-hall';

-- 户部 → /erp
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5403, 1, 5316, 'SYSTEM', '/erp', 'sys:erp:item:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5403);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/erp', b.permission_code = 'sys:erp:item:list', b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'hubu-hall';

-- 工部 → /mes
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5404, 1, 5317, 'SYSTEM', '/mes', 'sys:mes:workorder:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5404);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/mes', b.permission_code = 'sys:mes:workorder:list', b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'gongbu-hall';

-- 吏部 → /oa
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5405, 1, 5318, 'SYSTEM', '/oa', 'sys:oa:leave:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5405);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/oa', b.permission_code = 'sys:oa:leave:list', b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'libu-hall';

-- 礼部 → /crm
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5406, 1, 5319, 'SYSTEM', '/crm', 'sys:crm:browse', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5406);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/crm', b.permission_code = 'sys:crm:browse', b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'libu-ritual-hall';

-- 兵部 → /iot
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5407, 1, 5320, 'SYSTEM', '/iot', 'sys:iot:device', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5407);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/iot', b.permission_code = 'sys:iot:device', b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'bingbu-hall';

-- 翰林院 → /doc
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5408, 1, 5321, 'SYSTEM', '/doc', 'sys:doc:space:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5408);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/doc', b.permission_code = 'sys:doc:space:list', b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'hanlin-academy';

-- 杏坛 → /lms
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5409, 1, 5322, 'SYSTEM', '/lms', 'sys:lms:browse', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5409);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/lms', b.permission_code = 'sys:lms:browse', b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'xingtan-hall';

-- 市舶司 → /mall
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5410, 1, 5323, 'SYSTEM', '/mall', 'sys:mall:browse', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5410);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/mall', b.permission_code = 'sys:mall:browse', b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'shibosi-hall';

-- 将作监 → /pdm
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5411, 1, 5324, 'SYSTEM', '/pdm', 'sys:pdm:bom:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5411);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/pdm', b.permission_code = 'sys:pdm:bom:list', b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'jiangzuo-bureau';

-- 营造司 → /pm
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5412, 1, 5325, 'SYSTEM', '/pm', 'sys:pm:item:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5412);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/pm', b.permission_code = 'sys:pm:item:list', b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'yingzao-bureau';

-- 盟府 → /contract
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5413, 1, 5326, 'SYSTEM', '/contract', 'sys:contract:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5413);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/contract', b.permission_code = 'sys:contract:list', b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'mengfu-hall';

-- 宣抚院 → /party
INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5414, 1, 5327, 'SYSTEM', '/party', 'sys:party:browse', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5414);
UPDATE sc_node_binding b
  JOIN sc_scene_node n ON n.id = b.node_id AND n.tenant_id = b.tenant_id
   SET b.entry_type = 'SYSTEM', b.target_path = '/party', b.permission_code = 'sys:party:browse', b.enabled = 1, b.deleted = 0
 WHERE b.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'xuanfu-court';

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
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'region-plain', w.scale_x = 1.000, w.scale_y = 1.000, w.scale_z = 1.000,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 0.00, w.collider_half_z = 0.00,
       w.roof_color = NULL, w.body_color = NULL, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'outer-city';

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
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'region-inner', w.scale_x = 1.000, w.scale_y = 1.000, w.scale_z = 1.000,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 0.00, w.collider_half_z = 0.00,
       w.roof_color = NULL, w.body_color = NULL, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'inner-city';

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
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'region-royal', w.scale_x = 1.000, w.scale_y = 1.000, w.scale_z = 1.000,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 0.00, w.collider_half_z = 0.00,
       w.roof_color = NULL, w.body_color = NULL, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'royal-city';

-- 数枢殿
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5513, 1, 5301, 5313, 'hall-royal', NULL,
       1.000, 1.000, 1.000, 0.00, 0.00,
       11.50, 10.00, 13214010, 9383716, 1.00,
       'SUBAPP', 0.000, 4.000, 16765286,
       '数枢殿', '核心数据 · 皇城', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5513);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'hall-royal', w.scale_x = 1.000, w.scale_y = 1.000, w.scale_z = 1.000,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 11.50, w.collider_half_z = 10.00,
       w.roof_color = 13214010, w.body_color = 9383716, w.portal_kind = 'SUBAPP',
       w.portal_x = 0.000, w.portal_z = 4.000, w.portal_color = 16765286,
       w.portal_label = '数枢殿', w.portal_sublabel = '核心数据 · 皇城', w.portal_main = 1, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'shushu-hall';

-- 典籍阁
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5514, 1, 5301, 5314, 'pavilion-side', NULL,
       0.500, 0.500, 0.500, 0.00, 0.00,
       3.80, 3.50, 3959642, 9062960, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5514);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'pavilion-side', w.scale_x = 0.500, w.scale_y = 0.500, w.scale_z = 0.500,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 3.80, w.collider_half_z = 3.50,
       w.roof_color = 3959642, w.body_color = 9062960, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'dianji-pavilion';

-- 玉衡阁
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5515, 1, 5301, 5315, 'pavilion-side', NULL,
       0.500, 0.500, 0.500, 0.00, 0.00,
       3.80, 3.50, 3959642, 9062960, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5515);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'pavilion-side', w.scale_x = 0.500, w.scale_y = 0.500, w.scale_z = 0.500,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 3.80, w.collider_half_z = 3.50,
       w.roof_color = 3959642, w.body_color = 9062960, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'yuheng-pavilion';

-- 户部
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5516, 1, 5301, 5316, 'palace-hall', NULL,
       0.950, 0.950, 0.950, -165.00, 0.00,
       10.00, 9.00, 3107679, 9386540, 1.00,
       'SUBAPP', 16.047, 59.887, 7332863,
       '户部', 'ERP · 企业资源', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5516);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'palace-hall', w.scale_x = 0.950, w.scale_y = 0.950, w.scale_z = 0.950,
       w.rotation_y_deg = -165.00, w.elevation = 0.00, w.collider_half_x = 10.00, w.collider_half_z = 9.00,
       w.roof_color = 3107679, w.body_color = 9386540, w.portal_kind = 'SUBAPP',
       w.portal_x = 16.047, w.portal_z = 59.887, w.portal_color = 7332863,
       w.portal_label = '户部', w.portal_sublabel = 'ERP · 企业资源', w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'hubu-hall';

-- 工部
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5517, 1, 5301, 5317, 'palace-hall', NULL,
       0.950, 0.950, 0.950, -135.00, 0.00,
       10.00, 9.00, 4153208, 8141616, 1.00,
       'SUBAPP', 43.841, 43.841, 7332863,
       '工部', 'MES · 智能制造', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5517);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'palace-hall', w.scale_x = 0.950, w.scale_y = 0.950, w.scale_z = 0.950,
       w.rotation_y_deg = -135.00, w.elevation = 0.00, w.collider_half_x = 10.00, w.collider_half_z = 9.00,
       w.roof_color = 4153208, w.body_color = 8141616, w.portal_kind = 'SUBAPP',
       w.portal_x = 43.841, w.portal_z = 43.841, w.portal_color = 7332863,
       w.portal_label = '工部', w.portal_sublabel = 'MES · 智能制造', w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'gongbu-hall';

-- 吏部
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5518, 1, 5301, 5318, 'palace-hall', NULL,
       0.950, 0.950, 0.950, -105.00, 0.00,
       10.00, 9.00, 4877194, 9386540, 1.00,
       'SUBAPP', 59.887, 16.047, 7332863,
       '吏部', 'OA · 协同办公', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5518);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'palace-hall', w.scale_x = 0.950, w.scale_y = 0.950, w.scale_z = 0.950,
       w.rotation_y_deg = -105.00, w.elevation = 0.00, w.collider_half_x = 10.00, w.collider_half_z = 9.00,
       w.roof_color = 4877194, w.body_color = 9386540, w.portal_kind = 'SUBAPP',
       w.portal_x = 59.887, w.portal_z = 16.047, w.portal_color = 7332863,
       w.portal_label = '吏部', w.portal_sublabel = 'OA · 协同办公', w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'libu-hall';

-- 礼部
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5519, 1, 5301, 5319, 'palace-hall', NULL,
       0.950, 0.950, 0.950, -75.00, 0.00,
       10.00, 9.00, 7031416, 9059124, 1.00,
       'SUBAPP', 59.887, -16.047, 7332863,
       '礼部', 'CRM · 客户经营', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5519);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'palace-hall', w.scale_x = 0.950, w.scale_y = 0.950, w.scale_z = 0.950,
       w.rotation_y_deg = -75.00, w.elevation = 0.00, w.collider_half_x = 10.00, w.collider_half_z = 9.00,
       w.roof_color = 7031416, w.body_color = 9059124, w.portal_kind = 'SUBAPP',
       w.portal_x = 59.887, w.portal_z = -16.047, w.portal_color = 7332863,
       w.portal_label = '礼部', w.portal_sublabel = 'CRM · 客户经营', w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'libu-ritual-hall';

-- 兵部
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5520, 1, 5301, 5320, 'palace-hall', NULL,
       0.950, 0.950, 0.950, -45.00, 0.00,
       10.00, 9.00, 4871520, 7289392, 1.00,
       'SUBAPP', 43.841, -43.841, 7332863,
       '兵部', 'IoT · 物联感知', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5520);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'palace-hall', w.scale_x = 0.950, w.scale_y = 0.950, w.scale_z = 0.950,
       w.rotation_y_deg = -45.00, w.elevation = 0.00, w.collider_half_x = 10.00, w.collider_half_z = 9.00,
       w.roof_color = 4871520, w.body_color = 7289392, w.portal_kind = 'SUBAPP',
       w.portal_x = 43.841, w.portal_z = -43.841, w.portal_color = 7332863,
       w.portal_label = '兵部', w.portal_sublabel = 'IoT · 物联感知', w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'bingbu-hall';

-- 翰林院
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5521, 1, 5301, 5321, 'palace-hall', NULL,
       0.950, 0.950, 0.950, -15.00, 0.00,
       10.00, 9.00, 2906706, 9062960, 1.00,
       'SUBAPP', 16.047, -59.887, 7332863,
       '翰林院', 'DOC · 文档知识', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5521);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'palace-hall', w.scale_x = 0.950, w.scale_y = 0.950, w.scale_z = 0.950,
       w.rotation_y_deg = -15.00, w.elevation = 0.00, w.collider_half_x = 10.00, w.collider_half_z = 9.00,
       w.roof_color = 2906706, w.body_color = 9062960, w.portal_kind = 'SUBAPP',
       w.portal_x = 16.047, w.portal_z = -59.887, w.portal_color = 7332863,
       w.portal_label = '翰林院', w.portal_sublabel = 'DOC · 文档知识', w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'hanlin-academy';

-- 杏坛
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5522, 1, 5301, 5322, 'palace-hall', NULL,
       0.950, 0.950, 0.950, 15.00, 0.00,
       10.00, 9.00, 9067054, 9386540, 1.00,
       'SUBAPP', -16.047, -59.887, 7332863,
       '杏坛', 'LMS · 学习培训', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5522);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'palace-hall', w.scale_x = 0.950, w.scale_y = 0.950, w.scale_z = 0.950,
       w.rotation_y_deg = 15.00, w.elevation = 0.00, w.collider_half_x = 10.00, w.collider_half_z = 9.00,
       w.roof_color = 9067054, w.body_color = 9386540, w.portal_kind = 'SUBAPP',
       w.portal_x = -16.047, w.portal_z = -59.887, w.portal_color = 7332863,
       w.portal_label = '杏坛', w.portal_sublabel = 'LMS · 学习培训', w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'xingtan-hall';

-- 市舶司
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5523, 1, 5301, 5323, 'palace-hall', NULL,
       0.950, 0.950, 0.950, 45.00, 0.00,
       10.00, 9.00, 13214010, 9059124, 1.00,
       'SUBAPP', -43.841, -43.841, 7332863,
       '市舶司', 'MALL · 商城交易', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5523);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'palace-hall', w.scale_x = 0.950, w.scale_y = 0.950, w.scale_z = 0.950,
       w.rotation_y_deg = 45.00, w.elevation = 0.00, w.collider_half_x = 10.00, w.collider_half_z = 9.00,
       w.roof_color = 13214010, w.body_color = 9059124, w.portal_kind = 'SUBAPP',
       w.portal_x = -43.841, w.portal_z = -43.841, w.portal_color = 7332863,
       w.portal_label = '市舶司', w.portal_sublabel = 'MALL · 商城交易', w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'shibosi-hall';

-- 将作监
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5524, 1, 5301, 5324, 'palace-hall', NULL,
       0.950, 0.950, 0.950, 75.00, 0.00,
       10.00, 9.00, 3107679, 9386540, 1.00,
       'SUBAPP', -59.887, -16.047, 7332863,
       '将作监', 'PDM · 产品数据', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5524);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'palace-hall', w.scale_x = 0.950, w.scale_y = 0.950, w.scale_z = 0.950,
       w.rotation_y_deg = 75.00, w.elevation = 0.00, w.collider_half_x = 10.00, w.collider_half_z = 9.00,
       w.roof_color = 3107679, w.body_color = 9386540, w.portal_kind = 'SUBAPP',
       w.portal_x = -59.887, w.portal_z = -16.047, w.portal_color = 7332863,
       w.portal_label = '将作监', w.portal_sublabel = 'PDM · 产品数据', w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'jiangzuo-bureau';

-- 营造司
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5525, 1, 5301, 5325, 'palace-hall', NULL,
       0.950, 0.950, 0.950, 105.00, 0.00,
       10.00, 9.00, 3107679, 9386540, 1.00,
       'SUBAPP', -59.887, 16.047, 7332863,
       '营造司', 'PM · 工程项目', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5525);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'palace-hall', w.scale_x = 0.950, w.scale_y = 0.950, w.scale_z = 0.950,
       w.rotation_y_deg = 105.00, w.elevation = 0.00, w.collider_half_x = 10.00, w.collider_half_z = 9.00,
       w.roof_color = 3107679, w.body_color = 9386540, w.portal_kind = 'SUBAPP',
       w.portal_x = -59.887, w.portal_z = 16.047, w.portal_color = 7332863,
       w.portal_label = '营造司', w.portal_sublabel = 'PM · 工程项目', w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'yingzao-bureau';

-- 盟府
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5526, 1, 5301, 5326, 'palace-hall', NULL,
       0.950, 0.950, 0.950, 135.00, 0.00,
       10.00, 9.00, 3107679, 9386540, 1.00,
       'SUBAPP', -43.841, 43.841, 7332863,
       '盟府', 'CONTRACT · 合同印章', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5526);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'palace-hall', w.scale_x = 0.950, w.scale_y = 0.950, w.scale_z = 0.950,
       w.rotation_y_deg = 135.00, w.elevation = 0.00, w.collider_half_x = 10.00, w.collider_half_z = 9.00,
       w.roof_color = 3107679, w.body_color = 9386540, w.portal_kind = 'SUBAPP',
       w.portal_x = -43.841, w.portal_z = 43.841, w.portal_color = 7332863,
       w.portal_label = '盟府', w.portal_sublabel = 'CONTRACT · 合同印章', w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'mengfu-hall';

-- 宣抚院
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5527, 1, 5301, 5327, 'palace-hall', NULL,
       0.950, 0.950, 0.950, 165.00, 0.00,
       10.00, 9.00, 3107679, 9386540, 1.00,
       'SUBAPP', -16.047, 59.887, 7332863,
       '宣抚院', 'PARTY · 党务党建', 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5527);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'palace-hall', w.scale_x = 0.950, w.scale_y = 0.950, w.scale_z = 0.950,
       w.rotation_y_deg = 165.00, w.elevation = 0.00, w.collider_half_x = 10.00, w.collider_half_z = 9.00,
       w.roof_color = 3107679, w.body_color = 9386540, w.portal_kind = 'SUBAPP',
       w.portal_x = -16.047, w.portal_z = 59.887, w.portal_color = 7332863,
       w.portal_label = '宣抚院', w.portal_sublabel = 'PARTY · 党务党建', w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'xuanfu-court';

-- 枢密殿
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5528, 1, 5301, 5328, 'pavilion-side', NULL,
       0.720, 0.720, 0.720, 0.00, 0.00,
       6.10, 5.40, 3959642, 9062960, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5528);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'pavilion-side', w.scale_x = 0.720, w.scale_y = 0.720, w.scale_z = 0.720,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 6.10, w.collider_half_z = 5.40,
       w.roof_color = 3959642, w.body_color = 9062960, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'shumi-hall';

-- 度支殿
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5529, 1, 5301, 5329, 'pavilion-side', NULL,
       0.720, 0.720, 0.720, 0.00, 0.00,
       6.10, 5.40, 3959642, 9062960, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5529);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'pavilion-side', w.scale_x = 0.720, w.scale_y = 0.720, w.scale_z = 0.720,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 6.10, w.collider_half_z = 5.40,
       w.roof_color = 3959642, w.body_color = 9062960, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'duzhi-hall';

-- 藏书殿
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5530, 1, 5301, 5330, 'pavilion-side', NULL,
       0.720, 0.720, 0.720, 0.00, 0.00,
       6.10, 5.40, 3959642, 9062960, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5530);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'pavilion-side', w.scale_x = 0.720, w.scale_y = 0.720, w.scale_z = 0.720,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 6.10, w.collider_half_z = 5.40,
       w.roof_color = 3959642, w.body_color = 9062960, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'cangshu-hall';

-- 时辰殿
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5531, 1, 5301, 5331, 'pavilion-side', NULL,
       0.720, 0.720, 0.720, 0.00, 0.00,
       6.10, 5.40, 3959642, 9062960, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5531);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'pavilion-side', w.scale_x = 0.720, w.scale_y = 0.720, w.scale_z = 0.720,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 6.10, w.collider_half_z = 5.40,
       w.roof_color = 3959642, w.body_color = 9062960, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'shichen-hall';

-- 政事殿
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5532, 1, 5301, 5332, 'pavilion-side', NULL,
       0.720, 0.720, 0.720, 0.00, 0.00,
       6.10, 5.40, 3959642, 9062960, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5532);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'pavilion-side', w.scale_x = 0.720, w.scale_y = 0.720, w.scale_z = 0.720,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 6.10, w.collider_half_z = 5.40,
       w.roof_color = 3959642, w.body_color = 9062960, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'zhengshi-hall';

-- 考功殿
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5533, 1, 5301, 5333, 'pavilion-side', NULL,
       0.720, 0.720, 0.720, 0.00, 0.00,
       6.10, 5.40, 3959642, 9062960, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5533);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'pavilion-side', w.scale_x = 0.720, w.scale_y = 0.720, w.scale_z = 0.720,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 6.10, w.collider_half_z = 5.40,
       w.roof_color = 3959642, w.body_color = 9062960, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'kaogong-hall';

-- 通政殿
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5534, 1, 5301, 5334, 'pavilion-side', NULL,
       0.720, 0.720, 0.720, 0.00, 0.00,
       6.10, 5.40, 3959642, 9062960, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5534);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'pavilion-side', w.scale_x = 0.720, w.scale_y = 0.720, w.scale_z = 0.720,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 6.10, w.collider_half_z = 5.40,
       w.roof_color = 3959642, w.body_color = 9062960, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'tongzheng-hall';

-- 起居殿
INSERT INTO sc_node_world (id, tenant_id, scene_id, node_id, model_key, model_asset_id,
                            scale_x, scale_y, scale_z, rotation_y_deg, elevation,
                            collider_half_x, collider_half_z, roof_color, body_color, lod_bias,
                            portal_kind, portal_x, portal_z, portal_color,
                            portal_label, portal_sublabel, portal_main, deleted)
SELECT 5535, 1, 5301, 5335, 'pavilion-side', NULL,
       0.720, 0.720, 0.720, 0.00, 0.00,
       6.10, 5.40, 3959642, 9062960, 1.00,
       'NONE', NULL, NULL, NULL,
       NULL, NULL, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_world WHERE id = 5535);
UPDATE sc_node_world w
  JOIN sc_scene_node n ON n.id = w.node_id AND n.tenant_id = w.tenant_id
   SET w.model_key = 'pavilion-side', w.scale_x = 0.720, w.scale_y = 0.720, w.scale_z = 0.720,
       w.rotation_y_deg = 0.00, w.elevation = 0.00, w.collider_half_x = 6.10, w.collider_half_z = 5.40,
       w.roof_color = 3959642, w.body_color = 9062960, w.portal_kind = 'NONE',
       w.portal_x = NULL, w.portal_z = NULL, w.portal_color = NULL,
       w.portal_label = NULL, w.portal_sublabel = NULL, w.portal_main = 0, w.deleted = 0
 WHERE w.tenant_id = 1 AND n.scene_id = 5301 AND n.node_key = 'qiju-hall';

-- =======================================================================
-- 统计：场景 1 / 节点 26 / 入口绑定 13 / 三维表现 26
-- =======================================================================
