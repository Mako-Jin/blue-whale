-- =============================================================================
-- 文件        : V27__gateway_route_override.sql
-- 说明        : 网关路由业务属性覆盖层（GW-10 Phase 2）
--
-- 背景：路由定义（id/uri/predicates/filters/order）权威源为 Nacos，冷备为网关本地
--       YAML（见 docs/design/gateway-route-source.md D1）。DB 不存路由定义，只存
--       业务属性覆盖：停用某条路由（全站 404）与生效时间窗口，供运营后台不经
--       Nacos 发布流程即时启停路由。
--
-- 契约：
--   route_id 必须与 Nacos / 兜底路由中的 id 逐字一致；不存在的 route_id 为孤儿条目，
--   网关忽略并计指标 gateway.routes.override{result="orphan"}，不阻断路由刷新。
--   停用等于该路由全站 404，管理端操作需二次确认并留审计。
--   网关经 GET /auth/v1/internal/gateway/route-overrides（60s 快照）读取，
--   不引入 JDBC；auth 不可用时保留上一份覆盖快照。
--
-- 号段登记：2384–2387 = 网关路由覆盖管理。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 网关路由业务属性覆盖表（主键即路由 id，一条路由至多一条覆盖）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_gateway_route_override (
    route_id       VARCHAR(128) NOT NULL                COMMENT '路由ID（与 Nacos/兜底路由 id 逐字一致）',
    enabled        TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 / 0 停用（停用=该路由全站 404）',
    effective_from DATETIME     DEFAULT NULL            COMMENT '生效起始时间；NULL 立即生效',
    effective_to   DATETIME     DEFAULT NULL            COMMENT '生效截止时间；NULL 长期生效',
    version        BIGINT       NOT NULL DEFAULT 1      COMMENT '版本号：每次更新 +1（供快照变更识别）',
    remark         VARCHAR(255) DEFAULT NULL            COMMENT '备注（启停原因）',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (route_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='网关路由业务属性覆盖表（GW-10 Phase 2）';

-- ---------------------------------------------------------------------
-- 2. 权限点（判重键：perm_code；管理端 CRUD，本轮不建菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2384, 0, 'sys:gateway:route-override:list', '查询网关路由覆盖', 'API',
       '/auth/v1/gateway/route-overrides', 'GET', 2384, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:gateway:route-override:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2385, 0, 'sys:gateway:route-override:add', '新增网关路由覆盖', 'API',
       '/auth/v1/gateway/route-overrides', 'POST', 2385, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:gateway:route-override:add');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2386, 0, 'sys:gateway:route-override:edit', '编辑网关路由覆盖', 'API',
       '/auth/v1/gateway/route-overrides/*', 'PUT', 2386, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:gateway:route-override:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2387, 0, 'sys:gateway:route-override:delete', '删除网关路由覆盖', 'API',
       '/auth/v1/gateway/route-overrides/*', 'DELETE', 2387, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:gateway:route-override:delete');
