-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V10
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 上级计划 : docs/plans/2026-11-04-wave5.82-oa-panorama.md
-- 里程碑   : Wave 5.82 M3-P2 协同（日程 / 网盘 / 通讯录 / 工作台）
-- 说明     : 本脚本仅新建日程与日程共享 2 张表；日程按 D10 先做单次（不支持
--            RRULE 重复），会议通过查询会议参会人同步展示，不落日程副本。
-- 幂等     : CREATE TABLE IF NOT EXISTS；无种子数据。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 日程（可见性 PRIVATE/SHARED/PUBLIC；共享通过 oa_calendar_share 授权）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_calendar (
    id             BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    owner_id       BIGINT       NOT NULL            COMMENT '所有者用户ID（逻辑关联 sys_user.id）',
    owner_name     VARCHAR(64)  DEFAULT NULL        COMMENT '所有者姓名快照',
    title          VARCHAR(128) NOT NULL            COMMENT '日程标题',
    description    TEXT         DEFAULT NULL        COMMENT '日程描述',
    location       VARCHAR(128) DEFAULT NULL        COMMENT '地点',
    start_time     DATETIME     NOT NULL            COMMENT '开始时间',
    end_time       DATETIME     NOT NULL            COMMENT '结束时间',
    all_day        TINYINT      NOT NULL DEFAULT 0  COMMENT '全天：0 否 / 1 是',
    visibility     VARCHAR(16)  NOT NULL DEFAULT 'PRIVATE'
                                                    COMMENT '可见性：PRIVATE 私密 / SHARED 共享 / PUBLIC 公开',
    remind_minutes INT          DEFAULT NULL        COMMENT '提前提醒分钟数（空表示不提醒）',
    source         VARCHAR(16)  NOT NULL DEFAULT 'PERSONAL'
                                                    COMMENT '来源：PERSONAL 个人日程',
    source_id      BIGINT       DEFAULT NULL        COMMENT '来源业务ID',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_calendar_owner_time (tenant_id, owner_id, start_time),
    KEY idx_oa_calendar_visibility_time (tenant_id, visibility, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 日程表';

-- ---------------------------------------------------------------------
-- 2. 日程共享（同一日程对同一用户唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_calendar_share (
    id          BIGINT      NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT      NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    calendar_id BIGINT      NOT NULL            COMMENT '日程ID（逻辑关联 oa_calendar.id）',
    user_id     BIGINT      NOT NULL            COMMENT '被共享用户ID（逻辑关联 sys_user.id）',
    user_name   VARCHAR(64) DEFAULT NULL        COMMENT '被共享用户姓名快照',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT     NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_calendar_share_user (tenant_id, calendar_id, user_id),
    KEY idx_oa_calendar_share_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 日程共享表';
