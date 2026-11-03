-- =====================================================================
-- BlueWhale 视频平台服务（whale-video）Flyway 增量脚本：电影片单
-- =====================================================================
-- 版本       : V3（在 V1 基线 + V2 系列之上追加 vd_collection / vd_collection_item）
-- 数据库版本 : MySQL 8.0+
-- 业务口径   : 电影片单由运营维护，条目引用 vd_content 中 MOVIE 类型内容；
--              item_count 在加片 / 移除后刷新，仅统计未删除条目；
--              同一片单同一电影唯一（DB 唯一键 + 应用层幂等判断双保险），
--              单片单最多 200 部由应用层限制；电影授权到期自动下线不影响条目。
-- 脚本约束   : 禁止 DROP / TRUNCATE；CREATE TABLE IF NOT EXISTS 可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS vd_collection (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    title               VARCHAR(200)  NOT NULL                COMMENT '片单标题',
    description         VARCHAR(2000) DEFAULT NULL            COMMENT '片单简介',
    cover_file_id       BIGINT        DEFAULT NULL            COMMENT '封面文件ID（whale-file，公开 VO 不暴露）',
    sort_no             INT           NOT NULL DEFAULT 0      COMMENT '运营排序（升序）',
    publish_status      VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态：DRAFT / PUBLISHED / OFFLINE',
    item_count          INT           NOT NULL DEFAULT 0      COMMENT '片单条目数（加片 / 移除后刷新）',
    published_at        DATETIME      DEFAULT NULL            COMMENT '最近上架时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_vd_collection_list (tenant_id, publish_status, sort_no, id)
) COMMENT='电影片单表';

CREATE TABLE IF NOT EXISTS vd_collection_item (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    collection_id       BIGINT        NOT NULL                COMMENT '片单ID（vd_collection.id）',
    content_id          BIGINT        NOT NULL                COMMENT '电影内容ID（vd_content.id）',
    sort_no             INT           NOT NULL DEFAULT 0      COMMENT '片单内排序（升序）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vd_collection_item (collection_id, content_id),
    KEY idx_vd_collection_item_list (tenant_id, collection_id, sort_no, id)
) COMMENT='电影片单条目表';
