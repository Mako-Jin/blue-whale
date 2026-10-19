# Wave 5.26 规格（whale-form 全闭环 · 表单数据 `form_data` · 冻结契约）

> 用户指令（2026-10-19）：「开始搭建表单工作流服务」→ 经 AskUserQuestion 确认本轮口径：**whale-form 全闭环（表单数据）** —— 在 Wave 5.23「表单定义」之上补 `form_data` 表单数据表与提交/查询/删除接口，形成「定义 → 填写 → 数据」最小闭环；对齐 Wave 5.25 的 whale-workflow 全闭环模式。
> 执行原则：**先落文档（REQ-DOC-002）→ 冻结契约（本文 §三）→ 轨内并行 → 模块级自检 → 收尾串行全量回归（REQ-QA-002）**。
> 前置：Wave 5.23 已交付 `whale-form`（8084 / `/form/**` / `bluewhale_form` / `form_definition` 表 / `sys:form:add/edit/delete/list` 种子 id 36–39 / `FormController`）。
> 关联台账：[roadmap.md](../roadmap.md)、[task-breakdown.md](../task-breakdown.md)、[service-catalog.md](../../service-catalog.md)。

---

## 一、范围与非目标

| 轨 | 范围 | 落点 |
|---|---|---|
| **A 表单数据** | `form_data` 表 + 提交（校验定义存在 + 数据 JSON ≤64KB）/ 详情 / 分页 / 逻辑删除 | `whale-form` |
| **B 接线与门禁** | 权限种子 `sys:form:data:*`（id 49–51）+ 单测 + 检查报告 | `whale-form`、`whale-auth` db |

**非目标（明确不做）**
- 表单数据审批流、表单数据版本快照、草稿/暂存、字段级校验与唯一性约束。
- 表单与工作流联动（发起流程实例时绑定表单 / 按 `bizKey` 关联实例）——另起波次。
- 前端表单设计器与数据填报页、导入导出、附件上传。

---

## 二、现状事实（研究结论，勿重复调研）

- **Wave 5.23 已就绪**：`form_definition` 表（`schema_json` MEDIUMTEXT ≤64KB、`idx_code`）、`FormDefinition`/`FormDefinitionMapper`、`FormDefinitionService(+Impl)`、`FormController`（`/form/definition` 5 接口）、`FormDefinitionServiceImplTest`（8 用例）；库 `bluewhale_form` 已建；网关 `Path=/form/**` → `lb://whale-form` 已配；权限种子至 **43**（workflow 5.25 补至 48）。
- **模板**：whale-form 现行 `Xxx`(继承 `BaseEntity`) / `XxxMapper`(仅 `BaseMapper`) / `XxxService(+Impl)` / `XxxController`(`@RequirePermission`)；`MyBatis-Plus` 逻辑删除 `deleted`；`PageQuery`/`PageResult`；`BizException(ErrorCode.PARAM_ERROR/NOT_FOUND)`；`WebHeaders.username(request)` 读取提交人。

---

## 三、冻结契约（实现前不得更改）

### 3.1 表结构（库 `bluewhale_form`，`utf8mb4_general_ci`，InnoDB）

**`form_data`**（表单数据）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 雪花 ID |
| definition_id | BIGINT NOT NULL | 表单定义 ID |
| definition_code | VARCHAR(64) NOT NULL | 提交时的定义编码（冗余） |
| definition_version | INT NOT NULL | 提交时的定义版本（冗余） |
| biz_key | VARCHAR(64) NULL | 业务键（可选） |
| data_json | MEDIUMTEXT NULL | 表单数据 JSON（最长 64KB） |
| submitter | VARCHAR(64) NULL | 提交人账号 |
| status | TINYINT NOT NULL DEFAULT 1 | 1 有效 / 0 作废 |
| create_time / update_time / deleted | | 公共字段 |
| KEY idx_definition_id (definition_id)、KEY idx_biz_key (biz_key) | | |

### 3.2 接口（服务前缀 `/form`；网关 `Path=/form/**` 只转发不剥离）

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| POST | `/form/data` | `sys:form:data:submit` | 提交表单数据（body：`definitionId` 必填、`dataJson` 必填、`bizKey` 可选）→ 建数据(status=1) |
| GET | `/form/data/page` | `sys:form:data:list` | 数据分页（`definitionCode` 等值、`bizKey` 等值、`submitter` 等值），按 `create_time desc`，单页上限 200 |
| GET | `/form/data/{id}` | `sys:form:data:list` | 数据详情 |
| DELETE | `/form/data/{id}` | `sys:form:data:delete` | 删除数据（逻辑删除） |

- 统一响应体 `Result<T>` / `PageResult<T>`。
- 业务校验：定义不存在 → `NOT_FOUND`；数据不存在 → `NOT_FOUND`；`dataJson` 为空 → `PARAM_ERROR`（DTO `@NotBlank`）；`dataJson` 超 64KB → `PARAM_ERROR`。

### 3.3 权限种子（whale-auth `db/init.sql` 追加，落 `bluewhale_auth.sys_permission`）

| id | perm_code | perm_name | method | path |
|---|---|---|---|---|
| 49 | `sys:form:data:submit` | 提交表单数据 | POST | `/form/data` |
| 50 | `sys:form:data:list` | 查询表单数据 | GET | `/form/data/**` |
| 51 | `sys:form:data:delete` | 删除表单数据 | DELETE | `/form/data/{id}` |

---

## 四、新增/修改文件清单（预期）

- 新增 `entity/FormData.java`、`mapper/FormDataMapper.java`
- 新增 `dto/{FormDataSubmitRequest,FormDataQueryRequest}.java`、`vo/FormDataVO.java`
- 新增 `service/FormDataService.java(+impl/FormDataServiceImpl.java)`、`controller/FormDataController.java`
- 修改 `whale-form/src/main/resources/db/init.sql` 追加 `form_data` 表
- 修改 `whale-auth/src/main/resources/db/init.sql`（种子 49–51）
- 新增测试 `src/test/java/com/bluewhale/form/service/impl/FormDataServiceImplTest.java`

---

## 五、完成定义（DoD）

- `whale-form` 模块 `test` + `checkstyle:check` 全绿（0 违规）；全量 `mvn clean install -T 1C` BUILD SUCCESS。
- 运行时段证据（经网关 8080）：建表 + 种子落库；提交 `code=0` → 分页/详情可见 → 逻辑删除后详情 `404`；负向：定义不存在 `404`、`dataJson` 为空 `400`。
- `docs/reviews/2026-10-19-wave5.26-form-closed-loop.md` + devlog 追加 + roadmap/task-breakdown 勾选 + commit & push origin（Gitee）。
