# Wave 5.80 PDM M4：项目交付物、研发评审与在线预览（规格）

> 状态：已实现（2026-11-05）
> 关联计划：[Wave 5.80 PDM 全景计划](../../plans/2026-11-04-wave5.80-pdm-panorama.md) §十
> 关联需求：REQ-FUNC-054（研发项目交付物与评审）、REQ-FUNC-050（图文档预览）、REQ-SEC-030、REQ-ARCH-020

---

## 一、范围

| 能力 | 包含 | 不包含 |
|---|---|---|
| 项目交付物 | 引用 `whale-pm` 项目/任务与 PDM 已发布文档/BOM/工艺路线版本；归档、解除、分页检索 | 项目立项、任务派发、甘特（归 `whale-pm`） |
| 研发评审 | 设计/样机/转产评审；草稿、关闭、结论、参与人、附件、OA 审批实例快照 | 审批流引擎与会签（归 OA/Workflow） |
| 在线预览 | 预览任务幂等创建、状态、重试、inline 内容读取；原生 PDF/图片/STL/glTF | 字节存储（归 `whale-file`）、3D 渲染引擎共享包（冻结） |
| 检索增强 | 文档关键字扩展到描述与设计师 | MySQL ngram、标题栏 OCR（按 D-PDM-3 另行启用） |

---

## 二、数据模型（`V11__collab_preview.sql`）

| 表 | 关键字段 | 约束 |
|---|---|---|
| `pdm_clb_deliverable` | 项目/任务引用与名称快照、对象类型/ID/编号/标题/版本、交付物类型、归档人/时间 | 唯一 `(tenant_id, project_id, object_type, object_id, object_version_no)` |
| `pdm_clb_review` | 评审编号/类型/主题、项目与对象快照、参与人、结论/说明、附件、审批实例、状态、关闭人/时间 | 唯一 `(tenant_id, review_no)` |
| `pdm_doc_preview_job` | 文档/版本、预览类型、源文件名/内容类型、目标格式、查看器提示、状态、重试次数、失败原因、产物 | 唯一 `(tenant_id, idempotency_key)` |

---

## 三、规则

1. **交付物只归档已发布版本**：文档版本必须 `RELEASED`；BOM/工艺路线必须 `RELEASED` 且请求版本号与实体一致。重复归档返回 409。
2. **评审状态机**：`DRAFT → CLOSED`。关闭必须选择 `PASSED / CONDITIONAL / REJECTED`；关闭后不可修改、不可重复关闭；删除仅限草稿。
3. **预览幂等**：`idempotencyKey = documentId:versionId:previewType`，重复请求返回既有任务；重试仅对非成功任务生效并累计次数。
4. **预览权限**：创建与内容读取复用图文档 ACL（`versions` 校验 VIEW、`download` 校验 DOWNLOAD），不暴露 `fileId` 与存储路径。
5. **格式判定**：PDF/图片 → `SUCCESS/ORIGINAL`（`PDF`/`IMAGE`）；STL/glTF → `SUCCESS/ORIGINAL`（`STL`/`GLTF`）；Office → `UNSUPPORTED`（需 LibreOffice）；DWG/DXF → `UNSUPPORTED`（按 D-PDM-3 仅下载）；STEP/IGES → `UNSUPPORTED`（需 FreeCAD/OCCT）。
6. **内容读取**：任务成功时返回原文件；若未来转换产物写入 `artifactFileId`，优先返回产物。

---

## 四、接口

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/pdm/collab/deliverable/page` | `sys:pdm:collab:deliverable:list` |
| POST | `/pdm/collab/deliverable` | `sys:pdm:collab:deliverable:add` |
| DELETE | `/pdm/collab/deliverable/{id}` | `sys:pdm:collab:deliverable:delete` |
| GET | `/pdm/collab/review/page`、`/{id}` | `sys:pdm:collab:review:list` |
| POST | `/pdm/collab/review` | `sys:pdm:collab:review:add` |
| PUT/DELETE | `/pdm/collab/review/{id}` | `sys:pdm:collab:review:edit` |
| POST | `/pdm/collab/review/{id}/close` | `sys:pdm:collab:review:close` |
| GET | `/pdm/collab/preview/page`、`/{jobId}/content` | `sys:pdm:collab:preview:list` |
| POST | `/pdm/collab/preview/{documentId}/versions/{versionId}` | `sys:pdm:collab:preview:create` |
| POST | `/pdm/collab/preview/{jobId}/retry` | `sys:pdm:collab:preview:retry` |

写操作均带 `@AuditLog(module="PDM 协同办公")`。

---

## 五、验收

- 草稿文档版本归档被拒绝；同项目同对象同版本重复归档返回 409。
- 评审关闭后修改/重复关闭被拒绝；合法结论写入并记录关闭人/时间。
- 同一文档版本同类型预览重复请求返回同一任务；Office/STEP 任务给出可诊断原因；重试累计次数。
- 预览内容读取越权时返回 404/403，响应不泄漏 `fileId`。
- `mvnw -o -pl backend/whale-apps/whale-pdm -am test` 全绿；前端 lint/build 通过。
