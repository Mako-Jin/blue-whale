# P3-9c PDM 产品业务闭环（后端填充 + 前端联调）—— 检查报告

- 日期：2026-10-26
- 来源：待办队列 P3-9（erp/mes/pdm 业务应用后端填充 + 前端联调），ERP → MES → **PDM** 依次执行
- 结论：**已完成**

## 一、范围

`whale-pdm` 原为启动类空壳（仅 `WhalePdmApplication` + 无数据库 `application.yml`），前端 `whale-pdm` 为占位 mock 数据。本次沿用 P3-9a/P3-9b 同范式，补齐单实体（产品）CRUD 闭环。

## 二、变更点

### 后端（`backend/whale-apps/whale-pdm`）

- `pom.xml`：补 `micrometer-registry-prometheus`、`mybatis-plus-spring-boot4-starter`、`mysql-connector-j`、`whale-spring-boot-starter`。
- `application.yml`：补数据源（`bluewhale_pdm`）、`whale.permission.header.enabled=true`、MyBatis-Plus 逻辑删除与驼峰映射、actuator prometheus。
- 新增 `db/init.sql`：`pdm_product` 表（`tenant_id` 行级隔离 + `uk_tenant_code` 唯一 + 逻辑删除）+ 2 条种子 + 标准 `api_log` DDL。
- 新增 `entity/PdmProduct`、`mapper/PdmProductMapper`、`dto/PdmProductQueryRequest`、`dto/PdmProductSaveRequest`、`vo/PdmProductVO`、`service/PdmProductService`、`service/impl/PdmProductServiceImpl`、`controller/PdmProductController`。
- 接口：`GET /pdm/product/page`、`GET /pdm/product/{id}`、`POST /pdm/product`、`PUT /pdm/product/{id}`、`DELETE /pdm/product/{id}`；写操作全部 `@AuditLog`。

### 权限（`bluewhale_auth`）

- 新增幂等迁移 `migration-5.60-pdm.sql`：权限点 211–214（`sys:pdm:product:list/add/edit/delete`）+ 菜单 27（产品管理 `/pdm`）。
- 编号约定：203–206 归 ERP、207–210 归 MES、211–214 归 PDM；菜单 25/26 归 ERP/MES，PDM 用 27。

### 前端（`frontend/platform-apps/whale-pdm`）

- `api/pdm.ts`：移除 mock，改接 `@bluewhale/http` 经网关调 `/pdm/product/**`（列表取 `PageResult.records`；无 id 走 POST、有 id 走 PUT）。`List.tsx` / `Form.tsx` 无需改动。

## 三、验证

1. 构建：`mvn -o -pl whale-apps/whale-pdm -am package` **BUILD SUCCESS**。
2. 建库：`bluewhale_pdm` 建库 + init.sql 执行成功，表 `pdm_product` / `api_log` 就位，种子 2 行。
3. 迁移：`migration-5.60-pdm.sql` 执行后权限 211–214 与菜单 27 查询命中。
4. 服务：whale-pdm 9003 启动，`/actuator/health` **UP**，Nacos 注册 `whale-pdm` 成功。
5. 经网关 8080 冒烟 5/5：
   - `GET /pdm/product/page` code=0，total=2（Nacos 发现 + `lb://whale-pdm` 路由正常）；
   - `POST /pdm/product` code=0，返回 id=`2114569279898517505`；
   - `PUT /pdm/product/{id}` code=0，name/status 更新生效；
   - `GET /pdm/product/{id}` code=0，回显与更新一致；
   - `DELETE /pdm/product/{id}` code=0。
6. 日志：`bluewhale_pdm.api_log` 落 5 行，`biz_status=1`。
7. 前端：`tsc --noEmit` 通过（exit 0），`vite build` 成功（5.91s）。

## 四、风险与观察

- 权限迁移沿用既有约定不写 `sys_role_permission`，业务角色需在「角色管理」勾选 211–214；`super_admin` 隐式覆盖，本次冒烟即以此验证。
- PDM 目前仅产品单实体，BOM/图纸/版本管理等域未纳入本次范围（保持「最小可加性」）。
- ERP/MES/PDM 三服务同构（同 starter、同分页/唯一校验/审计范式），后续若出现第三个以上同构模块再考虑抽取公共基类，当前不做过度抽象。
