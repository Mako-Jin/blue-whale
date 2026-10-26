# Wave 5.61 M2「印章与本地盖章」实施计划

## Context

M1 已交付合同主流程（whale-contract 9006、4 表、11 接口、权限 215–222、菜单 28、前端 8109）。本计划承接设计稿 [wave5.61-contract-esign.md](../../docs/plans/wave5.61-contract-esign.md) 第十节 M2：

- 新增 `ct_seal` / `ct_seal_grant` / `ct_sign_task` / `ct_sign_record` 4 表；
- 印章 CRUD、用章授权、发起签署、本地签署（PDFBox 视觉盖章 + 可选 BouncyCastle 数字签名）、拒签、证据链、验签、作废；
- 权限 223–227、229–231 + 菜单 29–30；前端新增印章管理与签署中心两个页面；
- 验收：未授权用章 403 + 审计；签章件哈希与 `signed_sha256` 一致、篡改验签失败；骑缝章页数与配置一致；重复签署幂等；拒签记录原因且合同停在 `SIGNING`。

已确认环境：gateway 8080 / auth 8081 / workflow 8083 / file 8085 / contract 9006 / MySQL / Redis / Nacos 全部在线；本地 Maven 仓库有 PDFBox 2.0.31、bcpkix/bcprov-jdk18on 1.77；whale-file 内网接口 `GET /file/internal/{id}/content`、`POST /file/internal/upload` 可免权限调用（网关已屏蔽外部访问）。

## 后端实现

### 1. 依赖与配置

- `backend/whale-apps/whale-contract/pom.xml`：新增 `org.apache.pdfbox:pdfbox:2.0.31`、`org.bouncycastle:bcpkix-jdk18on:1.77`（bcprov 传递引入）。
- `application.yml` 新增：
  ```yaml
  whale:
    contract:
      file:
        base-url: ${CONTRACT_FILE_URL:http://127.0.0.1:8085}
        connect-timeout-millis: 1000
        read-timeout-millis: 5000
      esign:
        p12-path: ${CONTRACT_ESIGN_P12_PATH:}
        p12-password: ${CONTRACT_ESIGN_P12_PASSWORD:}
        tsa-url: ${CONTRACT_ESIGN_TSA_URL:}
  ```
  P12 未配置时 `algorithm=NONE`（仅视觉盖章 + 哈希），符合设计稿 PoC 约束。

### 2. DDL（`src/main/resources/db/init.sql` 追加，并实机执行）

4 张表均含 `id/tenant_id/create_time/update_time/deleted` 公共字段：

| 表 | 关键字段 | 索引 |
|---|---|---|
| `ct_seal` | `name`、`seal_type`(COMPANY/CONTRACT/LEGAL/FINANCE)、`image_file_id`、`keeper_user_id`、`valid_from/to`、`status` | `(tenant_id, status)` |
| `ct_seal_grant` | `seal_id`、`grantee_user_id`、`contract_category`(NULL=全类别)、`max_amount`(NULL=不限)、`valid_from/to`、`status`、`granted_by` | `(seal_id, grantee_user_id)` |
| `ct_sign_task` | `contract_id`、`version_id`、`seq_no`、`signer_user_id`、`signer_party`、`signer_name`、`provider`(LOCAL)、`provider_task_id`、`status`(PENDING/SIGNING/SIGNED/REFUSED/EXPIRED)、`signed_at`、`signed_file_id`、`refuse_reason` | `(contract_id, seq_no)`、`(provider, provider_task_id)` |
| `ct_sign_record` | `contract_id`、`sign_task_id`、`signer_user_id`、`seal_id`、`cert_serial`、`signed_at`、`original_sha256`、`signed_sha256`、`algorithm`、`evidence_no`、`chain_hash`、`client_ip` | `(contract_id, signed_at)`、`(evidence_no)` |

种子数据：1 枚测试印章（`image_file_id` 允许为 NULL，签章时后端自动生成内置蓝色圆形印章图）+ admin 全类别授权，便于冒烟。

### 3. 实体 / Mapper / DTO / VO

- entity：`CtSeal`、`CtSealGrant`、`CtSignTask`、`CtSignRecord`（继承 `BaseEntity`）。
- mapper：4 个 `BaseMapper` 接口，放在现有 `mapper/` 包。
- dto：
  - `SealSaveRequest`(name/sealType/imageFileId/keeperUserId/validFrom/validTo/status)
  - `SealGrantRequest`(granteeUserId/contractCategory/maxAmount/validFrom/validTo)
  - `SignStartRequest`(tasks: `SignTaskCreateRequest`{signerUserId, signerParty, signerName}，可空→按相对方生成)
  - `SignExecuteRequest`(sealId/page/x/y/width/height/rotation/crossPage)
  - `SignRefuseRequest`(reason，必填)
- vo：`SealVO`、`SealGrantVO`、`SignTaskVO`、`SignRecordVO`、`EvidenceVO`（contract + tasks + records + chainValid）、`VerifyResultVO`（hashMatch/chainValid/signatureValid/algorithm/details）。

### 4. esign 能力包 `com.bluewhale.contract.esign`

- `FileClient`：基于 `RestClient` 调 whale-file 内网接口，`download(Long fileId)` 返回 `byte[]`，`upload(String filename, String contentType, byte[] content)` 返回 fileId；不透传权限码。
- `PdfStampService`（PDFBox 2.0.31）：
  - 无正文文件时生成 2 页占位 PDF（标题/编号/相对方/金额）；
  - 按 `page/x/y/width/height/rotation` 叠加印章 PNG（内置自动生成兜底印章）；
  - `crossPage=true` 时按 PDF 页数均分印章图宽度，绘制在每页右侧页面切边区，返回实际盖章页数；
  - 输出 `byte[]`。
- `SignatureService`（BouncyCastle）：P12 存在时对字节做 CMS/PKCS#7 detached 签名，返回 `signedBytes`、`certSerial`、`algorithm`（`SHA256withRSA`）；未配置时 `algorithm=NONE`。
- `EsignHashUtils`：SHA-256 hex；`chain_hash = sha256(prevChain || contractId || versionId || signerId || signedAt || originalSha256 || signedSha256 || evidenceNo)`。

### 5. 服务与接口

新增 `service/SealService` + `impl/SealServiceImpl`、`service/SignService` + `impl/SignServiceImpl`，以及 `controller/ContractSealController`、`controller/ContractSignController`。

接口与权限：

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/contract/seal/page` | `sys:contract:seal:list`(223) |
| POST | `/contract/seal` | `sys:contract:seal:add`(224) |
| PUT | `/contract/seal/{id}` | `sys:contract:seal:edit`(225) |
| DELETE | `/contract/seal/{id}` | `sys:contract:seal:delete`(226) |
| POST | `/contract/seal/{id}/grant` | `sys:contract:seal:grant`(227) |
| GET | `/contract/seal/{id}/grants` | `sys:contract:seal:list` |
| POST | `/contract/seal/upload-image`（multipart） | `sys:contract:seal:add` |
| POST | `/contract/{id}/sign/start` | `sys:contract:sign:start`(228) |
| GET | `/contract/{id}/sign/tasks` | `sys:contract:list` |
| GET | `/contract/sign/pending` | `sys:contract:sign:execute`(229) |
| POST | `/contract/sign-task/{id}/sign` | `sys:contract:sign:execute` |
| POST | `/contract/sign-task/{id}/refuse` | `sys:contract:sign:execute` |
| GET | `/contract/{id}/evidence` | `sys:contract:evidence:read`(230) |
| POST | `/contract/{id}/verify` | `sys:contract:evidence:read` |
| POST | `/contract/{id}/void` | `sys:contract:sign:void`(231) |

核心规则：

1. **发起签署**：合同必须 `APPROVED`（或 `SIGNING` 追加任务）；按请求 tasks 或相对方 `signer_user_id` 生成 `ct_sign_task`，`seq_no` 递增；合同转 `SIGNING`；重复调用同 signer 幂等跳过。
2. **用章授权**：签署时校验印章 `ENABLED` + 印章有效期 + `ct_seal_grant`（grantee=当前操作人、类别匹配或全类别、合同金额 ≤ max_amount、授权有效期、status=ENABLED）；任一不满足抛 `BizException(ErrorCode.FORBIDDEN, "...")`，并写审计日志（`@AuditLog` 配合失败记录；若既有切面只记成功，则在服务内显式记录 `api_log` 失败原因，沿用 `ApiLogFilter` 语义）。
3. **本地签署**：任务状态 `PENDING/SIGNING`；操作人须等于 `signer_user_id`；下载正文 → 视觉盖章 → 可选数字签名 → 上传新文件 → 写 `ct_sign_record`（含 `original_sha256/signed_sha256/chain_hash/evidence_no`）→ 任务 `SIGNED`；同一任务已 `SIGNED` 直接返回既有记录（幂等，唯一约束 `(tenant_id, sign_task_id)` 兜底）。
4. **全部签完**：合同转 `SIGNED`，并同步 `ct_contract.file_id` 为最后一份签章件。
5. **拒签**：任务 `REFUSED` + 原因；合同保持 `SIGNING`。
6. **验签**：重新下载 `signed_file_id`，比对 SHA-256 与 `signed_sha256`；按签署时间顺序重算 `chain_hash` 连续性；P12 启用时校验 CMS 签名；任一失败返回 `signatureValid=false` + 明细（不抛异常，便于审计展示）。
7. **作废**：合同 `SIGNING/SIGNED/EFFECTIVE` → `VOID`，未完成任务置 `EXPIRED`，保留全部记录。
8. **租户隔离**：全部查询走 MyBatis-Plus 租户插件 + 实体 `tenant_id`（沿用 M1 行为）。

审计：seal 增改删/授权、sign start/execute/refuse/void/verify 全部加 `@AuditLog`，target 分别为 `ct_seal`、`ct_contract`。

### 6. 权限与菜单迁移

新增 `backend/whale-services/whale-auth/src/main/resources/db/migration-5.62-contract-esign.sql`（幂等 `INSERT IGNORE`）：

- 权限 223 `sys:contract:seal:list`、224 `add`、225 `edit`、226 `delete`、227 `grant`、228 `sys:contract:sign:start`、229 `sys:contract:sign:execute`、230 `sys:contract:evidence:read`、231 `sys:contract:sign:void`；
- 菜单 29 印章管理 `/contract/seal`、30 签署中心 `/contract/sign`（component `contract/seal`、`contract/sign`，parent 0，visible 1）。

实机执行到 `bluewhale_auth`，并核对 `sys_permission` 215–231、`sys_menu` 28–30。

## 前端实现（whale-contract 8109）

- `src/api/esign.ts`：印章/授权/签署任务/证据/验签接口与类型。
- `src/pages/SealList.tsx`：印章表格（名称/类型/保管人/有效期/状态）+ 新建/编辑弹窗 + 上传印章图（`/contract/seal/upload-image`）+ 授权弹窗（人/类别/金额上限/有效期）+ 授权记录抽屉。
- `src/pages/SignCenter.tsx`：待签署任务列表（按合同 SIGNING 状态），签署弹窗含印章选择、页码、坐标、尺寸、旋转、骑缝开关；拒签弹窗（原因必填）。
- `src/pages/ContractDetail.tsx`：APPROVED 状态显示「发起签署」按钮；SIGNING/SIGNED 显示签署任务 Tab、证据链 Tab 与「验签」「作废」按钮。
- `src/App.tsx`：新增 `/seal`、`/sign` 路由；保持 `/:id` 详情路由。
- 基座 qiankun 已注册 `/contract`，无需改动基座路由；菜单由后端菜单 29/30 驱动。

## 验证

1. `mvn -o -pl whale-apps/whale-contract -am package -DskipTests`（`JAVA_HOME=java21`，`dangerouslyDisableSandbox:true`）通过。
2. 执行 4 表 DDL + `migration-5.62-contract-esign.sql`；重启 whale-contract 9006（沿用 M1 env：`MYSQL_PASSWORD`、Redis、Nacos、`CONTRACT_APPROVAL_DEFINITION_ID`）。
3. 编写 `scripts/smoke/bw-contract-m2-smoke.ps1` 并跑通：
   - 造合同 → 提交审批 → admin 审批通过 → 发起签署（admin 任务）；
   - 未授权用户用章 → `code=403`，审计日志存在失败记录；
   - 合法签署 → 返回 `signed_sha256`，下载签章件哈希一致；
   - 篡改签章件（改数据库 `signed_sha256` 为错误值）→ `verify` 返回 `signatureValid=false`；
   - 骑缝章：2 页 PDF + `crossPage=true` → 返回 `stampedPages=2`；
   - 同一任务重复签署 → 记录数仍为 1（幂等）；
   - 第二个任务拒签 → 有原因，合同仍为 `SIGNING`。
4. 前端 `pnpm --filter whale-contract build`（tsc + vite）通过。
5. 文档：`docs/reviews/2026-10-26-p3-wave5.61-contract-m2.md`、追加 `docs/devlog/2026-10-26.md`、需求台账序号 19 改「M2 完成；M3 待启动」、roadmap 与 task-breakdown 勾选。
6. 提交并推送 Gitee origin；任务开始前尝试一次 GitHub 镜像（失败跳过，禁用裸 `--force`）。

## 关键文件清单

- 后端新增：`entity/CtSeal.java`、`CtSealGrant.java`、`CtSignTask.java`、`CtSignRecord.java`；4 个 mapper；`dto/SealSaveRequest.java` 等 5 个；`vo/SealVO.java` 等 6 个；`esign/FileClient.java`、`PdfStampService.java`、`SignatureService.java`、`EsignHashUtils.java`；`service/SealService.java`、`SignService.java` + impl；`controller/ContractSealController.java`、`ContractSignController.java`。
- 后端修改：`pom.xml`、`application.yml`、`resources/db/init.sql`。
- 迁移：`whale-auth/src/main/resources/db/migration-5.62-contract-esign.sql`。
- 前端新增：`src/api/esign.ts`、`src/pages/SealList.tsx`、`src/pages/SignCenter.tsx`；修改 `src/App.tsx`、`src/pages/ContractDetail.tsx`、`src/utils.ts`（印章/任务状态文案）。
- 脚本：`scripts/smoke/bw-contract-m2-smoke.ps1`。
- 文档：`docs/reviews/2026-10-26-p3-wave5.61-contract-m2.md`、`docs/devlog/2026-10-26.md`、`docs/requirements/register.md`、`docs/plans/roadmap.md`、`docs/plans/task-breakdown.md`、`docs/plans/wave5.61-contract-esign.md`。

## 风险与处置

- PDFBox 2.x 与 Spring Boot 4 无冲突；签名采用 detached CMS 写入 PDF 附件的简化实现，P12 未配置时跳过数字签名（PoC 语义，验收以哈希 + 视觉章为准）。
- `BizException` 到 HTTP 状态码的映射需沿用既有 `GlobalExceptionHandler`；若其对 `FORBIDDEN` 返回 HTTP 200 + `code=403`，冒烟按 `code` 断言，并额外核对审计记录。
- 骑缝章实现为「按页均分印章图并绘制在页面右侧切边」，页数以 PDF 实际页数为准，满足验收 ③。
