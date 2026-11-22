-- =============================================================================
-- 文件        : V45__doc_forum_mail_music_video_iot_api_path_versioning.sql
-- 说明        : 文档/论坛/邮箱/音乐/影视/IoT 对外接口版本化后，同步权限点路径
--
-- 背景：
--   六个模块的对外接口由 /<service>/** 统一为 /<service>/v1/**（与 /pay/v1/**、
--   /erp/v1/** 等保持一致；网关仍按前缀转发且不剥前缀）。
--   以下路径本轮保持不变：
--     - /<service>/internal/**：跨服务调用；
--     - /<service>/overview：SDK 概览端点，监控聚合配置引用；
--     - /music/stream/**、/video/stream/**：网关白名单放行的公开媒体流；
--     - /doc/ws：协同编辑 WebSocket 端点，由 WebSocketHandlerRegistry 注册。
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
SET path = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(path,
        '/doc/', '/doc/v1/'),
        '/forum/', '/forum/v1/'),
        '/mail/', '/mail/v1/'),
        '/music/', '/music/v1/'),
        '/video/', '/video/v1/'),
        '/iot/', '/iot/v1/'),
    update_time = CURRENT_TIMESTAMP
WHERE perm_type = 'API'
  AND deleted = 0
  AND (path LIKE '/doc/%' OR path LIKE '%/doc/%'
    OR path LIKE '/forum/%' OR path LIKE '%/forum/%'
    OR path LIKE '/mail/%' OR path LIKE '%/mail/%'
    OR path LIKE '/music/%' OR path LIKE '%/music/%'
    OR path LIKE '/video/%' OR path LIKE '%/video/%'
    OR path LIKE '/iot/%' OR path LIKE '%/iot/%')
  AND path NOT LIKE '%/internal/%'
  AND path NOT LIKE '%/overview%'
  AND path NOT LIKE '%/stream/%'
  AND path NOT LIKE '%/doc/ws'
  AND path NOT LIKE '%/doc/v1/%'
  AND path NOT LIKE '%/forum/v1/%'
  AND path NOT LIKE '%/mail/v1/%'
  AND path NOT LIKE '%/music/v1/%'
  AND path NOT LIKE '%/video/v1/%'
  AND path NOT LIKE '%/iot/v1/%';
