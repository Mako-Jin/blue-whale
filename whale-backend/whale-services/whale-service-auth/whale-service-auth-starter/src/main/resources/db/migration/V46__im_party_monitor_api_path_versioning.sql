-- =============================================================================
-- 文件        : V46__im_party_monitor_api_path_versioning.sql
-- 说明        : IM/党建/监控对外接口版本化后，同步权限点中的 API 路径
--
-- 背景：
--   三个模块的对外接口由 /<service>/** 统一为 /<service>/v1/**（与 /pay/v1/**、
--   /erp/v1/** 等保持一致；网关仍按前缀转发且不剥前缀）。
--   以下路径本轮保持不变：
--     - /im/internal/**：跨服务调用；
--     - /im/ws、/im/world：WebSocket 升级路径（网关白名单放行）；
--     - /monitor/overview：SDK 概览端点，监控聚合配置引用。
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
SET path = REPLACE(REPLACE(REPLACE(path,
        '/im/', '/im/v1/'),
        '/party/', '/party/v1/'),
        '/monitor/', '/monitor/v1/'),
    update_time = CURRENT_TIMESTAMP
WHERE type = 'API'
  AND deleted = 0
  AND (path LIKE '/im/%' OR path LIKE '%/im/%'
    OR path LIKE '/party/%' OR path LIKE '%/party/%'
    OR path LIKE '/monitor/%' OR path LIKE '%/monitor/%')
  AND path NOT LIKE '%/internal/%'
  AND path NOT LIKE '%/overview%'
  AND path NOT LIKE '%/im/ws'
  AND path NOT LIKE '%/im/world'
  AND path NOT LIKE '%/im/v1/%'
  AND path NOT LIKE '%/party/v1/%'
  AND path NOT LIKE '%/monitor/v1/%';
