-- 审计/观测日志本地表下线：auth 不再作为审计兜底与本地落库点（2026-11-13 决策）
--
-- 背景：P1-3 审计跨库汇聚曾以 auth 为试点（本地 audit_log 兜底 + 四通道观测表同构）。
-- 现决策收敛：审计与观测日志统一由 whale-audit 服务落库与多实例兜底，auth 只作转发方：
--   1. audit_log：@AuditLog 记录经 whale.audit.forward 转发 whale-audit；
--      本地兜底关闭（whale.audit.forward.local-fallback=false），可靠性由 whale-audit 多实例承担。
--   2. api_log：本地接口观测关闭（whale.log.api.enabled=false）。
--   3. frontend_log / behavior_log：前端 SDK 直报 whale-audit，auth 本地从无写入（0 行）。
--   4. outbound_log：auth 无三方出站调用（0 行）。
-- 五张表均为 append-only 日志表，本迁移删除后不可恢复；删除前确认 whale-audit 已承接汇聚。
-- 此前数据去向：audit_log 679 行（转发失败兜底期间）、api_log 7423 行，均留在 bluewhale_auth 快照备份中。

DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS api_log;
DROP TABLE IF EXISTS outbound_log;
DROP TABLE IF EXISTS frontend_log;
DROP TABLE IF EXISTS behavior_log;
