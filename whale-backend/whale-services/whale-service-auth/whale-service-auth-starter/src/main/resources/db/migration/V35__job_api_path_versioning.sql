-- =============================================================================
-- 文件        : V35__job_api_path_versioning.sql
-- 说明        : 定时任务管理接口版本化后，同步已有权限点的 API 路径
--
-- 背景：
--   whale-job 管理端接口由 /job/** 统一为 /job/v1/**（与 /file/v1/**、/form/v1/** 等
--   保持一致；网关仍按 Path=/job/** 转发且不剥前缀）。内网执行器协议
--   /job/internal/executor/** 不参与版本化，本脚本不会命中这类未入库的路径。
--
-- 说明：
--   - sys_permission.path 是权限管理界面展示与审计核对用的接口地址元数据；
--     运行期鉴权走 @RequirePermission 的权限码（perm_code）匹配，不依赖 path 列，
--     故本脚本不改变任何实际授权结果；
--   - 只处理 path 以 /job/ 开头的 API 型权限点，MENU/按钮等其它类型不受影响。
--
-- 幂等：
--   排除已是 /job/v<n>/ 形态的行，重复执行不会叠加成 /job/v1/v1/...
-- =============================================================================

UPDATE sys_permission
SET path = CONCAT('/job/v1', SUBSTRING(path, CHAR_LENGTH('/job') + 1)),
    update_time = CURRENT_TIMESTAMP
WHERE path LIKE '/job/%'
  AND path NOT REGEXP '^/job/v[0-9]+/'
  AND deleted = 0;
