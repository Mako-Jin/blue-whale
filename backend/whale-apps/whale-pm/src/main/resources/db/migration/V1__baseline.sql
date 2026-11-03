-- =====================================================================
-- BlueWhale 微服务平台 - 项目管理（whale-pm） Flyway 版本化基线 V1
-- =====================================================================
-- 适用库   : bluewhale_pm（MySQL 8.0+，utf8mb4）
-- 来源     : 由 db/init.sql 转换导出（2026-11-01 阶段 4 铺开）
-- 转换规则 : 1) 跳过 DROP TABLE；2) CREATE TABLE 统一 IF NOT EXISTS；
--            3) INSERT 统一 INSERT IGNORE（种子幂等）；4) 表尾统一 ENGINE/CHARSET/COLLATE。
-- 说明     : db/init.sql 为手工空库初始化入口，历史 migration-*.sql 仅供追溯，
--            二者均不在 spring.flyway.locations 扫描路径内（详见 db/README.md）。
-- =====================================================================

-- =====================================================================
-- BlueWhale 微服务平台 - 项目管理（whale-pm）数据库初始化脚本
-- =====================================================================
-- 库名       : bluewhale_pm
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成，无自增）、tenant_id、create_time、
--              update_time、deleted（逻辑删除 0/1，与 MyBatis-Plus 配置对齐）
-- 说明       : 表间关联均为逻辑外键（不建物理 FOREIGN KEY），便于微服务独立演进；
--              租户隔离由 starter 的 TenantLineInnerInterceptor 统一追加 tenant_id 条件。
-- 波次       : Wave 5.55 M1（项目域）+ M2（工作项域）+ M3（迭代与看板）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 项目空间表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_project (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_key  VARCHAR(16)  NOT NULL            COMMENT '项目键（租户内唯一，大写字母开头，如 BW）',
    name         VARCHAR(64)  NOT NULL            COMMENT '项目名称',
    project_type VARCHAR(16)  NOT NULL DEFAULT 'SCRUM'
                                                  COMMENT '项目类型：SCRUM 敏捷 / KANBAN 看板',
    visibility   VARCHAR(16)  NOT NULL DEFAULT 'PRIVATE'
                                                  COMMENT '可见性：PRIVATE 私有 / TEAM 团队可见',
    lead_user_id BIGINT       DEFAULT NULL        COMMENT '项目负责人用户ID（逻辑关联 sys_user.id）',
    description  VARCHAR(512) DEFAULT NULL        COMMENT '项目描述',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                                                  COMMENT '项目状态：ACTIVE 进行中 / ARCHIVED 已归档',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_project_key (tenant_id, project_key),
    KEY idx_pm_project_status (tenant_id, status),
    KEY idx_pm_project_lead (tenant_id, lead_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目空间表';

-- ---------------------------------------------------------------------
-- 2. 项目成员表（项目角色：ADMIN / MEMBER / VIEWER）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_project_member (
    id           BIGINT      NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT      NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id   BIGINT      NOT NULL            COMMENT '项目ID（逻辑关联 pm_project.id）',
    user_id      BIGINT      NOT NULL            COMMENT '成员用户ID（逻辑关联 sys_user.id）',
    project_role VARCHAR(16) NOT NULL DEFAULT 'MEMBER'
                                                 COMMENT '项目角色：ADMIN 管理员 / MEMBER 成员 / VIEWER 只读',
    joined_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT     NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_project_member (tenant_id, project_id, user_id),
    KEY idx_pm_member_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目成员表';

-- ---------------------------------------------------------------------
-- 3. 工作项状态表（每个项目独立一套；新建项目时由服务端复制默认模板）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_status (
    id         BIGINT      NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id  BIGINT      NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id BIGINT      NOT NULL            COMMENT '项目ID（逻辑关联 pm_project.id）',
    code       VARCHAR(32) NOT NULL            COMMENT '状态编码（项目内唯一），如 TODO / IN_PROGRESS / DONE',
    name       VARCHAR(32) NOT NULL            COMMENT '状态名称',
    category   VARCHAR(16) NOT NULL            COMMENT '状态分类：TODO 未开始 / IN_PROGRESS 进行中 / DONE 已完成',
    sort_no    INT         NOT NULL DEFAULT 0  COMMENT '排序号（越小越靠前）',
    is_default TINYINT     NOT NULL DEFAULT 0  COMMENT '是否默认状态：1 是 / 0 否',
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted    TINYINT     NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_status_code (tenant_id, project_id, code),
    KEY idx_pm_status_project (tenant_id, project_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作项状态表';

-- ---------------------------------------------------------------------
-- 4. 状态流转配置表（允许的状态迁移边）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_transition (
    id                 BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id          BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id         BIGINT       NOT NULL            COMMENT '项目ID（逻辑关联 pm_project.id）',
    from_status_id     BIGINT       NOT NULL            COMMENT '源状态ID（逻辑关联 pm_status.id）',
    to_status_id       BIGINT       NOT NULL            COMMENT '目标状态ID（逻辑关联 pm_status.id）',
    name               VARCHAR(64)  NOT NULL            COMMENT '流转动作名称，如 开始 / 完成 / 重新打开',
    require_comment    TINYINT      NOT NULL DEFAULT 0  COMMENT '是否要求填写评论：1 是 / 0 否',
    require_permission VARCHAR(128) DEFAULT NULL        COMMENT '附加权限编码（可选），如 sys:pm:item:transition',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_transition_edge (tenant_id, project_id, from_status_id, to_status_id),
    KEY idx_pm_transition_project (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='状态流转配置表';

-- ---------------------------------------------------------------------
-- 5. 种子数据（仅默认租户，便于接口冒烟与前端联调）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO pm_project (id, tenant_id, project_key, name, project_type, visibility,
                        lead_user_id, description, status)
VALUES (1, 1, 'DEMO', '示例项目', 'SCRUM', 'TEAM', 1, '内置示例项目，可直接用于冒烟', 'ACTIVE');

INSERT IGNORE INTO pm_project_member (id, tenant_id, project_id, user_id, project_role)
VALUES (1, 1, 1, 1, 'ADMIN');

INSERT IGNORE INTO pm_status (id, tenant_id, project_id, code, name, category, sort_no, is_default)
VALUES
    (1001, 1, 1, 'TODO', '待处理', 'TODO', 1, 1),
    (1002, 1, 1, 'IN_PROGRESS', '处理中', 'IN_PROGRESS', 2, 0),
    (1003, 1, 1, 'DONE', '已完成', 'DONE', 3, 0);

INSERT IGNORE INTO pm_transition (id, tenant_id, project_id, from_status_id, to_status_id, name,
                           require_comment, require_permission)
VALUES
    (2001, 1, 1, 1001, 1002, '开始', 0, NULL),
    (2002, 1, 1, 1002, 1003, '完成', 0, NULL),
    (2003, 1, 1, 1003, 1002, '重新打开', 0, NULL),
    (2004, 1, 1, 1002, 1001, '退回', 0, NULL);

-- ---------------------------------------------------------------------
-- 6. 工作项表（M2：Epic / Story / Task / Bug / Subtask 单表统一模型）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_work_item (
    id                 BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id          BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id         BIGINT       NOT NULL            COMMENT '项目ID（逻辑关联 pm_project.id）',
    item_no            INT          NOT NULL            COMMENT '项目内序号（从 1 递增）',
    item_key           VARCHAR(64)  NOT NULL            COMMENT '工作项键（项目键-序号，如 BW-102）',
    type               VARCHAR(16)  NOT NULL            COMMENT '类型：EPIC 史诗 / STORY 需求 / TASK 任务 / BUG 缺陷 / SUBTASK 子任务',
    title              VARCHAR(255) NOT NULL            COMMENT '标题',
    description        TEXT         DEFAULT NULL        COMMENT '描述（Markdown / 纯文本）',
    status_id          BIGINT       NOT NULL            COMMENT '状态ID（逻辑关联 pm_status.id）',
    priority           VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM'
                                                        COMMENT '优先级：HIGHEST / HIGH / MEDIUM / LOW / LOWEST',
    assignee_id        BIGINT       DEFAULT NULL        COMMENT '负责人用户ID',
    reporter_id        BIGINT       DEFAULT NULL        COMMENT '报告人用户ID',
    parent_id          BIGINT       DEFAULT NULL        COMMENT '父工作项ID（子任务必填）',
    sprint_id          BIGINT       DEFAULT NULL        COMMENT '迭代ID（M3 启用，逻辑关联 pm_sprint.id）',
    story_points       DECIMAL(5,1) DEFAULT NULL        COMMENT '故事点',
    due_date           DATE         DEFAULT NULL        COMMENT '截止日期',
    labels             VARCHAR(255) DEFAULT NULL        COMMENT '标签（英文逗号分隔）',
    rank_value         VARCHAR(64)  NOT NULL            COMMENT '排序值（12 位定长数字字符串，项目内唯一）',
    extension_json     TEXT         DEFAULT NULL        COMMENT '扩展字段 JSON（首版预留）',
    version            INT          NOT NULL DEFAULT 0  COMMENT '版本号（乐观锁）',
    client_request_id  VARCHAR(64)  DEFAULT NULL        COMMENT '客户端请求ID（创建幂等键）',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_item_no (tenant_id, project_id, item_no),
    UNIQUE KEY uk_pm_item_key (tenant_id, project_id, item_key),
    UNIQUE KEY uk_pm_item_idem (tenant_id, project_id, client_request_id),
    UNIQUE KEY uk_pm_item_rank (tenant_id, project_id, rank_value),
    KEY idx_pm_item_status (tenant_id, project_id, status_id),
    KEY idx_pm_item_assignee (tenant_id, project_id, assignee_id),
    KEY idx_pm_item_parent (tenant_id, project_id, parent_id),
    KEY idx_pm_item_sprint (tenant_id, project_id, sprint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作项表';

-- ---------------------------------------------------------------------
-- 7. 工作项关联表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_work_item_link (
    id          BIGINT      NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT      NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id  BIGINT      NOT NULL            COMMENT '项目ID',
    source_id   BIGINT      NOT NULL            COMMENT '源工作项ID',
    target_id   BIGINT      NOT NULL            COMMENT '目标工作项ID',
    link_type   VARCHAR(24) NOT NULL            COMMENT '关联类型：BLOCKS / BLOCKED_BY / RELATES / DUPLICATES / DUPLICATED_BY',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT     NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_link_edge (tenant_id, source_id, target_id, link_type),
    KEY idx_pm_link_project (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作项关联表';

-- ---------------------------------------------------------------------
-- 8. 工作项评论表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_comment (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id   BIGINT       NOT NULL            COMMENT '项目ID',
    work_item_id BIGINT       NOT NULL            COMMENT '工作项ID',
    author_id    BIGINT       NOT NULL            COMMENT '评论人用户ID',
    content      TEXT         NOT NULL            COMMENT '评论内容',
    parent_id    BIGINT       DEFAULT NULL        COMMENT '父评论ID（回复）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_pm_comment_item (tenant_id, work_item_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作项评论表';

-- ---------------------------------------------------------------------
-- 9. 工作项附件表（只存 whale-file 的 file_id 引用）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_attachment (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id   BIGINT       NOT NULL            COMMENT '项目ID',
    work_item_id BIGINT       NOT NULL            COMMENT '工作项ID',
    file_id      BIGINT       NOT NULL            COMMENT '文件ID（逻辑关联 whale-file）',
    file_name    VARCHAR(255) NOT NULL            COMMENT '文件名（展示用）',
    uploader_id  BIGINT       NOT NULL            COMMENT '上传人用户ID',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_attachment_file (tenant_id, work_item_id, file_id),
    KEY idx_pm_attachment_item (tenant_id, work_item_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作项附件表';

-- ---------------------------------------------------------------------
-- 10. 工作项动态表（append-only，不提供修改/删除端点）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_activity (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id   BIGINT       NOT NULL            COMMENT '项目ID',
    work_item_id BIGINT       DEFAULT NULL        COMMENT '工作项ID（项目级动态可为空）',
    actor_id     BIGINT       DEFAULT NULL        COMMENT '操作人用户ID',
    action       VARCHAR(32)  NOT NULL            COMMENT '动作：CREATE / UPDATE / TRANSITION / COMMENT / ATTACH / LINK / DELETE',
    field        VARCHAR(64)  DEFAULT NULL        COMMENT '变更字段',
    old_value    VARCHAR(512) DEFAULT NULL        COMMENT '旧值',
    new_value    VARCHAR(512) DEFAULT NULL        COMMENT '新值',
    trace_id     VARCHAR(64)  DEFAULT NULL        COMMENT '链路追踪ID',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_pm_activity_item (tenant_id, work_item_id, create_time),
    KEY idx_pm_activity_project (tenant_id, project_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作项动态表';

-- ---------------------------------------------------------------------
-- 11. 工作项种子数据（仅默认租户）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO pm_work_item (id, tenant_id, project_id, item_no, item_key, type, title, description,
                          status_id, priority, assignee_id, reporter_id, parent_id, story_points,
                          due_date, labels, rank_value, version)
VALUES
    (3001, 1, 1, 1, 'DEMO-1', 'STORY', '示例需求：登录体验优化', '演示用工作项',
     1001, 'MEDIUM', 1, 1, NULL, 5.0, '2026-11-30', 'demo,story', '000000001000', 0),
    (3002, 1, 1, 2, 'DEMO-2', 'TASK', '示例任务：补充单元测试', '演示用子任务',
     1002, 'HIGH', 1, 1, 3001, 3.0, '2026-11-15', 'demo,task', '000000002000', 0);

-- =====================================================================
-- Wave 5.55 M3：迭代与看板
-- =====================================================================

-- ---------------------------------------------------------------------
-- 12. 迭代表
-- ---------------------------------------------------------------------
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
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_sprint_name (tenant_id, project_id, name),
    KEY idx_pm_sprint_project (tenant_id, project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目迭代表';

-- ---------------------------------------------------------------------
-- 13. 看板表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_board (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id   BIGINT       NOT NULL            COMMENT '项目ID',
    name         VARCHAR(64)  NOT NULL            COMMENT '看板名称',
    board_type   VARCHAR(16)  NOT NULL DEFAULT 'SCRUM' COMMENT '看板类型：SCRUM 敏捷 / KANBAN 看板',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_board_name (tenant_id, project_id, name),
    KEY idx_pm_board_project (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目看板表';

-- ---------------------------------------------------------------------
-- 14. 看板列表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_board_column (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    board_id     BIGINT       NOT NULL            COMMENT '看板ID',
    status_id    BIGINT       NOT NULL            COMMENT '映射状态ID',
    name         VARCHAR(64)  NOT NULL            COMMENT '列名称',
    sort_no      INT          NOT NULL DEFAULT 0  COMMENT '排序号（升序）',
    wip_limit    INT          NOT NULL DEFAULT 0  COMMENT 'WIP 上限（0 表示不限）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_board_column_status (tenant_id, board_id, status_id),
    KEY idx_pm_board_column_sort (tenant_id, board_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='看板列表';

-- ---------------------------------------------------------------------
-- 15. 迭代与看板种子数据（仅默认租户）
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-auth',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    query_string   VARCHAR(1024) DEFAULT NULL           COMMENT '查询串（敏感字段已脱敏）',
    request_params TEXT          NULL                   COMMENT '请求体（JSON，敏感字段已脱敏）',
    response_body  TEXT          NULL                   COMMENT '响应体（JSON，敏感字段已脱敏，超限截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码（HTTP 状态）',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息）',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '调用方IP（兼容 IPv6）',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理（浏览器/客户端标识）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '接口耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_user_id (user_id),
    KEY idx_tenant_create (tenant_id, create_time),
    KEY idx_biz_status (biz_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='后端接口日志表';
