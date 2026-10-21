# Wave 5.28 任务拆分（动态表单元数据模型 · 并行执行）

> 依赖与并行：[spec.md](spec.md) 冻结契约后，**B（类型/编译）与 A（表结构）可先行**；C（元数据服务）依赖 A+B；D（迁移）依赖 A+C；E（发布/提交改造）依赖 B+C；F（门禁）末尾串行。轨内可并行小任务。
> **状态说明（2026-10-21 收尾审计）**：本文件为任务分解原稿，全部任务已随 Wave 5.28 落地，勾选已回填；权威结论以 [roadmap](../roadmap.md) §2.5 为准。

## Wave 5.28：whale-form 动态表单元数据模型（三层分离）

**轨 A：表结构（F2）**
- [x] A-a `entity/{FormTableDefinition,FormColumnMeta,FormFieldConfig,FormFieldOption}.java`
- [x] A-b `mapper/{FormTableDefinitionMapper,FormColumnMetaMapper,FormFieldConfigMapper,FormFieldOptionMapper}.java`
- [x] A-c `entity/{FormDefinition,FormTableRegistry,FormData}.java` 兼容列（tableDefinitionId / tableCode / tableVersion / formDefinitionId）
- [x] A-d `whale-form/src/main/resources/db/init.sql`：4 新表 + 3 表兼容列/键

**轨 B：类型与编译（F3）**
- [x] B-a `support/FormDataType.java`（data_type 白名单 + MySQL 列类型映射 + 默认 widget）
- [x] B-b `support/FormWidgetType.java`（widget_type 白名单校验）
- [x] B-c `support/FormMetadataCompiler.java`（列元数据 + 字段配置 + 选项 → `schema_json` 快照）

**轨 C：元数据服务与接口（F4）**
- [x] C-a `dto/{FormTableDefinitionSaveRequest,FormColumnMetaDTO,FormFieldConfigDTO,FormFieldOptionDTO}.java`
- [x] C-b `vo/{FormTableDefinitionVO,FormColumnMetaVO,FormFieldConfigVO,FormFieldOptionVO}.java`
- [x] C-c `service/FormTableDefinitionService(+Impl)`：表定义（含列）聚合 CRUD
- [x] C-d `controller/FormTableDefinitionController`：`/form/table-definition` CRUD + publish + tables
- [x] C-e `FormDefinitionService(+Impl)`：保存时持久化字段配置/选项 + 编译快照；详情返回 fields
- [x] C-f 权限种子 `sys:form:table:add/edit/delete/list/publish`（whale-auth db/init.sql，id 53–57）

**轨 D：迁移（F5）**
- [x] D-a `service/FormMetadataMigrationService(+Impl)`：`schema_json.fields[]` → 表定义 + 列 + 字段配置 + 选项（幂等）+ 回填 registry/form_data

**轨 E：发布/提交改造（F6）**
- [x] E-a `FormPublishService(+Impl)`：`publishTableDefinition`（列元数据建物理表 + 登记）+ 视图发布编译快照 + 确保物理表已发布（幂等）
- [x] E-b `FormDataService(+Impl)`：按表单视图 `field_config`（show_in_form=1）选列校验 + 表维度路由 + 主表新列落库
- [x] E-c `controller/FormController`：表定义发布/列表接线 + 定义详情带字段

**轨 F：门禁与收尾（F7–F9）**
- [x] F-a 单测：`FormMetadataCompilerTest` / `FormTableDefinitionServiceImplTest` / `FormMetadataMigrationServiceImplTest` + 既有用例回归
- [x] F-b 模块自检 `mvn -pl backend/whale-services/whale-form -am test checkstyle:check`（0 违规）
- [x] F-c 全量 `mvn clean install -T 1C` BUILD SUCCESS
- [x] F-d 增量 DDL/迁移落库 + 网关 `/form` 表定义→双表单视图→提交→详情→负向冒烟取证
- [x] F-e `docs/reviews/2026-10-19-wave5.28-form-metadata-model.md` + devlog + roadmap/task-breakdown 勾选 + commit & push origin
