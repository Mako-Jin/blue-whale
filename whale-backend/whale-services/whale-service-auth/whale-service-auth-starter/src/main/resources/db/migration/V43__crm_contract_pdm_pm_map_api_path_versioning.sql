-- =============================================================================
-- 文件        : V43__crm_contract_pdm_pm_map_api_path_versioning.sql
-- 说明        : CRM/合同/PDM/PM/地图对外接口版本化后，同步权限点中的 API 路径
--
-- 背景：
--   五个模块的对外接口由 /<service>/** 统一为 /<service>/v1/**（与 /pay/v1/**、
--   /erp/v1/** 等保持一致；网关仍按前缀转发且不剥前缀）。
--   以下路径本轮保持不变：
--     - /<service>/internal/**：跨服务调用；
--     - /<service>/overview：SDK 概览端点，监控聚合配置引用；
--     - /contract/sign/callback/**：对外登记的电子签回调地址。
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
        '/crm/', '/crm/v1/'),
        '/contract/', '/contract/v1/'),
        '/pdm/', '/pdm/v1/'),
        '/pm/', '/pm/v1/'),
        '/map/', '/map/v1/'),
    update_time = CURRENT_TIMESTAMP
WHERE perm_type = 'API'
  AND deleted = 0
  AND (path LIKE '/crm/%' OR path LIKE '%/crm/%'
    OR path LIKE '/contract/%' OR path LIKE '%/contract/%'
    OR path LIKE '/pdm/%' OR path LIKE '%/pdm/%'
    OR path LIKE '/pm/%' OR path LIKE '%/pm/%'
    OR path LIKE '/map/%' OR path LIKE '%/map/%')
  AND path NOT LIKE '%/internal/%'
  AND path NOT LIKE '%/overview%'
  AND path NOT LIKE '%/callback%'
  AND path NOT LIKE '%/crm/v1/%'
  AND path NOT LIKE '%/contract/v1/%'
  AND path NOT LIKE '%/pdm/v1/%'
  AND path NOT LIKE '%/pm/v1/%'
  AND path NOT LIKE '%/map/v1/%';
