# Wave 5.61 遗留：合同归档快照衔接 whale-archive（检查报告）

> 日期：2026-10-26　模块：`whale-archive`（8093）+ `whale-contract`（9006）
> 目标：把设计稿 M3 遗留「归档仍为合同侧状态流转、未新增 archive 内部快照接口」补齐为端到端可验证能力。

## 一、范围

- archive 新增内网**快照入档**写接口：业务服务在单据终态时推送不可变快照，档案服务上传快照文件、计算 SHA-256、生成「待整理」条目并记同步台账。
- contract 归档时调用该接口，把返回的档案条目ID回填 `ct_contract.archive_item_id`。
- 不含：档案人员「确认归档」动作（仍由既有 `/archive/item/{id}/confirm` 分配档号）、前端改动、自动归档规则调整。

## 二、方案要点

| 关注点 | 处置 |
|---|---|
| 复用既有链路 | 入档复用 `ArchiveItemStore`（条目/文件/台账同事务）+ `archive_source_record` 台账 + 既有快照桶与文件服务，仅新增「来源为业务推送」的入口 |
| 幂等键 | `(source_type, source_id)` 唯一索引；重复推送直接返回既有条目 `created=false`，已归档条目不因重复推送被改写 |
| 快照不可变 | 业务侧生成 JSON 文本（合同核心字段 + 相对方清单），档案侧上传后以 `SNAPSHOT` 角色挂到条目；同时可选挂 `ORIGINAL` 原件引用（合同正文 PDF） |
| 摘要校验 | 入档时对快照字节计算 SHA-256 存 `archive_item.snapshot_hash`，可回读文件复算比对 |
| 条目状态 | 落为 `0 待整理`，`archive_no` 为空；与工作流同步口径一致，档号在`确认归档`时统一分配 |
| 白名单与限额 | 仅接受 `CONTRACT`；快照 ≤1MB；标题截断 255；密级缺省「内部」 |
| 一致性 | contract 侧「先推送再落状态」，推送失败抛 `BizException` → 事务回滚、合同停留 `EFFECTIVE` 可重试；因档案侧幂等，重试不会产生重复条目 |
| 网络边界 | 服务间直连 `http://127.0.0.1:8093`，不经网关；`/archive/internal/**` 仍由网关 `internal-deny` 403 |

## 三、变更点

### archive

- 新增 [`ArchiveIngestRequest`](../../backend/whale-services/whale-archive/src/main/java/com/bluewhale/archive/dto/ArchiveIngestRequest.java)（Bean Validation：来源/标题/快照必填，密级 1–3，各字段长度上限）。
- 新增 [`ArchiveIngestResultVO`](../../backend/whale-services/whale-archive/src/main/java/com/bluewhale/archive/vo/ArchiveIngestResultVO.java)（ID 统一字符串返回）。
- 新增 [`ArchiveIngestService`](../../backend/whale-services/whale-archive/src/main/java/com/bluewhale/archive/service/ArchiveIngestService.java)：白名单/限额/幂等/上传/落库/台账。
- 修改 [`ArchiveInternalController`](../../backend/whale-services/whale-archive/src/main/java/com/bluewhale/archive/controller/ArchiveInternalController.java)：新增 `POST /archive/internal/items`。
- 修改 [`ArchiveConstants`](../../backend/whale-services/whale-archive/src/main/java/com/bluewhale/archive/support/ArchiveConstants.java)：新增 `SOURCE_CONTRACT`。

### contract

- 新增 [`ContractArchiveProperties`](../../backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/config/ContractArchiveProperties.java)：`enabled/base-url/connect-timeout-millis/read-timeout-millis/security-level`。
- 新增 [`ArchiveInternalClient`](../../backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/client/ArchiveInternalClient.java)：构造入档请求体（含相对方快照）、校验统一响应信封、解析 `data.itemId`；透传 `X-Tenant-Id`。
- 修改 [`ContractServiceImpl`](../../backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/service/impl/ContractServiceImpl.java)：`archive()` 先置 `ARCHIVED` 快照态 → 推送入档 → 回填 `archiveItemId` → 落库。
- 修改 [`application.yml`](../../backend/whale-apps/whale-contract/src/main/resources/application.yml)：`whale.contract.archive.*`，支持 `CONTRACT_ARCHIVE_URL` 等环境变量覆盖。
- 新增 [`ArchiveInternalClientTest`](../../backend/whale-apps/whale-contract/src/test/java/com/bluewhale/contract/client/ArchiveInternalClientTest.java)：3 用例（请求体字段/快照内容与相对方/无原件时省略原件引用）。
- 冒烟脚本 [`bw-contract-m3-smoke.ps1`](../../scripts/smoke/bw-contract-m3-smoke.ps1)：`evidenceNo` 改为每轮随机后缀（原先固定 `EV-M3-*-001` 撞 `uk_tenant_evidence`，脚本不可重跑）。

## 四、验证

### 4.1 构建与单测

- `mvn -o -pl backend/whale-services/whale-archive,backend/whale-apps/whale-contract -am package` **BUILD SUCCESS**，Checkstyle 0。
- contract 单测 **7/7**（原回调补偿 4 + 本次 3）；archive 既有单测未回归。

### 4.2 端到端（实机，M3 冒烟脚本全量 00–27 通过）

- 冒烟末段：`25.effective` → `26.archive` 通过，证明归档链路未破坏原流程。
- `ct_contract`：`HT-CG-2026-000018` → `status=ARCHIVED`、`archive_item_id=2114637203531321346`（回填成功）。
- `archive_item`：`source_type=CONTRACT`、`source_id` 为合同ID、`definition_code=CONTRACT`、`business_key=HT-CG-2026-000018`、`item_status=0`（待整理）、`year=2026`、`security_level=2`、`archive_no=NULL`、`snapshot_file_id=2114637203329998849`、`snapshot_hash=766713e2…1648`。
- `archive_file`：`SNAPSHOT`（`CONTRACT-HT-CG-2026-000018.json`，`application/json`，sort 0）+ `ORIGINAL`（`HT-CG-2026-000018.pdf`，`application/pdf`，sort 1）。
- `archive_source_record`：`sync_status=1`、`retry_count=0`。
- 幂等：同一 `(CONTRACT, 合同ID)` 再次直连 `POST /archive/internal/items` → `code=0`、`itemId` 相同、`created=false`，无新增条目/台账。
- 快照回读：`GET http://127.0.0.1:8085/file/internal/{snapshotFileId}/content` 复算 SHA-256 = `766713e2…1648`，与 `snapshot_hash` 一致。
- 档案中心可见：经网关 `GET /archive/item/page` `code=0`、`total=3`，首条为本次合同档案（`sourceType=CONTRACT`）。

## 五、结论与遗留

- 「合同归档 → 档案入档」已打通：合同侧只多一次内网调用与一个ID回填，档案侧复用既有落库与台账链路，不改变既有工作流同步行为。
- 遗留：
  1. 条目停在「待整理」，档号/保管期限/门类需档案人员在确认归档时补全（与工作流同步口径一致）；如需合同走自动归档，后续可基于 `archive_rule` 增加 `CONTRACT` 规则；
  2. 归档失败当前为**同步失败可重试**语义（事务回滚），未做异步补偿队列；若档案服务长时间不可用，合同归档动作会失败并提示，业务可稍后重试；
  3. 原件引用未做文件锁定（`/file/internal/{id}/lock`），与 Wave 5.53 M2「锁定拦截删除」口径存在差异，需在档案侧确认归档时补锁定。
