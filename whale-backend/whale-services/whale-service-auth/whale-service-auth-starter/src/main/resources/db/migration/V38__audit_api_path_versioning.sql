-- =============================================================================
-- 文件        : V38__audit_api_path_versioning.sql
-- 说明        : 审计日志服务接口版本化后，同步权限点与网关白名单中的 API 路径
--
-- 背景：
--   whale-audit 对外接口由 /audit/log/** 统一为 /audit/v1/log/**（与 /job/v1/**、
--   /form/v1/**、/file/v1/** 等保持一致；网关仍按 Path=/audit/** 转发且不剥前缀）。
--   内网运维接口 /audit/internal/mq/** 不参与版本化，本脚本不会命中。
--
-- 说明：
--   - sys_permission.path 是权限管理界面展示与审计核对用的接口地址元数据；
--     运行期鉴权走 @RequirePermission 的权限码（perm_code）匹配，不依赖 path 列，
--     故本脚本不改变任何实际授权结果；
--   - sys_gateway_whitelist.path_pattern 必须与真实路径一致，否则前端/行为日志上报
--     会在网关被 JWT 过滤器拦截（401）；
--   - 只处理以 /audit/ 开头的路径，其它模块不受影响。
--
-- 幂等：
--   排除已是 /audit/v<n>/ 形态的行，重复执行不会叠加成 /audit/v1/v1/...
-- =============================================================================

UPDATE sys_permission
SET path = CONCAT('/audit/v1', SUBSTRING(path, CHAR_LENGTH('/audit') + 1)),
    update_time = CURRENT_TIMESTAMP
WHERE path LIKE '/audit/%'
  AND path NOT REGEXP '^/audit/v[0-9]+/'
  AND deleted = 0;

UPDATE sys_gateway_whitelist
SET path_pattern = CONCAT('/audit/v1', SUBSTRING(path_pattern, CHAR_LENGTH('/audit') + 1)),
    update_time = CURRENT_TIMESTAMP
WHERE path_pattern LIKE '/audit/%'
  AND path_pattern NOT REGEXP '^/audit/v[0-9]+/';
