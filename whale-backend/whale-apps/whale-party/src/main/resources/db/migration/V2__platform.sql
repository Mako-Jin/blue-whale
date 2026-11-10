-- =====================================================================
-- BlueWhale 党政工会应用（whale-party）业务编号流水
-- =====================================================================
-- 库名       : bluewhale_party
-- 状态       : M-P0 平台表。1 张编号流水表，供 M-P1 起支部编码 / 党员编号 /
--              工会会员号 / 提案号统一取号。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 业务编号流水表
-- ---------------------------------------------------------------------
-- 为什么不用 COUNT(*) 取号：
--   删除过一条记录后 COUNT 会回退，下一次取号就会与既有编号重复
--   （支部编码是台账主键的一部分，重复意味着两个支部抢同一个编号）。
--   因此维护一条只增的 current_seq，取号只做 +1。
--
-- 为什么唯一键不含 deleted：
--   本表是流水，不做逻辑删除。若唯一键带 deleted，删除后重建同键会插入第二行，
--   取号就会分裂成两条独立的计数器，出现重复编号。
--
-- 周期语义（seq_date）：
--   空串表示该编号不按周期重置（支部编码、党员编号）；
--   'yyyy' / 'yyyyMM' / 'yyyyMMdd' 表示按年 / 月 / 日重置（提案号、党费流水号）。
--   把周期直接写进键值，比在代码里判断「跨年要不要清零」更不容易错。
--
-- 并发取号（应用层实现，记录于此以免后续改错）：
--   INSERT INTO pty_sequence (id, tenant_id, seq_key, seq_date, current_seq)
--   VALUES (?, ?, ?, ?, LAST_INSERT_ID(1))
--   ON DUPLICATE KEY UPDATE current_seq = LAST_INSERT_ID(current_seq + 1);
--   再取 SELECT LAST_INSERT_ID();
--   利用 LAST_INSERT_ID(expr) 在同一连接内回填新值，避免「先 SELECT FOR UPDATE
--   再 UPDATE」的两段式写法在连接池下拿错值。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_sequence (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL               COMMENT '租户ID',
    seq_key        VARCHAR(64)   NOT NULL               COMMENT '编号键：BRANCH / MEMBER / UNION_MEMBER / RELIEF / PROPOSAL 等',
    seq_date       VARCHAR(16)   NOT NULL DEFAULT ''    COMMENT '周期标识：空串=不重置；yyyy / yyyyMM / yyyyMMdd=按周期重置',
    current_seq    BIGINT        NOT NULL DEFAULT 0     COMMENT '当前已发到的最末流水号（只增不减）',
    remark         VARCHAR(255)  DEFAULT NULL           COMMENT '备注（取号规则说明，便于运维排查）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_sequence (tenant_id, seq_key, seq_date),
    KEY idx_pty_sequence_key (seq_key)
) COMMENT='业务编号流水（按租户+编号键+周期取号，只增不减）';
