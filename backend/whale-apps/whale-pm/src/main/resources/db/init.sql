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
-- 波次       : Wave 5.55 M1（项目域：项目 / 成员 / 状态 / 流转）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 项目空间表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS pm_project;
CREATE TABLE pm_project (
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
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_project_key (tenant_id, project_key),
    KEY idx_pm_project_status (tenant_id, status),
    KEY idx_pm_project_lead (tenant_id, lead_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目空间表';

-- ---------------------------------------------------------------------
-- 2. 项目成员表（项目角色：ADMIN / MEMBER / VIEWER）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS pm_project_member;
CREATE TABLE pm_project_member (
    id           BIGINT      NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT      NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id   BIGINT      NOT NULL            COMMENT '项目ID（逻辑关联 pm_project.id）',
    user_id      BIGINT      NOT NULL            COMMENT '成员用户ID（逻辑关联 sys_user.id）',
    project_role VARCHAR(16) NOT NULL DEFAULT 'MEMBER'
                                                 COMMENT '项目角色：ADMIN 管理员 / MEMBER 成员 / VIEWER 只读',
    joined_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT     NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_project_member (tenant_id, project_id, user_id),
    KEY idx_pm_member_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目成员表';

-- ---------------------------------------------------------------------
-- 3. 工作项状态表（每个项目独立一套；新建项目时由服务端复制默认模板）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS pm_status;
CREATE TABLE pm_status (
    id         BIGINT      NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id  BIGINT      NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id BIGINT      NOT NULL            COMMENT '项目ID（逻辑关联 pm_project.id）',
    code       VARCHAR(32) NOT NULL            COMMENT '状态编码（项目内唯一），如 TODO / IN_PROGRESS / DONE',
    name       VARCHAR(32) NOT NULL            COMMENT '状态名称',
    category   VARCHAR(16) NOT NULL            COMMENT '状态分类：TODO 未开始 / IN_PROGRESS 进行中 / DONE 已完成',
    sort_no    INT         NOT NULL DEFAULT 0  COMMENT '排序号（越小越靠前）',
    is_default TINYINT     NOT NULL DEFAULT 0  COMMENT '是否默认状态：1 是 / 0 否',
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted    TINYINT     NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_status_code (tenant_id, project_id, code),
    KEY idx_pm_status_project (tenant_id, project_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作项状态表';

-- ---------------------------------------------------------------------
-- 4. 状态流转配置表（允许的状态迁移边）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS pm_transition;
CREATE TABLE pm_transition (
    id                 BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id          BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id         BIGINT       NOT NULL            COMMENT '项目ID（逻辑关联 pm_project.id）',
    from_status_id     BIGINT       NOT NULL            COMMENT '源状态ID（逻辑关联 pm_status.id）',
    to_status_id       BIGINT       NOT NULL            COMMENT '目标状态ID（逻辑关联 pm_status.id）',
    name               VARCHAR(64)  NOT NULL            COMMENT '流转动作名称，如 开始 / 完成 / 重新打开',
    require_comment    TINYINT      NOT NULL DEFAULT 0  COMMENT '是否要求填写评论：1 是 / 0 否',
    require_permission VARCHAR(128) DEFAULT NULL        COMMENT '附加权限编码（可选），如 sys:pm:item:transition',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_transition_edge (tenant_id, project_id, from_status_id, to_status_id),
    KEY idx_pm_transition_project (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='状态流转配置表';

-- ---------------------------------------------------------------------
-- 5. 种子数据（仅默认租户，便于接口冒烟与前端联调）
-- ---------------------------------------------------------------------
INSERT INTO pm_project (id, tenant_id, project_key, name, project_type, visibility,
                        lead_user_id, description, status)
VALUES (1, 1, 'DEMO', '示例项目', 'SCRUM', 'TEAM', 1, '内置示例项目，可直接用于冒烟', 'ACTIVE');

INSERT INTO pm_project_member (id, tenant_id, project_id, user_id, project_role)
VALUES (1, 1, 1, 1, 'ADMIN');

INSERT INTO pm_status (id, tenant_id, project_id, code, name, category, sort_no, is_default)
VALUES
    (1001, 1, 1, 'TODO', '待处理', 'TODO', 1, 1),
    (1002, 1, 1, 'IN_PROGRESS', '处理中', 'IN_PROGRESS', 2, 0),
    (1003, 1, 1, 'DONE', '已完成', 'DONE', 3, 0);

INSERT INTO pm_transition (id, tenant_id, project_id, from_status_id, to_status_id, name,
                           require_comment, require_permission)
VALUES
    (2001, 1, 1, 1001, 1002, '开始', 0, NULL),
    (2002, 1, 1, 1002, 1003, '完成', 0, NULL),
    (2003, 1, 1, 1003, 1002, '重新打开', 0, NULL),
    (2004, 1, 1, 1002, 1001, '退回', 0, NULL);
