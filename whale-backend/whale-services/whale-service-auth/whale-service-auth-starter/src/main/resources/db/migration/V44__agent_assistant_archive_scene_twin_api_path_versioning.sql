-- =============================================================================
-- 文件        : V44__agent_assistant_archive_scene_twin_api_path_versioning.sql
-- 说明        : Agent/Assistant/档案/场景/数字孪生对外接口版本化后，同步权限点路径
--
-- 背景：
--   五个模块的对外接口由 /<service>/** 统一为 /<service>/v1/**（与 /pay/v1/**、
--   /erp/v1/** 等保持一致；网关仍按前缀转发且不剥前缀）。
--   以下路径本轮保持不变：
--     - /<service>/internal/**：跨服务调用；
--     - /<service>/overview：SDK 概览端点，监控聚合配置引用；
--     - /assistant/openapi/v1/**：自带版本段的开放接口，已对外登记；
--     - /agent/mcp：MCP 协议直连端点（IDE 直连 8090，不经网关）。
--
-- 说明：
--   - path 列支持逗号分隔的多条路径，因此用 REPLACE 而不是 CONCAT/SUBSTRING；
--   - sys_permission.path 只是权限管理界面展示与审计核对的元数据，运行期鉴权
--     走 perm_code，本脚本不改变任何实际授权结果。
--
-- 幂等：
--   已经是 /<service>/v1/ 的行不再处理，重复执行不会叠加成 /v1/v1/。
-- =============================================================================

UPDATE sys_permission
SET path = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(path,
        '/agent/', '/agent/v1/'),
        '/assistant/', '/assistant/v1/'),
        '/archive/', '/archive/v1/'),
        '/scene/', '/scene/v1/'),
        '/twin/', '/twin/v1/'),
    update_time = CURRENT_TIMESTAMP
WHERE perm_type = 'API'
  AND deleted = 0
  AND (path LIKE '/agent/%' OR path LIKE '%/agent/%'
    OR path LIKE '/assistant/%' OR path LIKE '%/assistant/%'
    OR path LIKE '/archive/%' OR path LIKE '%/archive/%'
    OR path LIKE '/scene/%' OR path LIKE '%/scene/%'
    OR path LIKE '/twin/%' OR path LIKE '%/twin/%')
  AND path NOT LIKE '%/internal/%'
  AND path NOT LIKE '%/overview%'
  AND path NOT LIKE '%/openapi/%'
  AND path NOT LIKE '%/mcp%'
  AND path NOT LIKE '%/agent/v1/%'
  AND path NOT LIKE '%/assistant/v1/%'
  AND path NOT LIKE '%/archive/v1/%'
  AND path NOT LIKE '%/scene/v1/%'
  AND path NOT LIKE '%/twin/v1/%';
