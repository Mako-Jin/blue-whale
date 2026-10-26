-- =====================================================================
-- BlueWhale 微服务平台 - 合同管理与电子签章（whale-contract）数据库初始化脚本
-- =====================================================================
-- 库名       : bluewhale_contract
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成，无自增）、tenant_id、create_time、
--              update_time、deleted（逻辑删除 0/1，与 MyBatis-Plus 配置对齐）
-- 说明       : 表间关联均为逻辑外键（不建物理 FOREIGN KEY），便于微服务独立演进
--              Wave 5.61 M1 仅落地合同主流程 4 表（印章/签署/证据表在 M2–M3 追加）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 合同主表
--    code 编号规则：HT-{类别码}-{yyyy}-{6位流水}，租户内唯一。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS ct_contract;
CREATE TABLE ct_contract (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code              VARCHAR(64)   NOT NULL            COMMENT '合同编号（租户内唯一）',
    title             VARCHAR(255)  NOT NULL            COMMENT '合同标题',
    category          VARCHAR(32)   NOT NULL            COMMENT '合同类别：PURCHASE/SALES/SERVICE/LEASE/OTHER',
    amount            DECIMAL(18,2) DEFAULT NULL        COMMENT '合同金额',
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    owner_id          BIGINT        DEFAULT NULL        COMMENT '负责人用户ID',
    dept_id           BIGINT        DEFAULT NULL        COMMENT '所属部门ID',
    status            VARCHAR(16)   NOT NULL DEFAULT 'DRAFT'
                                                       COMMENT '状态：DRAFT/APPROVING/APPROVED/REJECTED/WITHDRAWN/SIGNING/SIGNED/EFFECTIVE/ARCHIVED/TERMINATED/VOID',
    current_version   INT           NOT NULL DEFAULT 1  COMMENT '当前版本号',
    file_id           BIGINT        DEFAULT NULL        COMMENT '合同正文文件ID（whale-file）',
    effective_date    DATE          DEFAULT NULL        COMMENT '生效日期',
    expire_date       DATE          DEFAULT NULL        COMMENT '到期日期',
    archive_item_id   BIGINT        DEFAULT NULL        COMMENT '归档条目ID（whale-archive）',
    biz_type          VARCHAR(32)   DEFAULT NULL        COMMENT '弱关联业务类型（PM/ERP 等）',
    biz_id            VARCHAR(64)   DEFAULT NULL        COMMENT '弱关联业务ID',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_tenant_status_expire (tenant_id, status, expire_date),
    KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同主表';

-- ---------------------------------------------------------------------
-- 2. 合同相对方表（一份合同多方：甲方/乙方/其他）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS ct_contract_party;
CREATE TABLE ct_contract_party (
    id             BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    contract_id    BIGINT       NOT NULL            COMMENT '合同ID',
    party_type     VARCHAR(8)   NOT NULL            COMMENT '相对方类型：A 甲方 / B 乙方 / OTHER 其他',
    name           VARCHAR(128) NOT NULL            COMMENT '相对方名称',
    credit_code    VARCHAR(64)  DEFAULT NULL        COMMENT '统一社会信用代码',
    contact        VARCHAR(64)  DEFAULT NULL        COMMENT '联系人',
    phone          VARCHAR(32)  DEFAULT NULL        COMMENT '联系电话（脱敏展示）',
    signer_user_id BIGINT       DEFAULT NULL        COMMENT '签署人用户ID（外部相对方留空）',
    signer_name    VARCHAR(64)  DEFAULT NULL        COMMENT '签署人姓名',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同相对方表';

-- ---------------------------------------------------------------------
-- 3. 合同版本表（修订生成新版本，旧版本置 SUPERSEDED）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS ct_contract_version;
CREATE TABLE ct_contract_version (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    contract_id BIGINT       NOT NULL            COMMENT '合同ID',
    version_no  INT          NOT NULL            COMMENT '版本号（从 1 起）',
    file_id     BIGINT       DEFAULT NULL        COMMENT '该版本正文文件ID',
    sha256      VARCHAR(64)  DEFAULT NULL        COMMENT '正文文件 SHA-256',
    change_note VARCHAR(255) DEFAULT NULL        COMMENT '变更说明',
    status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                                                  COMMENT '版本状态：DRAFT 草稿 / SUPERSEDED 被替代 / EFFECTIVE 生效',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_contract_version (tenant_id, contract_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同版本表';

-- ---------------------------------------------------------------------
-- 4. 合同流程关联表（审批 / 签署阶段与工作流实例映射）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS ct_contract_flow;
CREATE TABLE ct_contract_flow (
    id                  BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    contract_id         BIGINT       NOT NULL            COMMENT '合同ID',
    version_id          BIGINT       DEFAULT NULL        COMMENT '合同版本ID',
    process_instance_id BIGINT       DEFAULT NULL        COMMENT '工作流实例ID',
    definition_id       BIGINT       DEFAULT NULL        COMMENT '流程定义ID',
    stage               VARCHAR(16)  NOT NULL DEFAULT 'APPROVAL'
                                                         COMMENT '阶段：APPROVAL 审批 / SIGN 签署',
    status              VARCHAR(16)  NOT NULL DEFAULT 'RUNNING'
                                                         COMMENT '状态：RUNNING/APPROVED/REJECTED/CANCELLED',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_contract (contract_id),
    KEY idx_instance (process_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同流程关联表';

-- ---------------------------------------------------------------------
-- 5. 种子数据（便于接口冒烟与前端联调）
-- ---------------------------------------------------------------------
INSERT INTO ct_contract (id, tenant_id, code, title, category, amount, currency, owner_id, dept_id, status,
                         current_version, effective_date, expire_date, remark)
VALUES
    (1, 1, 'HT-CG-2026-000001', '示例采购合同（软件维保）', 'PURCHASE', 50000.00, 'CNY', 1, 1, 'DRAFT',
     1, '2026-11-01', '2027-10-31', '内置种子'),
    (2, 1, 'HT-XS-2026-000002', '示例销售合同（设备交付）', 'SALES', 120000.00, 'CNY', 1, 1, 'APPROVED',
     1, '2026-10-01', '2027-09-30', '内置种子');

INSERT INTO ct_contract_party (id, tenant_id, contract_id, party_type, name, credit_code, contact, phone,
                               signer_user_id, signer_name)
VALUES
    (1, 1, 1, 'A', '蓝鲸科技有限公司', '91310000MA1K000001', '张三', '13800000001', 1, '张三'),
    (2, 1, 1, 'B', '示例软件服务商有限公司', '91310000MA1K000002', '李四', '13800000002', NULL, '李四'),
    (3, 1, 2, 'A', '蓝鲸科技有限公司', '91310000MA1K000001', '张三', '13800000001', 1, '张三'),
    (4, 1, 2, 'B', '示例设备制造有限公司', '91310000MA1K000003', '王五', '13800000003', NULL, '王五');

INSERT INTO ct_contract_version (id, tenant_id, contract_id, version_no, change_note, status)
VALUES
    (1, 1, 1, 1, '初始版本', 'DRAFT'),
    (2, 1, 2, 1, '初始版本', 'EFFECTIVE');

-- ---------------------------------------------------------------------
-- 6. 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS api_log;
CREATE TABLE api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-contract',
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
