-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V11
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 里程碑   : Wave 5.82 M3-P2 协同（日程 / 网盘 / 通讯录 / 工作台）
-- 说明     : 本脚本新建网盘 3 张表（文件夹 / 文件登记 / 授权），文件本体仍由
--            whale-file 托管，本库只保存引用与归属关系。
-- 幂等     : CREATE TABLE IF NOT EXISTS；无种子数据。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 网盘文件夹（parent_id = 0 表示根；scope 区分个人 / 部门）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_drive_folder (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    parent_id   BIGINT       NOT NULL DEFAULT 0  COMMENT '父文件夹ID（0 表示根，逻辑关联 oa_drive_folder.id）',
    name        VARCHAR(64)  NOT NULL            COMMENT '文件夹名称',
    scope       VARCHAR(16)  NOT NULL DEFAULT 'PERSONAL'
                                                 COMMENT '范围：PERSONAL 个人 / DEPARTMENT 部门',
    dept_id     BIGINT       DEFAULT NULL        COMMENT '部门ID（scope=DEPARTMENT 时生效，逻辑关联 sys_dept.id）',
    owner_id    BIGINT       NOT NULL            COMMENT '所有者用户ID（逻辑关联 sys_user.id）',
    owner_name  VARCHAR(64)  DEFAULT NULL        COMMENT '所有者姓名快照',
    sort_no     INT          NOT NULL DEFAULT 0  COMMENT '排序号（越小越靠前）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_drive_folder_parent (tenant_id, parent_id),
    KEY idx_oa_drive_folder_owner (tenant_id, owner_id),
    KEY idx_oa_drive_folder_dept (tenant_id, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 网盘文件夹表';

-- ---------------------------------------------------------------------
-- 2. 网盘文件登记（file_id 关联 whale-file，同一文件夹内不可重复登记）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_drive_file (
    id            BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    folder_id     BIGINT       NOT NULL            COMMENT '所属文件夹ID（逻辑关联 oa_drive_folder.id）',
    file_id       BIGINT       NOT NULL            COMMENT 'whale-file 文件ID',
    file_name     VARCHAR(255) DEFAULT NULL        COMMENT '文件名快照',
    file_size     BIGINT       NOT NULL DEFAULT 0  COMMENT '文件字节数快照',
    content_type  VARCHAR(128) DEFAULT NULL        COMMENT 'MIME 类型快照',
    uploader_id   BIGINT       DEFAULT NULL        COMMENT '上传人用户ID（逻辑关联 sys_user.id）',
    uploader_name VARCHAR(64)  DEFAULT NULL        COMMENT '上传人姓名快照',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_drive_file_folder (tenant_id, folder_id, file_id),
    KEY idx_oa_drive_file_folder (tenant_id, folder_id),
    KEY idx_oa_drive_file_uploader (tenant_id, uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 网盘文件登记表';

-- ---------------------------------------------------------------------
-- 3. 网盘文件夹授权（subject 为用户或部门；effect 区分允许 / 拒绝）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_drive_acl (
    id           BIGINT      NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT      NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    folder_id    BIGINT      NOT NULL            COMMENT '文件夹ID（逻辑关联 oa_drive_folder.id）',
    subject_type VARCHAR(16) NOT NULL            COMMENT '授权主体类型：USER 用户 / DEPT 部门',
    subject_id   BIGINT      NOT NULL            COMMENT '授权主体ID（用户ID或部门ID）',
    subject_name VARCHAR(64) DEFAULT NULL        COMMENT '授权主体名称快照',
    effect       VARCHAR(16) NOT NULL DEFAULT 'ALLOW'
                                                 COMMENT '效果：ALLOW 允许 / DENY 拒绝',
    permission   VARCHAR(16) NOT NULL DEFAULT 'READ'
                                                 COMMENT '权限：READ 读 / WRITE 写 / MANAGE 管理',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT     NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_drive_acl_subject (tenant_id, folder_id, subject_type, subject_id, effect),
    KEY idx_oa_drive_acl_folder (tenant_id, folder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 网盘文件夹授权表';
