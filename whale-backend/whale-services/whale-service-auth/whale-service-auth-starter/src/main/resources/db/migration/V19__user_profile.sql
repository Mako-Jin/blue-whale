-- =============================================================================
-- 文件        : V19__user_profile.sql
-- 说明        : 权限中心扩展 M3 —— 身份档案（聚合 + 6 张子表）
--
-- 背景：按 auth_tbl_user_profile / user_contact / user_credential / user_education /
--       user_employment / user_emergency_contact / user_secret_question 处置矩阵
--       （计划 §1.2 M3 行）新建 sys_user_profile 及 6 张子表。
--
-- 聚合不变量（应用层聚合根强制，DB 只建普通索引兜底）：
--   ① user_id 全局唯一 —— 同一用户至多一份档案；
--   ② 证件号、密保答案必须密文存储（AES-GCM，见 SensitiveFieldCipher）；
--   ③ 主联系方式 / 主证件同类型唯一；
--   ④ 子表只允许通过聚合根整体替换，不提供单条子表 CRUD。
--
-- 本脚本（幂等，可安全重放）：
--   1. 建表 sys_user_profile + 6 张子表；
--   2. 权限点 sys:user:profile:*（号段 2390–2394，判重键 perm_code）；
--   3. 授予 tenant_admin（按 perm_code 联查）；
--   4. 「身份档案」菜单（tenant_id=1 + 复制到业务租户）。
--
-- 号段登记：2390–2394 = 身份档案（2390 view、2391 save、2392 delete、2393
--           credential-view、2394 预留）。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 档案主表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_profile (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    user_id         BIGINT       NOT NULL                COMMENT '用户ID（逻辑关联 sys_user.id，全局唯一）',
    gender          VARCHAR(8)   DEFAULT NULL            COMMENT '性别：MALE 男 / FEMALE 女 / UNKNOWN 未知',
    birth_date      DATE         DEFAULT NULL            COMMENT '出生日期',
    birth_place     VARCHAR(128) DEFAULT NULL            COMMENT '籍贯/出生地',
    home_address    VARCHAR(255) DEFAULT NULL            COMMENT '家庭住址',
    nation          VARCHAR(32)  DEFAULT NULL            COMMENT '民族',
    political_status VARCHAR(32) DEFAULT NULL            COMMENT '政治面貌',
    marital_status  VARCHAR(16)  DEFAULT NULL            COMMENT '婚姻状况：UNMARRIED/MARRIED/DIVORCED/WIDOWED',
    remarks         VARCHAR(512) DEFAULT NULL            COMMENT '备注',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_profile_user (user_id),
    KEY idx_profile_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户身份档案主表';

-- ---------------------------------------------------------------------
-- 1.1 联系方式子表（多联系方式 + 已验证标记）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_contact (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    user_id         BIGINT       NOT NULL                COMMENT '用户ID（逻辑关联 sys_user_profile.user_id）',
    contact_type    VARCHAR(16)  NOT NULL                COMMENT '联系方式类型：MOBILE 手机 / EMAIL 邮箱 / WECHAT 微信 / DINGTALK 钉钉 / OTHER 其他',
    contact_value   VARCHAR(128) NOT NULL                COMMENT '联系方式值',
    is_primary      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否主联系方式：1 是（同类型唯一，聚合根保证）',
    verified        TINYINT      NOT NULL DEFAULT 0      COMMENT '已验证标记：0 未验证 / 1 已验证',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号（同类型内有序）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_contact_user (user_id, contact_type),
    KEY idx_contact_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户联系方式子表';

-- ---------------------------------------------------------------------
-- 1.2 证件子表（证件号 AES-GCM 加密后 Base64 落库）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_credential (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    user_id         BIGINT       NOT NULL                COMMENT '用户ID',
    credential_type VARCHAR(16)  NOT NULL                COMMENT '证件类型：ID_CARD 身份证 / PASSPORT 护照 / HK_MO_PASS 港澳通行证 / OTHER 其他',
    credential_no   TEXT         NOT NULL                COMMENT '证件号（AES-GCM 密文 Base64，禁止明文）',
    holder_name     VARCHAR(64)  DEFAULT NULL            COMMENT '证件持有人姓名',
    is_primary      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否主证件：1 是（同类型唯一，聚合根保证）',
    issue_date      DATE         DEFAULT NULL            COMMENT '签发日期',
    expiry_date     DATE         DEFAULT NULL            COMMENT '有效期至',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_credential_user (user_id, credential_type),
    KEY idx_credential_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户证件子表（证件号加密存储）';

-- ---------------------------------------------------------------------
-- 1.3 教育经历子表（多条有序）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_education (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    user_id         BIGINT       NOT NULL                COMMENT '用户ID',
    school          VARCHAR(128) NOT NULL                COMMENT '学校名称',
    major           VARCHAR(128) DEFAULT NULL            COMMENT '专业',
    degree          VARCHAR(16)  DEFAULT NULL            COMMENT '学历：DOCTOR 硕博 / MASTER 硕士 / BACHELOR 本科 / ASSOCIATE 大专 / OTHER 其他',
    start_date      DATE         DEFAULT NULL            COMMENT '入学时间',
    end_date        DATE         DEFAULT NULL            COMMENT '毕业时间',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号（倒序展示）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_education_user (user_id),
    KEY idx_education_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户教育经历子表';

-- ---------------------------------------------------------------------
-- 1.4 工作经历子表（多条有序）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_employment (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    user_id         BIGINT       NOT NULL                COMMENT '用户ID',
    company         VARCHAR(128) NOT NULL                COMMENT '公司名称',
    department      VARCHAR(128) DEFAULT NULL            COMMENT '部门',
    position        VARCHAR(64)  DEFAULT NULL            COMMENT '职位',
    employment_type VARCHAR(16)  DEFAULT NULL            COMMENT '用工性质：FORMAL 正式 / CONTRACT 合同 / INTERN 实习 / OUTSOURCING 外包',
    start_date      DATE         DEFAULT NULL            COMMENT '入职时间',
    end_date        DATE         DEFAULT NULL            COMMENT '离职时间（在职为 NULL）',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号（倒序展示）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_employment_user (user_id),
    KEY idx_employment_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户工作经历子表';

-- ---------------------------------------------------------------------
-- 1.5 紧急联系人子表（多条有序）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_emergency_contact (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    user_id         BIGINT       NOT NULL                COMMENT '用户ID',
    contact_name    VARCHAR(64)  NOT NULL                COMMENT '联系人姓名',
    relationship    VARCHAR(16)  DEFAULT NULL            COMMENT '与本人关系：PARENT / SPOUSE / CHILD / SIBLING / FRIEND / OTHER',
    phone           VARCHAR(32)  DEFAULT NULL            COMMENT '联系电话',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_emergency_user (user_id),
    KEY idx_emergency_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户紧急联系人子表';

-- ---------------------------------------------------------------------
-- 1.6 密保问题子表（答案 AES-GCM 加密后 Base64 落库）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_secret_question (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    user_id         BIGINT       NOT NULL                COMMENT '用户ID',
    question        VARCHAR(255) NOT NULL                COMMENT '密保问题',
    answer_encrypted TEXT        NOT NULL                COMMENT '密保答案（AES-GCM 密文 Base64，禁止明文）',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_secret_user (user_id),
    KEY idx_secret_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户密保问题子表（答案加密存储）';

-- ---------------------------------------------------------------------
-- 2. 权限点（判重键：perm_code —— 主键撞号不得让整条语句静默跳过）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2390, 0, 'sys:user:profile:view', '查询身份档案', 'API', '/user-profile/v1/*', 'GET', 2390, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:user:profile:view');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2391, 0, 'sys:user:profile:save', '保存身份档案', 'API', '/user-profile/v1', 'POST', 2391, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:user:profile:save');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2392, 0, 'sys:user:profile:delete', '删除身份档案', 'API', '/user-profile/v1/*', 'DELETE', 2392, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:user:profile:delete');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2393, 0, 'sys:user:profile:credential-view', '查看证件明文', 'API', '/user-profile/v1/*/credential/v1/*', 'GET', 2393, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:user:profile:credential-view');

-- ---------------------------------------------------------------------
-- 3. 授予 tenant_admin（按 perm_code 联查）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.perm_code IN
         ('sys:user:profile:view', 'sys:user:profile:save', 'sys:user:profile:delete',
          'sys:user:profile:credential-view')
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- ---------------------------------------------------------------------
-- 4. 菜单（判重键：tenant_id + path，主键 MD5 派生）
--    父节点取各租户自己的 /system 目录，找不到时回落顶级（0）。
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:user-profile'), 1, 15), 16, 10) AS UNSIGNED),
       1,
       COALESCE((SELECT id FROM sys_menu
                 WHERE tenant_id = 1 AND path = '/system' AND menu_type = 'DIR' AND deleted = 0
                 LIMIT 1), 0),
       '身份档案', '/system/user-profile', 'system/user-profile/index', 'idcard', 13,
       'MENU', 'sys:user:profile:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/system/user-profile' AND deleted = 0
);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:user-profile')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, COALESCE(sysdir.id, 0), '身份档案', '/system/user-profile', 'system/user-profile/index',
       'idcard', 13, 'MENU', 'sys:user:profile:view', 1, 1
FROM sys_tenant t
LEFT JOIN sys_menu sysdir ON sysdir.tenant_id = t.id AND sysdir.path = '/system'
     AND sysdir.menu_type = 'DIR' AND sysdir.deleted = 0
WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu x
      WHERE x.tenant_id = t.id AND x.path = '/system/user-profile' AND x.deleted = 0
  );
