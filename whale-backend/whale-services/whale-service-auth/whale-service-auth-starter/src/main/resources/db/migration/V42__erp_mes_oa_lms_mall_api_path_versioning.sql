-- =============================================================================
-- 文件        : V42__erp_mes_oa_lms_mall_api_path_versioning.sql
-- 说明        : ERP/MES/OA/LMS/MALL 对外接口版本化后，同步权限点中的 API 路径
--
-- 背景：
--   五个应用的对外接口由 /<service>/** 统一为 /<service>/v1/**（与 /pay/v1/**、
--   /form/v1/**、/file/v1/** 等保持一致；网关仍按前缀转发且不剥前缀）。
--   内网端点 /<service>/internal/** 与概览端点 /<service>/overview 本轮保持不变，
--   避免破坏跨服务调用与监控聚合配置。
--
-- 说明：
--   - path 列支持逗号分隔的多条路径，因此用 REPLACE 而不是 CONCAT/SUBSTRING；
--   - sys_permission.path 只是权限管理界面展示与审计核对的元数据，运行期鉴权
--     走 perm_code，本脚本不改变任何实际授权结果；
--   - 排除 /internal/ 与 /overview，保证元数据与实际路由一致。
--
-- 幂等：
--   已经是 /<service>/v1/ 的行不再处理，重复执行不会叠加成 /v1/v1/。
-- =============================================================================

UPDATE sys_permission
SET path = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(path,
        '/erp/', '/erp/v1/'),
        '/mes/', '/mes/v1/'),
        '/oa/', '/oa/v1/'),
        '/lms/', '/lms/v1/'),
        '/mall/', '/mall/v1/'),
    update_time = CURRENT_TIMESTAMP
WHERE type = 'API'
  AND deleted = 0
  AND (path LIKE '/erp/%' OR path LIKE '%/erp/%'
    OR path LIKE '/mes/%' OR path LIKE '%/mes/%'
    OR path LIKE '/oa/%' OR path LIKE '%/oa/%'
    OR path LIKE '/lms/%' OR path LIKE '%/lms/%'
    OR path LIKE '/mall/%' OR path LIKE '%/mall/%')
  AND path NOT LIKE '%/internal/%'
  AND path NOT LIKE '%/overview%'
  AND path NOT LIKE '%/erp/v1/%'
  AND path NOT LIKE '%/mes/v1/%'
  AND path NOT LIKE '%/oa/v1/%'
  AND path NOT LIKE '%/lms/v1/%'
  AND path NOT LIKE '%/mall/v1/%';
