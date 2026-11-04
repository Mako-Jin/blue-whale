# Wave 5.80 · PDM M0-B 图文档 ACL 任务清单

> 规格：[spec.md](./spec.md)。按顺序执行，每项完成后立即更新状态。
> 所有后端路径相对 `backend/whale-apps/whale-pdm/`，前端路径相对 `frontend/platform-apps/whale-pdm/`。

## T1 数据库与权限迁移
- [x] 新增 `src/main/resources/db/migration/V3__document_acl_subject.sql`
  - `information_schema` 守卫，仅在 `subject_id` 为 `bigint` 时改为 `VARCHAR(64)`
  - 无 `DROP/TRUNCATE/DELETE`
- [x] 新增 `backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.80-pdm-acl.sql`
  - 权限 910 `sys:pdm:doc:acl:list`（`/pdm/acl` GET）
  - 权限 911 `sys:pdm:doc:acl:manage`（`/pdm/acl,/pdm/acl/{id}` PUT/DELETE）
  - `tenant_admin / pdm_manager / pdm_doc_manager` 授权 910–911

**验证**：`PdmFlywayMigrationScriptTest` 通过；脚本可重复执行。

## T2 ACL 领域模型
- [x] `document/entity/PdmDocAcl.java`（继承 `BaseEntity`，`subjectId` 为 `String`）
- [x] `document/mapper/PdmDocAclMapper.java`
- [x] `document/dto/PdmDocAclSaveRequest.java`
- [x] `document/vo/PdmDocAclVO.java`
- [x] `document/support/PdmDocAclConstants.java`（资源/主体/动作/效果常量 + 校验）
- [x] `document/support/PdmDocAclDecision.java`（`ALLOW/DENY/ABSTAIN`）
- [x] `document/support/PdmDocViewer.java`（`userId + roles`，`of(request)`、`canManage()`）
- [x] `converter/PdmConverter.java` 增加 `toAclVo`

**验证**：字段与 DDL 一一对应；`PdmArchitectureTest` 通过。

## T3 目录祖先链能力
- [x] `PdmDocFolderService#ancestorFolderIds(Long folderId)`
- [x] `PdmDocFolderService#subtreeFolderIds(Collection<Long> folderIds)`
- [x] `PdmDocFolderServiceImpl` 实现：`path` 解析、缺失路径按 `parentId` 回溯、空集合返回空
- [x] 既有 `subtreeFolderIds(Long)` 委托到集合实现

**验证**：目录路径 `/1/2/3/` 返回 `[1,2,3]`；移动目录后结果随 `path` 更新。

## T4 ACL 解析器
- [x] `document/service/impl/PdmDocAclResolver.java`
  - `decideDocument(documentId, folderId, viewer, action)`
  - `decideFolder(folderId, viewer, action)`
  - `deniedFolderIds(viewer)`（`VIEW + DENY` 目录规则展开子树）
  - `deniedDocumentIds(viewer)`（`VIEW + DENY` 文档规则去重）
  - 主体匹配：`ALL` 恒命中、`USER` 比较用户 ID 文本、`ROLE` 比较角色 code
  - 判定顺序：任意 `DENY` 短路 → 否则 `ALLOW` → 否则 `ABSTAIN`

**验证**：`PdmDocAclResolverTest` 覆盖继承、DENY 优先与三类主体。

## T5 ACL 服务与接口
- [x] `document/service/PdmDocAclService.java` + `impl/PdmDocAclServiceImpl.java`
  - `list(resourceType, resourceId, viewer)`
  - `save(request, viewer)`
  - `delete(id, viewer)`
  - 资源存在性校验；`ALL → "0"`；`USER` 必须数字；`ROLE` 保留 code
  - 唯一键重复时返回既有记录（捕获 `DuplicateKeyException` 后重查）
  - `assertManage`：`DENY` 拒绝、`ALLOW` 放行、`ABSTAIN` 要求属主或 `canManage`
- [x] `document/controller/PdmDocAclController.java`
  - `GET /pdm/acl` → `sys:pdm:doc:acl:list`
  - `PUT /pdm/acl`、`DELETE /pdm/acl/{id}` → `sys:pdm:doc:acl:manage`
  - 写操作加 `@AuditLog`

**验证**：`PdmDocAclServiceImplTest`；重复 PUT 幂等。

## T6 文档服务接入 ACL
- [x] `PdmDocDocumentService` 接口改为 `PdmDocViewer` 签名（见 spec §4.3）
- [x] `PdmDocDocumentServiceImpl`：
  - `page` 追加 `notIn(folderId, deniedFolderIds)` / `notIn(id, deniedDocumentIds)`，空集合不追加
  - `detail/versions`：`VIEW=DENY` → 403
  - `download`：`DOWNLOAD=DENY` → 403
  - `update/delete/uploadVersion/checkOut/checkIn/release/rollback`：`DENY → 403`、`ALLOW → 放行`、`ABSTAIN → 属主/管理员`
- [x] `PdmDocDocumentController` 统一 `PdmDocViewer.of(servletRequest)` 传参

**验证**：`PdmDocDocumentServiceImplTest` 全部通过并新增 ACL 用例。

## T7 前端 ACL 弹窗
- [x] `src/api/document.ts`：`PdmDocAcl`、`PdmDocAclSavePayload`、`listAcl/saveAcl/deleteAcl`
- [x] 新增 `src/pages/document/AclModal.tsx`
  - 规则列表、新增表单（主体类型/主体标识/动作/效果）、删除确认
  - `ALL` 时主体标识禁用并置 `0`；维护按钮按 `sys:pdm:doc:acl:manage` 显隐
- [x] `src/pages/document/index.tsx`
  - 文档行“权限”按钮（`sys:pdm:doc:acl:list`）
  - 选中目录后“目录权限”按钮
  - 复用 `AclModal`，变更后刷新列表/目录

**验证**：`pnpm --filter whale-pdm lint` 与 `build` 通过。

## T8 文档与验收
- [x] 更新 `docs/design/pdm-module-panorama.md` 的 M0-B 状态与接口表
- [x] 更新 `docs/plans/2026-11-04-wave5.80-pdm-panorama.md` 的 M0-B 进度
- [x] 新增 `docs/reviews/2026-11-04-wave5.80-pdm-m0b.md`（检查结论 + 验证命令）
- [x] 运行 `mvn -pl backend/whale-apps/whale-pdm test`
- [x] 运行 `pnpm --filter whale-pdm lint`、`pnpm --filter whale-pdm build`
- [x] 仅提交 PDM 专属文件；`docs/devlog/2026-11-04.md`、`docs/requirements/register.md`、`docs/service-catalog.md`、`frontend/pnpm-lock.yaml` 保持未提交

**验证**：验收标准 §7 全部满足，提交信息为中文 `feat:`。
