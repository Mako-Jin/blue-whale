# Wave 5.80 · PDM M0-B 图文档 ACL 规格

- 状态：待评审
- 上级计划：[2026-11-04-wave5.80-pdm-panorama.md](../../plans/2026-11-04-wave5.80-pdm-panorama.md)（M0-B）
- 目标模块：`whale-backend/whale-apps/whale-pdm`、`whale-frontend/platform-apps/whale-pdm`
- 前置：M0-A 图文档中心已完成（commit `baf3d617`）
- 不在本规格范围：在线预览、导出、审批联动、水印、审计报表

## 1. 背景与问题

M0-A 已落地目录树、文档主档、版本、检入/检出、发布/回滚、下载代理，但存在三个缺口：

| 缺口 | 现状 | 本规格交付 |
|---|---|---|
| ACL 表未启用 | `pdm_doc_acl` 已建表但无实体/服务/接口 | 文档与目录级 ACL 的增删查 |
| 无有效权限解析 | 只校验功能权限码 `sys:pdm:doc:list` / `download` | 文档 → 所在目录祖先链继承，显式 `DENY` 优先于 `ALLOW` |
| 无数据级过滤 | 列表返回目录子树全部文档 | 列表过滤无 `VIEW` 文档；详情/版本/下载/写操作鉴权 |

角色侧约束：网关只下发角色 **code**（`X-User-Roles`），不下发角色 ID，因此 ACL 的 `ROLE` 主体必须按角色码存储与匹配。

## 2. 设计决策

### D-1 `subject_id` 改为字符串，兼容三类主体
新增 Flyway `V3__document_acl_subject.sql`，将 `pdm_doc_acl.subject_id` 从 `BIGINT` 改为 `VARCHAR(64)`：

| subject_type | subject_id 取值 |
|---|---|
| `ALL` | 固定 `0` |
| `USER` | 用户 ID 十进制文本 |
| `ROLE` | 角色 code（如 `pdm_member`） |

迁移必须幂等：通过 `information_schema.COLUMNS` 判断当前类型，仅当仍是 `bigint` 时执行 `ALTER ... MODIFY`；脚本内不得出现 `DROP/TRUNCATE/DELETE`。

### D-2 有效权限解析只做三态
`PdmDocAclDecision` 取值为 `ALLOW / DENY / ABSTAIN`，解析顺序固定：

1. 命中任意匹配主体的 `DENY` → `DENY`（短路，DENY 优先）；
2. 存在匹配主体的 `ALLOW` → `ALLOW`；
3. 无任何匹配规则 → `ABSTAIN`（由调用方回落到既有规则）。

主体匹配：`ALL` 恒命中；`USER` 比较 `userId` 文本；`ROLE` 比较当前角色 code 集合；其他主体一律不命中。

### D-3 继承按目录祖先链，不按子树
文档的有效规则 = 文档自身 `DOCUMENT` 规则 + 文档所在目录及其全部父目录的 `FOLDER` 规则。目录 ACL 对目录内文档、子目录及子目录内文档生效。

- 新增 `PdmDocFolderService#ancestorFolderIds(Long folderId)`：解析 `path`（形如 `/1/2/`）得到根到当前目录的 ID 链，路径缺失时沿 `parentId` 回溯，末端保证包含当前目录自身。
- 新增 `PdmDocFolderService#subtreeFolderIds(Collection<Long> folderIds)`：一次加载目录集合，按 `path` 前缀计算全部后代；空入参返回空列表，未知 ID 跳过。

### D-4 读操作默认放行，显式 DENY 收敛
- `VIEW`：`DENY` → 403/过滤；`ALLOW` 或 `ABSTAIN` → 允许（既有 `sys:pdm:doc:list` 仍为第一道门）。
- `DOWNLOAD`：`DENY` → 403；`ALLOW` 或 `ABSTAIN` → 允许（既有 `sys:pdm:doc:download` 仍为第一道门）。

### D-5 写操作保持“属主/管理员”，ACL 作为叠加层
对 `EDIT`（更新、上传版本、检出、检入、发布、回滚）与 `DELETE`：

```
DENY  → 403（即使本人是属主或管理员）
ALLOW → 放行（即使不是属主）
ABSTAIN → 回落既有 assertOwnerOrManager
```

`MANAGE` 仅用于 ACL 管理接口，判定口径同上：`DENY` 拒绝，`ALLOW` 放行，`ABSTAIN` 要求属主或 `PdmDocRoles.canManage`。

### D-6 列表过滤在 SQL 层完成，保证分页总数正确
`page` 不拉全量再内存过滤，而是在查询条件上排除：

- 所有命中当前用户的、`VIEW + DENY` 的目录规则，展开为目录子树 ID 集合 → `notIn(folder_id, ...)`；
- 所有命中当前用户的、`VIEW + DENY` 的文档规则 → `notIn(id, ...)`。

集合为空时不追加条件，避免生成 `IN ()`。文档级 `ALLOW` 不能突破目录级 `DENY`（D-2 短路）。

### D-7 目录树不做可见性裁剪
M0-B 的目录树仍返回完整树，保证 ACL 管理可导航；目录树可见性裁剪留到 M1A 权限细化。文档列表与详情才是数据隔离主路径。

### D-8 接口统一为 `/pdm/acl`，按 `resourceType` 区分资源
不拆分 document/folder 两个控制器，避免重复代码：

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/pdm/acl?resourceType=DOCUMENT&resourceId={id}` | 查询规则 |
| `PUT` | `/pdm/acl` | 幂等新增规则（请求体含资源与主体） |
| `DELETE` | `/pdm/acl/{id}` | 删除规则 |

`GET` 同样要求 `MANAGE` 能力，防止普通成员枚举权限配置。

### D-9 前端只做文档级 + 目录级 ACL 弹窗
列表行“权限”操作打开文档 ACL；目录树选中节点后“目录权限”打开目录 ACL。两种场景复用同一弹窗组件。

## 3. 数据库与权限迁移

### 3.1 Flyway

新增 `whale-backend/whale-apps/whale-pdm/src/main/resources/db/migration/V3__document_acl_subject.sql`：

```sql
-- 将 subject_id 扩展为字符串：USER 存用户ID，ROLE 存角色码，ALL 存 0
SET @subject_type := (
  SELECT DATA_TYPE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_doc_acl' AND COLUMN_NAME = 'subject_id'
);
SET @ddl := IF(@subject_type = 'bigint',
  'ALTER TABLE pdm_doc_acl MODIFY COLUMN subject_id VARCHAR(64) NOT NULL DEFAULT ''0'' COMMENT ''主体标识：USER 存用户ID，ROLE 存角色码，ALL 为 0''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

唯一键 `uk_doc_acl` 不变（`tenant_id, resource_type, resource_id, subject_type, subject_id, action, effect`）。

### 3.2 权限迁移

新增 `whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.80-pdm-acl.sql`，沿用既有幂等写法（`INSERT ... WHERE NOT EXISTS` + MD5 授权 ID）：

| ID | perm_code | 名称 | path / method |
|---|---|---|---|
| 910 | `sys:pdm:doc:acl:list` | 查询图文档权限 | `/pdm/acl` / `GET` |
| 911 | `sys:pdm:doc:acl:manage` | 维护图文档权限 | `/pdm/acl,/pdm/acl/{id}` / `PUT/DELETE` |

授权：`tenant_admin`、`pdm_manager`、`pdm_doc_manager` 获得 910–911；`pdm_member` 不授予。菜单不新增（ACL 是页内弹窗）。

前置：`bluewhale_pdm` 已执行 `V2__document_center.sql` 与 `V3__document_acl_subject.sql`。

## 4. 后端文件与改动

### 4.1 新增

| 文件 | 职责 |
|---|---|
| `document/entity/PdmDocAcl.java` | 继承 `BaseEntity`；字段 `resourceType/resourceId/subjectType/subjectId/action/effect` |
| `document/mapper/PdmDocAclMapper.java` | `BaseMapper<PdmDocAcl>` |
| `document/dto/PdmDocAclSaveRequest.java` | `resourceType/resourceId/subjectType/subjectId/action/effect`，`@NotBlank/@NotNull` |
| `document/vo/PdmDocAclVO.java` | 规则视图（含 `id/createTime/updateTime`） |
| `document/support/PdmDocAclConstants.java` | 资源/主体/动作/效果常量与 `isValidXxx` |
| `document/support/PdmDocAclDecision.java` | `ALLOW/DENY/ABSTAIN` 枚举 |
| `document/support/PdmDocViewer.java` | `record(Long userId, Set<String> roles)`，`of(HttpServletRequest)`、`canManage()` |
| `document/service/PdmDocAclService.java` | `list/save/delete` |
| `document/service/impl/PdmDocAclServiceImpl.java` | 校验资源存在、主体/动作/效果合法、唯一键幂等插入、删除 |
| `document/service/impl/PdmDocAclResolver.java` | `decideDocument`、`decideFolder`、`deniedFolderIds`、`deniedDocumentIds` |
| `document/controller/PdmDocAclController.java` | `/pdm/acl` 三端点 + `@RequirePermission` + `@AuditLog` |

### 4.2 修改

- `PdmConverter`：新增 `@Mapping(target = "id", ignore = true)` 等必要忽略的 `toAclVo(PdmDocAcl)`。
- `PdmDocFolderService` / `PdmDocFolderServiceImpl`：
  - `ancestorFolderIds(Long folderId)`；
  - `subtreeFolderIds(Collection<Long>)`，既有单 ID 方法委托到集合实现。
- `PdmDocDocumentService` / `PdmDocDocumentServiceImpl`：读取与写入方法改为接收 `PdmDocViewer`，并接入 ACL：
  - `page(request, viewer)`：按 D-6 排除 DENY 目录子树与文档；
  - `detail(id, viewer)`、`versions(id, viewer)`：`VIEW=DENY` → 403；
  - `download(id, versionId, viewer)`：`DOWNLOAD=DENY` → 403；
  - `update/delete/uploadVersion/checkOut/checkIn/release/rollback`：按 D-5 先 ACL 后属主/管理员；
  - `create` 不改鉴权，仍由 `sys:pdm:doc:add` 控制。
- `PdmDocDocumentController`：统一构造 `PdmDocViewer.of(servletRequest)` 并传入。

### 4.3 方法签名（关键）

```java
PageResult<PdmDocDocumentVO> page(PdmDocDocumentQueryRequest request, PdmDocViewer viewer);
PdmDocDocumentVO detail(Long id, PdmDocViewer viewer);
List<PdmDocVersionVO> versions(Long documentId, PdmDocViewer viewer);
PdmDocDownload download(Long documentId, Long versionId, PdmDocViewer viewer);
PdmDocDocumentVO update(Long id, PdmDocDocumentSaveRequest request, PdmDocViewer viewer, boolean canManage);
void delete(Long id, PdmDocViewer viewer, boolean canManage);
PdmDocVersionVO uploadVersion(Long documentId, PdmDocUploadFile file, String changeNote, PdmDocViewer viewer, boolean canManage);
PdmDocDocumentVO checkOut(Long documentId, PdmDocViewer viewer);
PdmDocVersionVO checkIn(Long documentId, PdmDocUploadFile file, String changeNote, PdmDocViewer viewer);
PdmDocVersionVO release(Long documentId, Long versionId, PdmDocViewer viewer, boolean canManage);
PdmDocVersionVO rollback(Long documentId, Long versionId, PdmDocViewer viewer, boolean canManage);
```

## 5. 前端改动

- `src/api/document.ts`：新增类型 `PdmDocAcl`、`PdmDocAclSavePayload`，函数 `listAcl(resourceType, resourceId)`、`saveAcl(payload)`、`deleteAcl(id)`，复用现有请求封装。
- 新增 `src/pages/document/AclModal.tsx`：
  - Props：`open/resourceType/resourceId/title/onClose/onChanged`；
  - 表格展示已有规则；新增表单：主体类型（USER/ROLE/ALL）、主体标识（ALL 时禁用并置 `0`）、动作、效果；
  - 删除二次确认；操作按钮按 `hasPermission('sys:pdm:doc:acl:manage')` 控制显隐。
- `src/pages/document/index.tsx`：
  - 列表行新增“权限”按钮（`sys:pdm:doc:acl:list`）；
  - 目录树选中节点后新增“目录权限”按钮；
  - 复用 `AclModal`，关闭后按需刷新列表/目录。

## 6. 测试

### 6.1 新增单测

- `PdmDocAclResolverTest`
  - 无规则 → `ABSTAIN`；
  - 文档级 `DENY VIEW` → `DENY`；
  - 目录级 `DENY VIEW` 对子目录文档生效；
  - 文档 `ALLOW` + 目录 `DENY` → `DENY`（DENY 优先）；
  - `ALL/ USER/ ROLE` 三类主体命中与不命中；
  - `deniedFolderIds` 展开子树、`deniedDocumentIds` 去重。
- `PdmDocAclServiceImplTest`
  - 非法 `resourceType/subjectType/action/effect` → 400；
  - `ALL` 强制 `subject_id=0`；`USER` 必须为数字；`ROLE` 保留角色码；
  - 重复唯一键幂等返回既有记录；
  - 删除跨租户/不存在 → 404（由租户插件与 `selectById` 兜底）。

### 6.2 修改既有单测

`PdmDocDocumentServiceImplTest` 全部调用补 `PdmDocViewer`，并新增：

- `page` 排除 DENY 目录子树与 DENY 文档；
- `detail/versions` 无 VIEW → 403；
- `download` DENY DOWNLOAD → 403；
- 属主被显式 `DENY EDIT` 时上传/更新被拒；
- 非属主获得 `ALLOW EDIT` 后可上传。

### 6.3 验证命令

```powershell
mvn -pl whale-backend/whale-apps/whale-pdm test
pnpm --filter whale-pdm lint
pnpm --filter whale-pdm build
```

## 7. 验收标准

1. 目录级 `DENY VIEW` 后，该目录及子目录文档不再出现在 `page`，总数同步减少。
2. 文档级 `DENY VIEW` 后，`detail` 与 `versions` 返回 403。
3. 文档级 `DENY DOWNLOAD` 后，下载返回 403；无规则时保持原行为。
4. 文档级 `DENY EDIT` 可拦截属主上传；`ALLOW EDIT` 可放行非属主。
5. `MANAGE` 能力控制 ACL 查询与维护；普通成员调用返回 403。
6. `ROLE` 规则按角色码命中，`USER` 按用户 ID 命中，`ALL` 命中所有用户。
7. Flyway `V3` 幂等、无破坏性语句；权限迁移可重复执行。
8. 后端测试、前端 lint/build 通过。

## 8. 风险与回滚

| 风险 | 缓解 |
|---|---|
| 更改 `subject_id` 类型影响存量数据 | 仅从 `BIGINT` 转 `VARCHAR`，数值文本兼容；迁移前做备份 |
| ACL 误配导致全员不可见 | `ALLOW` 无法突破 `DENY`；管理端可删除 `DENY` 规则恢复；管理员角色默认拥有 `MANAGE` |
| 列表 SQL 条件过多 | 空集合不追加条件；DENY 目录先收敛为子树集合 |
| 写操作收紧导致既有调用失败 | 全部为新增 ACL 叠加，无规则时行为与 M0-A 一致 |

## 9. 批准门槛

本规格需用户确认后进入实现；确认前不修改代码、不新增迁移脚本。
