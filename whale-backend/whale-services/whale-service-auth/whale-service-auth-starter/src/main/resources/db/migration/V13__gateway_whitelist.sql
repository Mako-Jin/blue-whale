-- =============================================================================
-- 文件        : V13__gateway_whitelist.sql
-- 说明        : 网关免鉴权白名单入库（权限中心控制平面）
--
-- 背景：whale-gateway 的 JWT 白名单原硬编码在网关 application.yml 的
--       whale.gateway.white-list（15 条），调整需改配置并重启网关，且与权限中心
--       数据割裂。本脚本把白名单作为权限中心的数据资产落到 auth 库，由网关
--       GET /auth/v1/internal/gateway/whitelist 定时拉取（60s 刷新），
--       网关本地配置保留为兜底（远程失败/为空时仅本地生效）。
--
-- 本脚本（幂等）：
--   1. 建表 sys_gateway_whitelist；
--   2. 种子 15 条，与网关 application.yml 的 white-list 逐字一致；
--   3. 权限点 sys:gateway:whitelist:list/add/edit/delete（号段 2360–2363，
--      按 perm_code 判重），为后续管理端 CRUD 预留；本轮不建菜单、不授租户管理员。
--
-- 号段登记：2360–2363 = 网关白名单管理。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 网关白名单表
--    唯一键包含 scope，便于后续按租户扩展（当前仅 PLATFORM 参与下发）；
--    http_method='*' 表示不限制方法。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_gateway_whitelist (
    id              BIGINT       NOT NULL                COMMENT '主键ID',
    path_pattern    VARCHAR(255) NOT NULL                COMMENT '免鉴权路径（Ant 通配，如 /music/stream/**）',
    http_method     VARCHAR(16)  NOT NULL DEFAULT '*'    COMMENT 'HTTP 方法：* 表示不限',
    scope           VARCHAR(16)  NOT NULL DEFAULT 'PLATFORM' COMMENT '作用域：PLATFORM 平台',
    source          VARCHAR(16)  NOT NULL DEFAULT 'MIGRATION' COMMENT '来源：MIGRATION 迁移 / MANUAL 手工',
    remark          VARCHAR(255) DEFAULT NULL            COMMENT '备注（放行原因）',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号（小者优先）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gateway_whitelist (path_pattern, http_method, scope),
    KEY idx_gateway_whitelist_status (status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='网关免鉴权白名单表';

-- ---------------------------------------------------------------------
-- 2. 种子数据（判重键：path_pattern + http_method + scope）
-- ---------------------------------------------------------------------
INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 1, '/auth/v1/login', '*', 'PLATFORM', 'MIGRATION', '登录', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/auth/v1/login' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 2, '/auth/v1/refresh', '*', 'PLATFORM', 'MIGRATION', '刷新令牌', 1, 2 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/auth/v1/refresh' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 3, '/brand/v1/resolve', '*', 'PLATFORM', 'MIGRATION', '登录页品牌解析', 1, 3 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/brand/v1/resolve' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 4, '/actuator/**', '*', 'PLATFORM', 'MIGRATION', '健康检查与监控端点', 1, 4 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/actuator/**' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 5, '/v3/api-docs/**', '*', 'PLATFORM', 'MIGRATION', 'OpenAPI 文档', 1, 5 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/v3/api-docs/**' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 6, '/doc.html', '*', 'PLATFORM', 'MIGRATION', '接口文档首页', 1, 6 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/doc.html' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 7, '/webjars/**', '*', 'PLATFORM', 'MIGRATION', '文档静态资源', 1, 7 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/webjars/**' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 8, '/favicon.ico', '*', 'PLATFORM', 'MIGRATION', '站点图标', 1, 8 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/favicon.ico' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 9, '/audit/log/frontend/batch', '*', 'PLATFORM', 'MIGRATION',
       '前端日志上报（未登录期也需上报，REQ-SEC-006）', 1, 9 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/audit/log/frontend/batch' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 10, '/audit/log/behavior/batch', '*', 'PLATFORM', 'MIGRATION',
       '行为日志上报（未登录期也需上报，REQ-SEC-006）', 1, 10 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/audit/log/behavior/batch' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 11, '/im/ws', '*', 'PLATFORM', 'MIGRATION',
       'WebIM WS 升级（浏览器 WS 无法携带 Authorization，改用一次性 ticket）', 1, 11 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/im/ws' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 12, '/doc/ws', '*', 'PLATFORM', 'MIGRATION',
       '文档实时协作 WS 升级（一次性 ticket）', 1, 12 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/doc/ws' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 13, '/music/stream/**', '*', 'PLATFORM', 'MIGRATION',
       '音频流（<audio> 标签无法携带 Authorization，改用播放票据）', 1, 13 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/music/stream/**' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 14, '/video/stream/**', '*', 'PLATFORM', 'MIGRATION',
       '视频流（<video> 标签无法携带 Authorization，改用播放票据）', 1, 14 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/video/stream/**' AND http_method = '*' AND scope = 'PLATFORM');

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, source, remark, status, sort_no)
SELECT 15, '/contract/sign/callback/**', '*', 'PLATFORM', 'MIGRATION',
       '第三方电子签章回调（依赖渠道 HMAC/RSA 验签 + 时间戳窗口 + 幂等）', 1, 15 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/contract/sign/callback/**' AND http_method = '*' AND scope = 'PLATFORM');

-- ---------------------------------------------------------------------
-- 3. 权限点（判重键：perm_code；为后续管理端 CRUD 预留，本轮不建菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2360, 0, 'sys:gateway:whitelist:list', '查询网关白名单', 'API',
       '/auth/v1/gateway/whitelist,/auth/v1/gateway/whitelist/*', 'GET', 2360, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:gateway:whitelist:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2361, 0, 'sys:gateway:whitelist:add', '新增网关白名单', 'API',
       '/auth/v1/gateway/whitelist', 'POST', 2361, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:gateway:whitelist:add');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2362, 0, 'sys:gateway:whitelist:edit', '编辑网关白名单', 'API',
       '/auth/v1/gateway/whitelist/*', 'PUT', 2362, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:gateway:whitelist:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2363, 0, 'sys:gateway:whitelist:delete', '删除网关白名单', 'API',
       '/auth/v1/gateway/whitelist/*', 'DELETE', 2363, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:gateway:whitelist:delete');
