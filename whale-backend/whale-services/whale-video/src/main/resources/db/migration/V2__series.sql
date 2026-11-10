-- =====================================================================
-- BlueWhale 视频平台服务（whale-video）Flyway 增量脚本：系列
-- =====================================================================
-- 版本       : V2（在 V1 基线之上追加 vd_series）
-- 数据库版本 : MySQL 8.0+
-- 业务口径   : 系列为运营维护的视频合集（当前仅 VIDEO 入系列），
--              集数由 vd_content.series_id / episode_no 关联，
--              episode_count 在内容创建 / 编辑 / 删除 / 发布 / 下架时刷新；
--              同一系列内集数唯一由应用层校验（逻辑删除下不建唯一键）。
-- 脚本约束   : 禁止 DROP / TRUNCATE；CREATE TABLE IF NOT EXISTS 可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS vd_series (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    title               VARCHAR(200)  NOT NULL                COMMENT '系列标题',
    description         VARCHAR(2000) DEFAULT NULL            COMMENT '系列简介',
    cover_file_id       BIGINT        DEFAULT NULL            COMMENT '封面文件ID（whale-file，公开 VO 不暴露）',
    content_type        VARCHAR(16)   NOT NULL DEFAULT 'VIDEO' COMMENT '内容类型：当前仅 VIDEO 可入系列',
    category_id         BIGINT        NOT NULL DEFAULT 0      COMMENT '分类ID（vd_category.id，0 表示未分类）',
    creator_id          BIGINT        NOT NULL DEFAULT 0      COMMENT '创作者ID（vd_creator.id，0 表示未登记）',
    uploader_id         BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人用户ID（运营维护）',
    publish_status      VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态：DRAFT / PUBLISHED / OFFLINE',
    episode_count       INT           NOT NULL DEFAULT 0      COMMENT '已发布集数（内容变更时刷新）',
    sort_no             INT           NOT NULL DEFAULT 0      COMMENT '运营排序（升序）',
    published_at        DATETIME      DEFAULT NULL            COMMENT '最近上架时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_vd_series_list (tenant_id, publish_status, sort_no, id),
    KEY idx_vd_series_category (tenant_id, category_id, publish_status),
    KEY idx_vd_series_creator (tenant_id, creator_id)
) COMMENT='视频系列表';
