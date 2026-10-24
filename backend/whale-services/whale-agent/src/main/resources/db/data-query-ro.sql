-- =====================================================================
-- T14 只读数据通道：独立只读账号 + 白名单视图（whale-agent / bluewhale_agent）
-- =====================================================================
-- 执行方式：以具备 CREATE USER / GRANT 权限的账号（如 root）执行
-- 部署要求：
--   1. 将 CHANGE_ME_STRONG_PASSWORD 替换为强口令，并同步到环境变量
--      WHALE_AGENT_DATA_PASSWORD；
--   2. 按网络边界收紧 'whale_ai_ro'@'%' 的主机范围（建议改为应用网段）；
--   3. 视图使用 DEFINER 语义，只读账号只需视图的 SELECT 权限，不接触基表。
-- 应用侧对应配置（默认关闭）：
--   WHALE_AGENT_DATA_ENABLED=true
--   WHALE_AGENT_DATA_URL=jdbc:mysql://<host>:3306/bluewhale_agent?...
--   WHALE_AGENT_DATA_USERNAME=whale_ai_ro
--   WHALE_AGENT_DATA_ALLOWED_VIEWS=v_ai_agent_fact_summary,v_ai_agent_usage_summary
-- =====================================================================

CREATE USER IF NOT EXISTS 'whale_ai_ro'@'%' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';

-- 回收账号级全部权限，确保只保留下面显式授予的视图 SELECT
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'whale_ai_ro'@'%';

-- ---------------------------------------------------------------------
-- 白名单视图 1：结构化事实按类型汇总（不含 value_json 原始内容）
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_ai_agent_fact_summary AS
SELECT fact_type            AS fact_type,
       COUNT(*)             AS fact_count,
       MAX(captured_at)     AS last_captured_at
FROM agent_fact
WHERE deleted = 0
GROUP BY fact_type;

-- ---------------------------------------------------------------------
-- 白名单视图 2：智能体会话使用量按日汇总（不含消息正文）
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_ai_agent_usage_summary AS
SELECT DATE(create_time)                              AS stat_date,
       COUNT(*)                                       AS message_count,
       COUNT(DISTINCT conversation_id)                AS conversation_count
FROM agent_message
WHERE deleted = 0
GROUP BY DATE(create_time);

GRANT SELECT ON bluewhale_agent.v_ai_agent_fact_summary TO 'whale_ai_ro'@'%';
GRANT SELECT ON bluewhale_agent.v_ai_agent_usage_summary TO 'whale_ai_ro'@'%';

FLUSH PRIVILEGES;
