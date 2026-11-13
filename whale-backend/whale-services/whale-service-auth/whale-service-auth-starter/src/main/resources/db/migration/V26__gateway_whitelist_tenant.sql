-- =============================================================================
-- 文件        : V26__gateway_whitelist_tenant.sql
-- 说明        : 网关白名单支持租户维度（GW-08 收尾）
--
-- 背景：V13 建表时以 scope='PLATFORM' 预留作用域设计。本轮打通租户维度下发：
--       scope='TENANT' 的条目绑定 tenant_id，网关对携带对应租户 accessToken 的请求
--       按租户白名单放行；scope='PLATFORM'（tenant_id=0）语义不变。
--
-- 契约：
--   tenant_id = 0 表示平台级（与 scope='PLATFORM' 一致，0 便于唯一键判重）；
--   scope='TENANT' 时 tenant_id 必须 > 0（领域层不变量校验）。
-- =============================================================================

-- 1. 增加租户维度列（0 = 平台级）
ALTER TABLE sys_gateway_whitelist
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID：0 平台级 / >0 绑定租户' AFTER scope;

-- 2. 唯一键扩展到租户维度：同一路径+方法+作用域可在不同租户各自一条
ALTER TABLE sys_gateway_whitelist
    DROP INDEX uk_gateway_whitelist,
    ADD UNIQUE KEY uk_gateway_whitelist (path_pattern, http_method, scope, tenant_id);
