-- =====================================================================
-- V2：审计日志追加写守卫（等保 2.0「审计记录应受到保护，避免被删除、修改或覆盖」）
-- =====================================================================
-- 背景：audit_log 是合规证据表，服务层只允许 INSERT + SELECT；归档任务
--       （AuditLogArchiver）只导出不删除，因此「禁止 UPDATE / DELETE」不会
--       影响任何既有业务路径。
-- 分工：
--   · 代码层闸门  scripts/security/check-audit-append-only.py   —— 禁止提交改写语句
--   · 数据库层闸门 本迁移 + scripts/security/AuditAppendOnlyGuard.java —— 连手工 SQL/DBA
--     客户端也改不动。守卫工具同时负责其余各业务库（本迁移只管 whale-audit 自己的库）。
-- 范围：仅 audit_log。api_log / outbound_log / frontend_log / behavior_log 有保留期清理
--       任务（LogRetentionCleaner 按 cron 删除过期行），加 DELETE 守卫会让保留期失效。
-- 幂等：先 DROP TRIGGER IF EXISTS 再建，可反复执行。
-- =====================================================================

DROP TRIGGER IF EXISTS trg_audit_log_no_update;

CREATE TRIGGER trg_audit_log_no_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_log is append-only, UPDATE is forbidden';

DROP TRIGGER IF EXISTS trg_audit_log_no_delete;

CREATE TRIGGER trg_audit_log_no_delete
    BEFORE DELETE ON audit_log
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_log is append-only, DELETE is forbidden';
