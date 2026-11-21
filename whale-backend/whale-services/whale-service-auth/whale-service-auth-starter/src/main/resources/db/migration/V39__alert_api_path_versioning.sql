-- =============================================================================
-- 文件        : V39__alert_api_path_versioning.sql
-- 说明        : 告警服务接口版本化后，同步权限点与网关白名单中的 API 路径
--
-- 背景：
--   whale-alert 对外接口由 /alert/** 统一为 /alert/v1/**（与 /audit/v1/**、
--   /job/v1/**、/form/v1/** 等保持一致；网关仍按 Path=/alert/** 转发且不剥前缀）。
--   内网端点同步调整为 /alert/v1/internal/**，由网关 /*/*/internal/** 规则统一 403。
--
-- 说明：
--   - sys_permission.path 是权限管理界面展示与审计核对用的接口地址元数据；
--     运行期鉴权走 @RequirePermission 的权限码（perm_code）匹配，不依赖 path 列，
--     故本脚本不改变任何实际授权结果；
--   - sys_gateway_whitelist.path_pattern 必须与真实路径一致，否则前端/行为日志上报
--     会在网关被 JWT 过滤器拦截（401）；
--   - 只处理以 /alert/ 开头的路径，其它模块不受影响。
--
-- 幂等：
--   排除已是 /alert/v<n>/ 形态的行，重复执行不会叠加成 /alert/v1/v1/...
-- =============================================================================

UPDATE sys_permission
SET path = CONCAT('/alert/v1', SUBSTRING(path, CHAR_LENGTH('/alert') + 1)),
    update_time = CURRENT_TIMESTAMP
WHERE path LIKE '/alert/%'
  AND path NOT REGEXP '^/alert/v[0-9]+/'
  AND deleted = 0;

UPDATE sys_gateway_whitelist
SET path_pattern = CONCAT('/alert/v1', SUBSTRING(path_pattern, CHAR_LENGTH('/alert') + 1)),
    update_time = CURRENT_TIMESTAMP
WHERE path_pattern LIKE '/alert/%'
  AND path_pattern NOT REGEXP '^/alert/v[0-9]+/';
