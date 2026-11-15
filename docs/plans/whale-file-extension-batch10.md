# whale-file 能力扩展（批次 10：上传策略热更新）

## 一、范围与决策

批次 1~9 已完成存储路由与回收站、重命名/移动、指标与健康检查、孤儿对象对账、分享链接、
批量删除与打包下载、上传内容魔数校验、租户/桶级配额、文件生命周期领域事件、CDN 预签名地址。
本批次落地路线图阶段 3 的「上传策略热更新」，替代此前判定过大的「文件版本」批次
（文件版本仍需按数据模型、版本列表、回滚与清理再拆批）。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 运行时查询上传策略 | ✅ | `GET /file/v1/upload-policy`，需 `sys:file:manage` |
| 运行时替换权限码规则 | ✅ | `PUT /file/v1/upload-policy`，整表替换并返回生效视图 |
| 策略开关热更新 | ❌ | `enabled` 仍只来自启动配置，避免误操作把全站上限放大/关闭 |
| 多实例同步 | ❌ | 规则只保存在当前实例内存，多副本需配置中心广播（见待做项） |
| 规则持久化 | ❌ | 重启后回到 `whale.file.upload-policy.*` 配置值，不落库 |

**边界**：只热更新「按权限码的规则集合」，不改变全局 `max-size-bytes` / `quota-bytes`，
也不改变普通上传（multipart 全量读入）的上限口径；未命中规则仍回落全局默认值。

## 二、设计

### 2.1 注册表

新增 `UploadPolicyRegistry`（`service/support`，`@Component`）：

- 构造时从 `FileProperties#getUploadPolicy().getRules()` 深拷贝一份快照作为初始值；
- `rules()` 返回不可变快照（`List.copyOf`），供上传路径高频只读访问；
- `replace(rules)` 先校验再整体替换（`AtomicReference#set`），校验失败抛
  `PARAM_ERROR` 且保留旧快照：
  1. 规则列表非空；
  2. 每条规则非 `null`；
  3. `permission` 非空白（去除首尾空白后保存）；
  4. `maxSizeBytes > 0`；
  5. `quotaBytes` 允许 `null`（沿用全局配额）或 `>= 0`（`0` 表示不限）。

启动配置里的非法规则仍按原语义在解析阶段被跳过（不阻塞启动），
热更新入口则直接拒绝，避免把无效规则写进运行期。

### 2.2 策略解析

`FileUploadPolicy` 改为从 `UploadPolicyRegistry` 读取规则，`enabled` 仍读 `FileProperties`；
选择语义不变：命中多条取单文件上限最大的一条、`quotaBytes` 为空沿用全局配额、
`super_admin` 视为命中全部规则。构造参数由 `(FileProperties)` 变为
`(FileProperties, UploadPolicyRegistry)`。

### 2.3 接口

新增 `FileUploadPolicyApi`（`/file/v{version}/upload-policy`）：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/file/v1/upload-policy` | `sys:file:manage` | 返回开关、全局默认值、当前规则 |
| PUT | `/file/v1/upload-policy` | `sys:file:manage` | 整表替换规则，返回更新后的视图 |

响应 `UploadPolicyResponse`：`enabled`、`defaultMaxSizeBytes`、`defaultQuotaBytes`、
`rules[]`（`permission`、`maxSizeBytes`、`quotaBytes`）。
请求 `UploadPolicyUpdateRequest`：`rules[]` 必填非空，权限码 `@NotBlank`、
上限 `@Min(1)`、配额 `@Min(0)` 且允许缺省。

`enabled` 与全局默认值只在响应中展示，PUT 请求体不包含，防止借热更新绕过启动配置。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 注册表 | 初始快照来自配置；替换后生效；输入列表/规则对象后续变更不影响快照；返回列表不可变；空列表、空条目、空白权限码、非正上限、负配额被拒绝且保留旧快照 |
| 策略解析 | 既有 8 项用例改用注册表；热更新后按新规则解析；被替换掉的旧规则不再生效 |
| 控制器 | GET 返回开关/默认值/规则；PUT 生效并返回新视图；非法请求体返回 `PARAM_ERROR` 且不改动注册表；GET/PUT 均标注 `sys:file:manage` |

## 四、完成情况

- 注册表：`UploadPolicyRegistry`（`service/support`，`@Component`）构造时深拷贝
  `whale.file.upload-policy.rules`，`rules()` 返回不可变快照，`replace(rules)` 经
  `AtomicReference` 原子整表替换；列表为空、条目为空、权限码空白、上限非正、配额为负
  均抛 `PARAM_ERROR` 且保留旧快照，权限码保存前去首尾空白。
- 策略：`FileUploadPolicy` 改为从注册表读取规则，`enabled` 与全局默认值仍读
  `FileProperties`；命中多条取最大上限、配额空沿用默认、`super_admin` 全命中均保持不变。
- 接口：新增 `FileUploadPolicyApi`、`UploadPolicyResponse`、`UploadPolicyUpdateRequest`、
  `FileUploadPolicyController`；`GET|PUT /file/v1/upload-policy` 均要求 `sys:file:manage`，
  PUT 返回更新后的视图，请求体不含开关与全局默认值。
- 测试：`UploadPolicyRegistryTest` 6 项、`FileUploadPolicyTest` 新增热更新 1 项、
  `FileUploadPolicyControllerTest` 5 项；既有控制器测试改用带注册表的构造。
- 配置未变：沿用 `whale.file.upload-policy.enabled` 与 `rules`，无新增配置键与数据库变更。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
302 项单测零失败（批次 9 为 290，本轮新增 12 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 规则来源改为注册表后，既有用例继续修改 `FileProperties#getRules()` 已不再影响解析 →
  改为通过 `registry.replace` 注入；「非法配置被忽略」用例改用独立 `FileProperties` 构造注册表，
  避免误用会拒绝非法规则的热更新入口。
- 首版 `List.copyOf` 只复制列表外壳，外部仍可通过 `Rule` 的 setter 改到注册表内部状态 →
  构造与替换均逐条深拷贝，并补「输入列表与规则对象变更不影响快照」用例。
- `FileInternalControllerTest` 构造策略时误建两个 `FileProperties` 实例 → 统一为同一配置对象。

## 七、待做项

- 多实例同步：当前规则仅存在本实例内存，多副本部署需接入配置中心（Nacos）广播或改为持久化 + 定时拉取。
- 规则持久化与审计：如需重启保留与操作留痕，应落库并记录操作人/时间。
- 全局默认上限（`max-size-bytes` / `quota-bytes`）的热更新与 `enabled` 开关的受控变更。
- 文件版本（数据模型 + 新版本上传、版本列表、回滚与清理/配额口径，仍需拆批）。
- 存储迁移工具、多段 Range、存储加密、防盗链/限流。
