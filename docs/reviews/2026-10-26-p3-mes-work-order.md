# P3-9b MES 生产工单业务闭环（后端填充 + 前端联调）—— 检查报告

- 日期：2026-10-26
- 来源：待办队列 P3-9（erp/mes/pdm 业务应用后端填充 + 前端联调），ERP → **MES** → PDM 依次执行
- 结论：**已完成**

## 一、范围

`whale-mes` 原为启动类空壳（仅 `WhaleMesApplication` + 无数据库 `application.yml`），前端 `whale-mes` 为占位 mock 数据。本次沿用 P3-9a ERP 同范式，补齐单实体（生产工单）CRUD 闭环。

## 二、变更点

### 后端（`whale-backend/whale-apps/whale-mes`）

- `pom.xml`：补 `micrometer-registry-prometheus`、`mybatis-plus-spring-boot4-starter`、`mysql-connector-j`、`whale-spring-boot-starter`。
- `application.yml`：补数据源（`bluewhale_mes`）、`whale.permission.header.enabled=true`、MyBatis-Plus 逻辑删除与驼峰映射、actuator prometheus。
- 新增 `db/init.sql`：`mes_work_order` 表（`tenant_id` 行级隔离 + `uk_tenant_code` 唯一 + 逻辑删除）+ 2 条种子 + 标准 `api_log` DDL。
- 新增 `entity/MesWorkOrder`、`mapper/MesWorkOrderMapper`、`dto/MesWorkOrderQueryRequest`、`dto/MesWorkOrderSaveRequest`、`vo/MesWorkOrderVO`、`service/MesWorkOrderService`、`service/impl/MesWorkOrderServiceImpl`、`controller/MesWorkOrderController`。
- 接口：`GET /mes/work-order/page`、`GET /mes/work-order/{id}`、`POST /mes/work-order`、`PUT /mes/work-order/{id}`、`DELETE /mes/work-order/{id}`；写操作全部 `@AuditLog`。

### 权限（`bluewhale_auth`）

- 新增幂等迁移 `migration-5.59-mes.sql`：权限点 207–210（`sys:mes:workorder:list/add/edit/delete`）+ 菜单 26（工单管理 `/mes`）。
- 编号约定：203–206 归 ERP；207–210 归 MES；PDM 从 211 起编；菜单 25 归 ERP，MES 用 26。

### 前端（`whale-frontend/platform-apps/whale-mes`）

- `api/mes.ts`：移除 mock，改接 `@bluewhale/http` 经网关调 `/mes/work-order/**`（列表取 `PageResult.records`；无 id 走 POST、有 id 走 PUT）。`List.tsx` / `Form.tsx` 无需改动。

## 三、验证

1. 构建：`mvn -o -pl whale-apps/whale-mes -am package` **BUILD SUCCESS**（9 源文件编译，repackage 完成）。
2. 建库：`bluewhale_mes` 建库 + init.sql 执行成功，表 `mes_work_order` / `api_log` 就位，种子 2 行。
3. 迁移：`migration-5.59-mes.sql` 执行后权限 207–210 与菜单 26 查询命中。
4. 服务：whale-mes 9002 启动，`/actuator/health` **UP**，Nacos 注册 `whale-mes` 成功。
5. 经网关 8080 冒烟 5/5：
   - `GET /mes/work-order/page` code=0，total=2（Nacos 发现 + `lb://whale-mes` 路由正常）；
   - `POST /mes/work-order` code=0，返回 id=`2114568041110933505`；
   - `PUT /mes/work-order/{id}` code=0，name/status 更新生效；
   - `GET /mes/work-order/{id}` code=0，回显与更新一致；
   - `DELETE /mes/work-order/{id}` code=0。
6. 日志：`bluewhale_mes.api_log` 落 5 行，`biz_status=1`、`response_code=200`。
7. 前端：`tsc --noEmit` 通过（exit 0），`vite build` 成功（6.00s）。

## 四、风险与观察

- 权限迁移沿用既有约定不写 `sys_role_permission`，业务角色需在「角色管理」勾选 207–210；`super_admin` 隐式覆盖，本次冒烟即以此验证。
- MES 目前仅生产工单单实体，工单报工/工序/物料领用等多实体域未纳入本次范围（保持「最小可加性」）。
- 服务注册地址为 `192.168.0.214:9002`，与网关同机部署时 `lb://` 路由正常；跨机部署需确保 Nacos 注册 IP 可达。
