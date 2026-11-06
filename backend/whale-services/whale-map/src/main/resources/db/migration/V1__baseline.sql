-- =====================================================================
-- BlueWhale 地图服务（whale-map） Flyway 版本化基线 V1
-- =====================================================================
-- 适用库   : bluewhale_map（MySQL 8.0+，utf8mb4）
-- 公共字段 : id BIGINT（雪花算法）、create_time、update_time
-- 租户隔离 : tenant_id 由 starter 的 TenantLineInnerInterceptor 自动追加/填充
-- 边界说明 : 本服务只做「地址与坐标的互转 + 点位落库」，不存底图、不存瓦片。
--            底图由前端 SDK 渲染，供应商 Key 不出服务端。
--
-- 为什么这三张表都没有 deleted 列：
--   它们都不是业务单据，而是「状态」——一个业务对象当前在哪、某个地址上次解析成什么、
--   今天已经用了多少配额。状态表的正确语义是覆盖写与物理删除。若挂上逻辑删除，
--   唯一键 (tenant_id, biz_type, biz_id) 会在「删掉再打点」时撞键，
--   只能靠把 deleted 编进唯一键来绕，而 deleted 只有 0/1 两个值，删两次照样撞。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 业务对象打点（一个对象一个位置）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS map_location (
    id              BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    biz_type        VARCHAR(32)   NOT NULL                COMMENT '业务类型：CRM_CONTACT/ERP_SUPPLIER/OA_TRIP 等',
    biz_id          VARCHAR(64)   NOT NULL                COMMENT '业务对象ID（统一按字符串存，兼容各类主键）',
    lng             DECIMAL(10,7) NOT NULL                COMMENT '经度（存储坐标系，默认 WGS84）',
    lat             DECIMAL(9,7)  NOT NULL                COMMENT '纬度（存储坐标系，默认 WGS84）',
    coord_type      VARCHAR(8)    NOT NULL DEFAULT 'WGS84' COMMENT '坐标系：WGS84/GCJ02/BD09',
    geohash         VARCHAR(12)   NOT NULL                COMMENT 'geohash（按存储坐标编码，用于附近检索）',
    address         VARCHAR(255)  DEFAULT NULL            COMMENT '标准地址（解析结果或人工填写）',
    province        VARCHAR(64)   DEFAULT NULL            COMMENT '省',
    city            VARCHAR(64)   DEFAULT NULL            COMMENT '市',
    district        VARCHAR(64)   DEFAULT NULL            COMMENT '区县',
    adcode          VARCHAR(12)   DEFAULT NULL            COMMENT '行政区划码（六位）',
    provider        VARCHAR(16)   DEFAULT NULL            COMMENT '坐标来源供应商（MANUAL 表示人工填写）',
    source          VARCHAR(16)   NOT NULL DEFAULT 'GEOCODE' COMMENT '来源：GEOCODE 解析 / MANUAL 手填 / IMPORT 导入',
    remark          VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    -- 一个业务对象只有一个位置：重复打点是覆盖，不是追加，唯一键保证不会悄悄留下两条
    UNIQUE KEY uk_map_location_biz (tenant_id, biz_type, biz_id),
    -- 附近查询先按类型 + geohash 前缀圈定九宫格，再按半径精筛
    KEY idx_map_location_geo (tenant_id, biz_type, geohash),
    KEY idx_map_location_adcode (tenant_id, adcode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='业务对象打点表';

-- ---------------------------------------------------------------------
-- 2. 地址解析缓存（同一地址反复解析是常态）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS map_geocode_cache (
    id              BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    -- 缓存按供应商隔离：同一地址在腾讯与高德可能落到不同楼栋，
    -- 混用一条缓存会让「切供应商后位置突然变了」且无法解释
    provider        VARCHAR(16)   NOT NULL                COMMENT '供应商名',
    query_type      VARCHAR(16)   NOT NULL                COMMENT '查询类型：GEOCODE/REGEO/POI',
    cache_key       CHAR(64)      NOT NULL                COMMENT '查询串的 SHA-256（等长，避免超长地址撑爆索引）',
    query_text      VARCHAR(255)  DEFAULT NULL            COMMENT '原始查询串（排障用，超长截断）',
    result_json     TEXT          NULL                    COMMENT '标准化后的结果 JSON',
    lng             DECIMAL(10,7) DEFAULT NULL            COMMENT '经度（存储坐标系）',
    lat             DECIMAL(9,7)  DEFAULT NULL            COMMENT '纬度（存储坐标系）',
    coord_type      VARCHAR(8)    DEFAULT NULL            COMMENT '坐标系',
    hit_count       BIGINT        NOT NULL DEFAULT 0      COMMENT '命中次数（用于评估缓存价值与淘汰顺序）',
    expire_time     DATETIME      NOT NULL                COMMENT '过期时间（过期不返回，由清理任务物理删除）',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_map_cache_key (tenant_id, provider, query_type, cache_key),
    KEY idx_map_cache_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='地图解析结果缓存表';

-- ---------------------------------------------------------------------
-- 3. 供应商日配额用量（跨实例共享，重启不清零）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS map_quota_usage (
    id              BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    provider        VARCHAR(16)   NOT NULL                COMMENT '供应商名',
    usage_date      DATE          NOT NULL                COMMENT '统计日期（自然日）',
    used_count      INT           NOT NULL DEFAULT 0      COMMENT '已用次数',
    limit_count     INT           NOT NULL DEFAULT 0      COMMENT '上限（0 表示不限）',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_map_quota_day (tenant_id, provider, usage_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='地图供应商日配额用量表';
