-- =============================================================================
-- 文件        : V10__rbac_role_baseline.sql
-- 说明        : RBAC 数据治理 —— 补齐「代码依赖但库里从未存在」的角色与权限点
--
-- 事故背景（2026-11-11 实测 bluewhale_auth）：
--   1) 库里 sys_role 共 55 个角色，role_code='tenant_admin' 的 **0 条**。
--      但 migration-5.65 / 5.67 / 5.68 / 5.70 / 5.71 / 5.73 / 5.74 / 5.75 / 5.76 /
--      5.77 / 5.78 … 以及 V4 / V5 / V6 / V8 的授权段全部写成
--      `WHERE r.role_code IN ('tenant_admin')`。角色不存在 → JOIN 结果为空 →
--      整段授权静默不生效（不报错、不告警），这些脚本等于从未执行。
--   2) 后果不只是「少授权」：whale-mall / whale-lms / whale-party / whale-crm /
--      whale-pdm 的 Actor 类把 tenant_admin 当作「全量数据范围」判定依据
--      （MallActor.MANAGE_ROLES、LmsActor.MANAGE_ROLES、PartyActor#isTenantAdmin …），
--      角色不存在 → 除硬旁路的 super_admin 外，任何人都拿不到全量范围。
--   3) **mall_admin / mall_operator 同样不存在**。whale-mall 的发货、改价、售后审核、
--      经营报表全部走 `actor.isOperator()`；该判定只认 tenant_admin / mall_admin /
--      mall_operator 三个角色码，而三者全缺 → 商城运营动作对非超管永久不可用。
--   4) 另查出 8 个权限码「代码引用但库里没有」：
--        sys:mall:activity:list/manage、sys:mall:member:list/manage（5.100 未执行）
--        sys:iot:product/device/rule（6.02 未执行）
--        sys:twin:scene（6.03 未执行）
--      表现同样是「编译/启动/单测全绿，真人点按钮才 403」。
--
-- 本脚本（幂等，判重键一律不含固定 id）：
--   1. 补 8 个缺失权限点（判重键 = perm_code，主键撞号不得让整条语句静默跳过）
--   2. 建角色 tenant_admin / mall_admin / mall_operator（判重键 = tenant_id + role_code）
--   3. 授权：tenant_admin = 全部启用权限 − 平台专属码（与 TenantProvisioningAppService
--      开通新租户时的口径完全一致，避免「平台租户一套、业务租户另一套」）
--      mall_admin = 全部 sys:mall:*；mall_operator = 运营子集（不含渠道结算与类目维护）
--   4. 复制角色与授权到已有业务租户（当前仅有平台租户，逻辑为未来租户保留）
--
-- 号段登记：
--   权限编号 : 2225–2228 = mall 活动/会员（与 migration-5.100-mall-combo.sql 同号同码，
--              幂等副本，不算撞号）；2310–2313 = iot / twin（与 6.02 / 6.03 同号同码）
--   角色编号 : 26001 = tenant_admin；26002 = mall_admin；26003 = mall_operator
--              （26000–26099 段此前完全空闲；其余角色在 1 / 14xxx / 16xxx / 17xxx /
--               20xxx / 21xxx / 25xxx 段）
--
-- 校验（执行后应满足）：
--   -- a) 角色存在
--   SELECT role_code, COUNT(*) FROM sys_role WHERE role_code IN
--     ('tenant_admin','mall_admin','mall_operator') GROUP BY role_code;  -- 期望各 1
--   -- b) 孤儿权限只剩 6 个平台运营专属码（只有超管可达，属预期）
--   SELECT p.perm_code FROM sys_permission p
--   WHERE p.deleted=0 AND p.status=1
--     AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
--                     JOIN sys_role r ON r.id=rp.role_id AND r.deleted=0 AND r.status=1
--                     WHERE rp.permission_id=p.id);
--   期望：sys:tenant:list / add / edit / status / renew、sys:user:tenant
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 缺失权限点（判重键：perm_code）
--    注意：id 列只是「首次落库时的建议主键」，判重绝不看 id ——
--    一旦 id 被别的模块占用，`WHERE NOT EXISTS (id = ? OR perm_code = ?)`
--    会让整条 INSERT 静默跳过，正是 6.01 助手权限丢失的成因。
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2225, 0, 'sys:mall:activity:list', '商城活动列表', 'API',
       '/mall/activity/page,/mall/activity/*', 'GET', 2225, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:activity:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2226, 0, 'sys:mall:activity:manage', '商城活动维护', 'API',
       '/mall/activity,/mall/activity/*/status', 'POST', 2226, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:activity:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2227, 0, 'sys:mall:member:list', '商城会员列表', 'API',
       '/mall/member/page,/mall/member/*', 'GET', 2227, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:member:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2228, 0, 'sys:mall:member:manage', '商城会员维护', 'API',
       '/mall/member,/mall/member/*', 'POST', 2228, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:member:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2310, 0, 'sys:iot:product', 'IoT 产品与物模型', 'API', '/iot/products/**', NULL, 110, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:iot:product');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2311, 0, 'sys:iot:device', 'IoT 设备管理', 'API', '/iot/devices/**', NULL, 111, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:iot:device');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2312, 0, 'sys:iot:rule', 'IoT 规则引擎', 'API', '/iot/rules/**', NULL, 112, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:iot:rule');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2313, 0, 'sys:twin:scene', '数字孪生场景管理', 'API', '/twin/scenes/**', NULL, 113, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:twin:scene');

-- ---------------------------------------------------------------------
-- 2. 平台租户（tenant_id = 1）角色模板
--    判重键 = (tenant_id, role_code)，与 uk_tenant_role_code 一致；
--    id 不参与判重，撞号时不会连带跳过合法角色。
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 26001, 1, 'tenant_admin', '租户管理员',
       '租户内全量管理：用户、角色、菜单、部门、业务模块（平台运营动作除外）', 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 1 AND role_code = 'tenant_admin');

INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 26002, 1, 'mall_admin', '商城管理员',
       '商城全量：类目/商品/库存/渠道/订单/售后/营销/会员/履约，含全量数据范围', 260, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 1 AND role_code = 'mall_admin');

INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 26003, 1, 'mall_operator', '商城运营',
       '商城运营：订单处理、发货、改价上下架、售后审核、经营报表（不含渠道结算与类目维护）', 261, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 1 AND role_code = 'mall_operator');

-- ---------------------------------------------------------------------
-- 3. 授权
--    3.1 tenant_admin = 全部启用权限 − 平台专属码
--        平台专属码与 TenantProvisioningAppService.PLATFORM_ONLY_PERM_CODES 保持一一对应，
--        改一处必须改两处（新增权限点时也只需授一次：本段是「全量减排除」，不枚举白名单）。
--        结果：158 个孤儿权限中 152 个归位，仅剩 6 个平台运营码只归超管，符合设计。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin'
      AND p.perm_code NOT IN ('sys:tenant:list', 'sys:tenant:add', 'sys:tenant:edit',
                              'sys:tenant:status', 'sys:tenant:renew', 'sys:user:tenant')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- 3.2 mall_admin = 全部 sys:mall:*
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 AND p.perm_code LIKE 'sys:mall:%'
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mall_admin'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- 3.3 mall_operator = 履约与查询侧子集
--     刻意不含：category:manage / channel:manage / coupon:manage / freight:manage /
--     member:manage / activity:manage / product:manage —— 这些是「配置类」动作，
--     与 MallActor 注释「运营不应能改渠道结算配置」一致。
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
         AND p.perm_code IN (
             'sys:mall:browse',
             'sys:mall:category:list',
             'sys:mall:product:list', 'sys:mall:product:publish',
             'sys:mall:stock:list', 'sys:mall:stock:manage',
             'sys:mall:channel:list',
             'sys:mall:order:list', 'sys:mall:order:manage',
             'sys:mall:order:cancel', 'sys:mall:order:refund',
             'sys:mall:aftersale:list', 'sys:mall:aftersale:audit',
             'sys:mall:coupon:list', 'sys:mall:coupon:claim',
             'sys:mall:freight:list',
             'sys:mall:shipment:list', 'sys:mall:shipment:manage',
             'sys:mall:activity:list', 'sys:mall:member:list'
         )
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mall_operator'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- ---------------------------------------------------------------------
-- 4. 复制到已有业务租户
--    角色管理接口按 TenantContext 过滤 tenant_id，平台租户的模板角色在业务租户
--    不可见也不能用于授权计算，必须逐租户复制一份。
--    tenant_admin 由 TenantProvisioningAppService 在开通时直接创建，故此处只复制
--    mall_admin / mall_operator（与 BUSINESS_ROLE_TEMPLATE_CODES 对齐）。
--    主键 = MD5(tenant_id:role_code) 前 15 位，重复执行 ID 稳定。
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name,
           x.description AS description, x.sort_no AS sort_no, 1 AS status
    FROM sys_tenant t
    CROSS JOIN (
        SELECT 'mall_admin' AS role_code, '商城管理员' AS role_name,
               '商城全量：类目/商品/库存/渠道/订单/售后/营销/会员/履约，含全量数据范围' AS description, 260 AS sort_no
        UNION ALL
        SELECT 'mall_operator', '商城运营',
               '商城运营：订单处理、发货、改价上下架、售后审核、经营报表（不含渠道结算与类目维护）', 261
    ) x
    WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role tgt
    WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code)
);

-- 4.2 业务租户的 mall 角色授权（按 role_code 匹配，覆盖所有租户内同名角色）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND ( (r.role_code = 'mall_admin' AND p.perm_code LIKE 'sys:mall:%')
         OR (r.role_code = 'mall_operator' AND p.perm_code IN (
                 'sys:mall:browse',
                 'sys:mall:category:list',
                 'sys:mall:product:list', 'sys:mall:product:publish',
                 'sys:mall:stock:list', 'sys:mall:stock:manage',
                 'sys:mall:channel:list',
                 'sys:mall:order:list', 'sys:mall:order:manage',
                 'sys:mall:order:cancel', 'sys:mall:order:refund',
                 'sys:mall:aftersale:list', 'sys:mall:aftersale:audit',
                 'sys:mall:coupon:list', 'sys:mall:coupon:claim',
                 'sys:mall:freight:list',
                 'sys:mall:shipment:list', 'sys:mall:shipment:manage',
                 'sys:mall:activity:list', 'sys:mall:member:list'
             )) )
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
