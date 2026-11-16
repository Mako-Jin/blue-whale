-- =============================================================================
-- 文件        : V34__gateway_whitelist_remote_complete.sql
-- 说明        : 网关白名单远程名单补全（P2-7 本地 white-list 精简）
--
-- 背景：V13 种子只覆盖了最初的 15 条本地 white-list；此后三方登录、OAuth2、
--       3D 世界频道等 9 条放行规则只加在网关 application.yml，未同步入库。
--       远程名单因此不是权威源——管理端停用/删除条目时，本地配置仍会放行。
--
-- 本脚本（幂等）：把上述 9 条按平台级（scope='PLATFORM'、tenant_id=0）补入，
--       与 V13 种子合并后覆盖网关此前的全部本地放行项。
--       网关本地 white-list 随后精简为登录闭环兜底（见 application.yml 注释）。
--
-- 判重键：path_pattern + http_method + scope + tenant_id（uk_gateway_whitelist）。
-- =============================================================================

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, tenant_id, source, remark, status, sort_no)
SELECT 16, '/social/v1/*/authorize', '*', 'PLATFORM', 0, 'MIGRATION',
       '三方登录授权起始（浏览器跳转链路，免 JWT）', 1, 16 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/social/v1/*/authorize' AND http_method = '*'
                    AND scope = 'PLATFORM' AND tenant_id = 0);

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, tenant_id, source, remark, status, sort_no)
SELECT 17, '/social/v1/*/callback', '*', 'PLATFORM', 0, 'MIGRATION',
       '三方登录渠道回调（外部 IdP 跳转，不可重试）', 1, 17 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/social/v1/*/callback' AND http_method = '*'
                    AND scope = 'PLATFORM' AND tenant_id = 0);

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, tenant_id, source, remark, status, sort_no)
SELECT 18, '/social/v1/login', '*', 'PLATFORM', 0, 'MIGRATION',
       '三方一次性登录码换令牌（短时效凭证交换）', 1, 18 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/social/v1/login' AND http_method = '*'
                    AND scope = 'PLATFORM' AND tenant_id = 0);

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, tenant_id, source, remark, status, sort_no)
SELECT 19, '/oauth2/token', '*', 'PLATFORM', 0, 'MIGRATION',
       'OAuth2 令牌端点（客户端凭据由 auth 服务内校验）', 1, 19 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/oauth2/token' AND http_method = '*'
                    AND scope = 'PLATFORM' AND tenant_id = 0);

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, tenant_id, source, remark, status, sort_no)
SELECT 20, '/oauth2/revoke', '*', 'PLATFORM', 0, 'MIGRATION',
       'OAuth2 撤销端点（协议端点，免 JWT）', 1, 20 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/oauth2/revoke' AND http_method = '*'
                    AND scope = 'PLATFORM' AND tenant_id = 0);

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, tenant_id, source, remark, status, sort_no)
SELECT 21, '/oauth2/introspect', '*', 'PLATFORM', 0, 'MIGRATION',
       'OAuth2 内省端点（协议端点，免 JWT）', 1, 21 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/oauth2/introspect' AND http_method = '*'
                    AND scope = 'PLATFORM' AND tenant_id = 0);

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, tenant_id, source, remark, status, sort_no)
SELECT 22, '/oauth2/jwks', '*', 'PLATFORM', 0, 'MIGRATION',
       'OAuth2 JWKS 公钥端点（协议端点，免 JWT）', 1, 22 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/oauth2/jwks' AND http_method = '*'
                    AND scope = 'PLATFORM' AND tenant_id = 0);

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, tenant_id, source, remark, status, sort_no)
SELECT 23, '/.well-known/**', '*', 'PLATFORM', 0, 'MIGRATION',
       'OAuth2 / OIDC 发现文档（协议端点，免 JWT）', 1, 23 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/.well-known/**' AND http_method = '*'
                    AND scope = 'PLATFORM' AND tenant_id = 0);

INSERT INTO sys_gateway_whitelist (id, path_pattern, http_method, scope, tenant_id, source, remark, status, sort_no)
SELECT 24, '/im/world', '*', 'PLATFORM', 0, 'MIGRATION',
       '3D 蓝鲸城世界频道 WS 升级（一次性 ticket，握手无法带 Authorization）', 1, 24 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_gateway_whitelist
                  WHERE path_pattern = '/im/world' AND http_method = '*'
                    AND scope = 'PLATFORM' AND tenant_id = 0);
