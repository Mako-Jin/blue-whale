-- =====================================================================
-- BlueWhale 地图服务（whale-map） Flyway 版本化迁移 V2
-- =====================================================================
-- 适用库   : bluewhale_map（MySQL 8.0+，utf8mb4）
-- 本轮内容 : 围栏（打卡范围、销售区域、配送范围）
--
-- 坐标口径（本表最关键的一条）：
--   围栏顶点与点位**同源**，一律落 WGS84，入口由服务端从 GCJ-02/BD-09 转过来。
--   不在这里存「底图坐标系」，因为底图会换（腾讯 GL / 高德 JS），而点位不会。
--   判定不走 MySQL 的 ST_Contains —— 它按度在笛卡尔平面上算，经度 1 度在北京约
--   85 公里、在赤道约 111 公里，东西向会被拉伸；且 SRID 写错不报错，只静默偏几百米。
--   判定在应用层做局部等距投影 + 射线法（见 GeoFence），顺带给出「离边界还有多远」。
--
-- 为什么本表**有** deleted 列（与 V1 三张表相反）：
--   V1 的三张表是「状态」（对象在哪、地址解析成什么、今天用了几次），覆盖写与物理删除
--   才是正确语义。围栏是「配置单据」：它会被打卡记录、配送单引用，删掉之后那些记录
--   仍需指回一个能解释的名字；误删也要能恢复。所以这里用逻辑删除，并且——
--   因为允许同名围栏并存，本表**不建唯一键**，也就不存在「删掉再建撞键」的问题。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 围栏（圆 / 多边形）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS map_fence (
    id              BIGINT        NOT NULL                 COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1       COMMENT '租户ID（行级隔离）',
    name            VARCHAR(64)   NOT NULL                 COMMENT '围栏名称',
    fence_type      VARCHAR(16)   NOT NULL                 COMMENT '围栏类型：CIRCLE 圆 / POLYGON 多边形',
    biz_type        VARCHAR(32)   DEFAULT NULL             COMMENT '绑定业务类型：空表示通用围栏（如 OA_ATTEND 考勤组）',
    biz_id          VARCHAR(64)   DEFAULT NULL             COMMENT '绑定业务对象ID（如某门店、某考勤组ID）',
    -- 圆
    center_lng      DECIMAL(10,7) DEFAULT NULL             COMMENT '圆心经度（存储坐标系；POLYGON 为空）',
    center_lat      DECIMAL(9,7)  DEFAULT NULL             COMMENT '圆心纬度（存储坐标系；POLYGON 为空）',
    radius_meters   DECIMAL(12,2) DEFAULT NULL             COMMENT '半径（米；POLYGON 为空）',
    -- 多边形：[[lng,lat],...]，首尾不闭合（闭合点由写入时去掉，避免一条零长度边）
    points          TEXT          DEFAULT NULL             COMMENT '多边形顶点 JSON（存储坐标系）',
    coord_type      VARCHAR(8)    NOT NULL DEFAULT 'WGS84' COMMENT '坐标系（与点位同源，一律 WGS84）',
    -- 外接矩形：判定前先按 bbox 粗筛，把「点根本不在附近」的围栏挡在几何运算之外
    min_lng         DECIMAL(10,7) DEFAULT NULL             COMMENT '外接矩形最小经度',
    max_lng         DECIMAL(10,7) DEFAULT NULL             COMMENT '外接矩形最大经度',
    min_lat         DECIMAL(9,7)  DEFAULT NULL             COMMENT '外接矩形最小纬度',
    max_lat         DECIMAL(9,7)  DEFAULT NULL             COMMENT '外接矩形最大纬度',
    enabled         TINYINT       NOT NULL DEFAULT 1       COMMENT '是否启用：1 启用 / 0 停用（停用不参与判定）',
    remark          VARCHAR(255)  DEFAULT NULL             COMMENT '备注',
    deleted         TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 正常 / 1 已删除',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    -- 反查「这个点落在哪些围栏里」时按业务范围圈定（打卡只看考勤组的围栏，不看销售区域）
    KEY idx_map_fence_scope (tenant_id, deleted, enabled, biz_type, biz_id),
    -- bbox 粗筛：先按纬度带收窄，再交给应用层算精确结果
    KEY idx_map_fence_bbox (tenant_id, min_lat, max_lat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='围栏表（打卡范围/销售区域/配送范围）';
