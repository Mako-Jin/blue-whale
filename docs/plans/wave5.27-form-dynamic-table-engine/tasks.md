# Wave 5.27 任务拆分（动态表引擎 · 并行执行）

> 依赖与并行：[spec.md](spec.md) 冻结契约后，A（表/登记）先行；B（引擎内核）与 C（发布接线）依赖 A 的实体与 DDL 契约；D（数据改造）依赖 B+C。轨内可并行小任务，末尾串行全量回归。
> **状态说明（2026-10-21 收尾审计）**：本文件为任务分解原稿，全部任务已随 Wave 5.27 落地，勾选已回填；权威结论以 [roadmap](../roadmap.md) §2.5 为准。

## Wave 5.27：whale-form 动态表引擎（主从分离 · 版本化物理表）

**轨 A：表与登记（D2）**
- [x] A-a `entity/FormTableRegistry.java` + `mapper/FormTableRegistryMapper.java`
- [x] A-b `whale-form/src/main/resources/db/init.sql`：`form_data` 加 `tenant_id` + `idx_tenant_id`；新增 `form_table_registry`
- [x] A-c `WebHeaders`：+`TENANT_ID` 常量 + `tenantId(request)`

**轨 B：引擎内核（D3）**
- [x] B-a `support/FormField.java`（字段定义值对象）
- [x] B-b `support/FormSchemaParser.java`（解析 + key/type 白名单 + 保留列校验）
- [x] B-c `support/FormTableDdlGenerator.java`（表名校验 + 类型映射 + DDL 生成）
- [x] B-d `support/FormDataEngine.java`（接口）+ `support/impl/JdbcFormDataEngine.java`（JdbcTemplate 参数化：建表/插入/查字段/逻辑删除 + 表名二次正则）
- [x] B-e `support/TenantResolver.java`（`X-Tenant-Id` → tenant_id，缺省 1，非法 400）

**轨 C：发布与接线（D4）**
- [x] C-a `service/FormPublishService(+Impl)`：解析 schema → 建物理表 → 登记 registry（同 code+version 重复 → 400）
- [x] C-b `controller/FormController`：+`POST /form/definition/{id}/publish`（`sys:form:publish`）、`GET /form/definition/{id}/tables`（`sys:form:list`）
- [x] C-c `vo/FormTableRegistryVO.java`
- [x] C-d 权限种子 `sys:form:publish`（whale-auth db/init.sql，id 52）

**轨 D：数据改造（D5）**
- [x] D-a `entity/FormData` +`tenantId`；`vo/FormDataVO` +`tenantId`/`fields`
- [x] D-b `FormDataService(+Impl)`：`submit` 校验已发布 + 解析/校验 `dataJson` + 主表/物理表双写；`getDetail` 组装 `fields`；`delete` 双删；`page` 加 tenant 过滤
- [x] D-c `FormDataController`：解析并传 `tenantId`

**轨 E：门禁与收尾（D6–D9）**
- [x] E-a 单测：`FormSchemaParserTest` / `FormTableDdlGeneratorTest`（解析与 DDL 用例）+ `FormPublishServiceImplTest`（发布/重复/非法编码）+ 改造 `FormDataServiceImplTest`（已发布校验/双写/未知字段/必填/跨租户）
- [x] E-b 模块自检 `mvn -pl backend/whale-services/whale-form -am test checkstyle:check`（0 违规）
- [x] E-c 全量 `mvn clean install -T 1C` BUILD SUCCESS
- [x] E-d 建库/种子 + 网关 `/form` 发布→提交→详情→列表→删除冒烟取证 + 负向
- [x] E-e `docs/reviews/2026-10-19-wave5.27-form-dynamic-table-engine.md` + devlog + roadmap/task-breakdown 勾选 + commit & push origin
