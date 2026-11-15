# whale-form 安全与权限深化（批次 4）

> 已落地：导出脱敏（第 22 项）、多版本检索限流（第 23 项）、JSON 深度防护（第 24 项）、
> ArchUnit 补强（第 16 项）、契约测试（第 18 项）、注释同步（第 20 项）、
> 字段级权限（第 6 项）、附件引用表（第 3 项）。
> 文档约束：`X-Username` 可信边界（第 21 项）、DDL 权限收敛（第 14 项）。
> 保持不做：子表条件检索（第 3 项的剩余部分）。

## 一、已落地项

### 1. 导出脱敏（第 22 项）

- 列元数据 `form_column_meta.sensitive`（V13，0 普通 / 1 敏感），编译发布快照时写入字段节点
  `sensitive=true`，`FormSchemaParser` 解析到 `FormField#sensitive`。
- 导出 CSV/XLSX 时按标记掩码：长度 ≥ 11 保留前 3 后 4（`138****5678`）、长度 ≥ 7 保留前 2 后 2、
  其余 `***`；**入库、详情、内部接口保持原值**，避免破坏业务读取。
- 历史版本独有字段（导出时按 `dataJson` 补列）没有敏感标记，不做掩码——这是已知边界，
  需要覆盖时应在列元数据中补标后重新发布。

### 2. 多版本检索限流（第 23 项）

- 动态字段检索只让**最近 3 个版本**的物理表参与（按路由版本号倒序），避免版本堆积时
  查询次数线性放大（每版本 count + selectIds 两次查询）。
- 边界：更老版本的数据不参与条件检索；按主键详情、不带条件的列表/导出不受影响。
  若业务确需检索历史版本，应通过导出或对账脚本单独处理。

### 3. JSON 深度防护（第 24 项）

- 新增 `FormJson#assertDepth`：进入 Jackson 解析前做一次线性扫描，拒绝嵌套超过 64 层的 JSON；
  字符串字面量内的括号不计数（含转义引号）。
- 覆盖入口：表单结构定义、字段扩展配置（`validateJson`）、表单标签、提交数据、动态过滤条件。
- 边界：只限制嵌套深度，长度仍由各入口原有上限约束（结构 64KB / 过滤 4KB / 扩展配置 1KB）。

### 4. ArchUnit 与契约测试（第 16、18 项）

- 新增规则：`service` 不得依赖 `controller`；`support` 不得依赖 `controller` / `service`
  （原有 controller↛mapper/po、mapper↛controller/service、po↛外层规则保留）。
- 新增 `FormContractTest`：固化 `FormDataVO` / `FormDefinitionVO` 的 JSON 字段集合，
  改名或误删会直接失败；`schemaJson` 为空时按 `NON_NULL` 省略的行为也被固化。

### 5. 注释同步（第 20 项）

- 状态列表类注释统一为「1 草稿 / 2 审批中 / 3 已通过 / 4 已驳回」等与常量一致的口径；
  新增字段注释明确「仅影响导出脱敏」「仅运维标记」等作用范围。

## 二、部署层约束（第 21、14 项，代码不落）

### 1. `X-Username` 可信边界（第 21 项）

- 现状：提交人取自请求头 `X-Username`（`WebHeaders.username`），解耦了网关鉴权与业务服务。
- 约束：**该头只允许由网关写入**。部署时必须满足：
  1. 业务服务不直接暴露公网端口，只允许网关网段访问（安全组 / NetworkPolicy）；
  2. 网关在转发前**覆盖**（而非透传）客户端传入的 `X-Username`、`X-Tenant-Id`；
  3. 服务直连场景（调试、运维脚本）必须走内网通道，并显式设置可信头。
- 后续可选加固：从 JWT 解析用户名，彻底不依赖请求头。该改动涉及鉴权 starter 与服务间调用，
  单独排期。

### 2. 动态 DDL 权限收敛（第 14 项）

- 现状：发布时由应用主账号执行 `CREATE/ALTER` 物理表（`formDataEngine.executeDdl`）。
- 约束：生产环境应用账号只应具备 `form_data_*` 前缀表的 DDL 权限，禁止全局 `CREATE/ALTER/DROP`：
  - MySQL 8：`GRANT CREATE, ALTER, DROP ON \`form\_data\_%\`.* TO 'form_app'@'%';`（按库名调整），
    并从库级/全局权限中移除同类权限；
  - 或由 DBA 审批后通过独立通道执行 DDL（应用只生成并审计语句，`form_ddl_audit` 已有全量语句留痕）。
- 发布预检（批次 1）与 DDL 审计（已有）保留，作为权限收敛后的第二道防线。

## 三、字段级权限与附件引用（第 6、3 项）

### 1. 字段级权限（第 6 项，已落地）

- 数据与配置：`form_field_config.visible_roles` / `editable_roles`（V14，JSON 字符串数组文本，空表示不限制），
  保存时校验为 JSON 字符串数组并去重；编译表单视图快照时写入 schema 字段节点。
- 角色来源：网关注入的 `X-User-Roles`（逗号/分号分隔），由 `FormTenantConfig` 拦截器写入
  `FormRoleContext` 线程上下文，请求结束清理。
- 服务端行为：
  - **详情/归档详情**：不可见字段不查询、不返回；返回 `readonlyFields` 标记当前角色不可编辑的字段。
  - **导出**：按可见角色裁剪列（再叠加敏感字段脱敏）。
  - **提交**：当前角色不可编辑的字段不允许携带非空值，直接拒绝。
  - **修改**：当前角色不可编辑的字段值发生变化时拒绝（值未变化放行）。
  - **列表/过滤检索**：不返回字段值，不做字段裁剪。
- 兼容边界：**未携带角色头**（服务间内网调用、旧调用方）时不做任何字段裁剪与限制，行为与升级前一致；
  因此字段级权限同样依赖「`X-User-Roles` 仅由网关覆盖注入」的部署约束（见第二节）。
- 未做：字段级权限不影响审批只读判定（审批中整单只读仍然优先）。

### 2. 附件引用表（第 3 项，附件反查已落地；子表检索仍不支持）

- 新增 `form_attachment_ref`（V15）：`(tenant_id, data_id, field_key, attachment_id)` 唯一，
  `(tenant_id, attachment_id)` 索引；提交/导入/修改时按附件字段值重建引用（先清理后写入），删除数据时清理。
- 反查接口：`GET /form/v1/data/attachment-refs?attachmentId=...`（权限 `sys:form:data:list`，单次最多 200 条）。
- 子表检索仍明确不支持：`SUBTABLE` 以 JSON 文本落库，不参与条件检索；
  `FormDataType.FILTERABLE` 继续排除 `SUBTABLE` / `ATTACHMENT`。
- 存量数据：引用表只覆盖升级后的写入；历史数据需一次性回填（按 `form_data.dataJson` 扫描附件字段），
  脚本按环境单独执行，不放入 Flyway（避免大表长事务）。

## 四、验收与回归

- `mvn -o -pl whale-services/whale-service-form test`：249 项通过
  （批次 4 新增 11 项，批次 5 新增 10 项：字段权限 7、附件引用 3）。
- `mvn -o -pl whale-services/whale-service-form checkstyle:check`：通过。
- 迁移：form 库新增 `V13`（列敏感标记）、`V14`（字段级角色）、`V15`（附件引用表），不自动授权任何角色。
- 兼容：`sensitive` 缺省 0，未标记字段导出行为不变；字段角色为空表示不限制；检索限流只影响第 4 个及更老版本的条件检索。
