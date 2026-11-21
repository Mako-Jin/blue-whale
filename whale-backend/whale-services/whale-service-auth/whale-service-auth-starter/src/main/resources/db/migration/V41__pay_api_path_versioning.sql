-- =============================================================================
-- 文件        : V41__pay_api_path_versioning.sql
-- 说明        : 支付服务接口版本化后，同步权限点中的 API 路径
--
-- 背景：
--   whale-pay 对外/内网接口由 /pay/** 统一为 /pay/v1/**（与 /form/v1/**、
--   /job/v1/**、/audit/v1/** 等保持一致；网关仍按 Path=/pay/** 转发且不剥前缀）。
--   内网端点同步调整为 /pay/v1/internal/**，由网关 /*/*/internal/** 规则统一 403。
--
-- 说明：
--   - sys_permission.path 是权限管理界面展示与审计核对用的接口地址元数据；
--     运行期鉴权走 @RequirePermission 的权限码（perm_code）匹配，不依赖 path 列，
--     故本脚本不改变任何实际授权结果；
--   - 只处理以 /pay/ 开头的路径，其它模块不受影响。
--
-- 幂等：
--   排除已是 /pay/v<n>/ 形态的行，重复执行不会叠加成 /pay/v1/v1/...
-- =============================================================================

UPDATE sys_permission
SET path = CONCAT('/pay/v1', SUBSTRING(path, CHAR_LENGTH('/pay') + 1)),
    update_time = CURRENT_TIMESTAMP
WHERE path LIKE '/pay/%'
  AND path NOT REGEXP '^/pay/v[0-9]+/'
  AND deleted = 0;
