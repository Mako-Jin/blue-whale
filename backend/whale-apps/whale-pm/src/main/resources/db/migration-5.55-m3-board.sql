-- =====================================================================
-- Wave 5.55 M3：项目管理（whale-pm）迭代与看板增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_pm
-- 执行方式 : mysql -u<user> -p bluewhale_pm < migration-5.55-m3-board.sql
-- 幂等说明 : CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行。
-- 变更内容 : pm_sprint / pm_board / pm_board_column 三表 + 演示租户种子。
-- =====================================================================

CREATE TABLE IF NOT EXISTS pm_sprint (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id   BIGINT       NOT NULL            COMMENT '项目ID',
    name         VARCHAR(64)  NOT NULL            COMMENT '迭代名称',
    goal         VARCHAR(512) DEFAULT NULL        COMMENT '迭代目标',
    start_date   DATE         DEFAULT NULL        COMMENT '开始日期',
    end_date     DATE         DEFAULT NULL        COMMENT '结束日期',
    status       VARCHAR(16)  NOT NULL DEFAULT 'FUTURE' COMMENT '状态：FUTURE 未启动 / ACTIVE 进行中 / CLOSED 已关闭',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_sprint_name (tenant_id, project_id, name),
    KEY idx_pm_sprint_project (tenant_id, project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目迭代表';

CREATE TABLE IF NOT EXISTS pm_board (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id   BIGINT       NOT NULL            COMMENT '项目ID',
    name         VARCHAR(64)  NOT NULL            COMMENT '看板名称',
    board_type   VARCHAR(16)  NOT NULL DEFAULT 'SCRUM' COMMENT '看板类型：SCRUM 敏捷 / KANBAN 看板',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_board_name (tenant_id, project_id, name),
    KEY idx_pm_board_project (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目看板表';

CREATE TABLE IF NOT EXISTS pm_board_column (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    board_id     BIGINT       NOT NULL            COMMENT '看板ID',
    status_id    BIGINT       NOT NULL            COMMENT '映射状态ID',
    name         VARCHAR(64)  NOT NULL            COMMENT '列名称',
    sort_no      INT          NOT NULL DEFAULT 0  COMMENT '排序号（升序）',
    wip_limit    INT          NOT NULL DEFAULT 0  COMMENT 'WIP 上限（0 表示不限）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_board_column_status (tenant_id, board_id, status_id),
    KEY idx_pm_board_column_sort (tenant_id, board_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='看板列表';

-- 演示租户种子（与 init.sql 同构；已存在则跳过）
INSERT IGNORE INTO pm_sprint (id, tenant_id, project_id, name, goal, start_date, end_date, status)
VALUES
    (4001, 1, 1, 'Sprint 1', '演示迭代：登录体验优化', '2026-11-01', '2026-11-15', 'ACTIVE');

INSERT IGNORE INTO pm_board (id, tenant_id, project_id, name, board_type)
VALUES
    (5001, 1, 1, '演示看板', 'SCRUM');

INSERT IGNORE INTO pm_board_column (id, tenant_id, board_id, status_id, name, sort_no, wip_limit)
VALUES
    (5101, 1, 5001, 1001, '待办', 1, 0),
    (5102, 1, 5001, 1002, '进行中', 2, 5),
    (5103, 1, 5001, 1003, '已完成', 3, 0);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, name, status FROM pm_sprint WHERE tenant_id = 1;
--   SELECT id, name FROM pm_board WHERE tenant_id = 1;
--   SELECT id, board_id, status_id, name, wip_limit FROM pm_board_column WHERE tenant_id = 1;
-- =====================================================================
