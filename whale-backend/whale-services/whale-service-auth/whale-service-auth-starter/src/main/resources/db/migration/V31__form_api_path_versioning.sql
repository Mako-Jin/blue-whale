-- =============================================================================
-- 文件        : V31__form_api_path_versioning.sql
-- 说明        : 表单服务接口版本化后，同步已有权限点的 API 路径
--
-- 背景：
--   whale-form 更名 whale-service-form，同时按 REQ-ARCH-003（网关不剥前缀）把控制器
--   路径改为 /form/v{version}/**，实际对外路径由 /form/definition 变为
--   /form/v1/definition（其余端点同理）。sys_permission.path 是权限管理界面展示与
--   审计核对用的接口地址元数据，必须与真实路径保持一致，否则排障与权限复核会按旧路径
--   找不到对应接口。
--
-- 说明：
--   - 运行时鉴权走 @RequirePermission 的权限码（perm_code）匹配，不依赖 path 列，
--     故本脚本不改变任何实际授权结果；
--   - 只处理 path 以 /form/ 开头的 API 型权限点，MENU/按钮等其它类型不受影响。
--
-- 幂等：
--   排除已是 /form/v<n>/ 形态的行，重复执行不会叠加成 /form/v1/v1/...
-- =============================================================================

UPDATE sys_permission
SET path = CONCAT('/form/v1', SUBSTRING(path, CHAR_LENGTH('/form') + 1)),
    update_time = CURRENT_TIMESTAMP
WHERE path LIKE '/form/%'
  AND path NOT REGEXP '^/form/v[0-9]+/'
  AND deleted = 0;
