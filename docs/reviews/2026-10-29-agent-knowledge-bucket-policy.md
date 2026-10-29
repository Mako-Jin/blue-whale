# W5.75 补丁检查报告：文件知识桶白名单（default-deny）

> 日期：2026-10-29
> 范围：`whale-agent` 文件知识采集范围
> 关联：REQ-FUNC-015、REQ-SEC-008、Wave 5.53 D8a、[doc-service 设计 §8.2](../design/doc-service.md)、[索引运维 §六](../ops/agent-index-ops.md)

---

## 一、问题

原实现 `FileKnowledgeSyncService` 只按 `excluded-buckets`（默认 `whale-doc`）排除附件桶，其余桶全部入库：

- 普通文件知识块 `permission_code=null`，仅要求 `sys:agent:chat`；
- 个人/部门桶（如 `oa-*`、`whale-im`、自定义桶）中的文件会对同租户所有可问答用户可见；
- 文档侧私有空间页面已通过 `PUBLIC + 非受限` 条件排除，但文件侧没有等价边界。

结论：文件知识池实际是「租户级公开池」，缺少显式的入库白名单，属于安全边界缺口。

## 二、改动

| 项 | 内容 |
|---|---|
| 配置 | 新增 `whale.agent.knowledge.included-buckets` / `WHALE_AGENT_KNOWLEDGE_INCLUDED_BUCKETS`，默认 `bluewhale,whale-archive`，前缀匹配 |
| 判定 | `included(bucket)` 先于 `excluded(bucket)`：白名单为空、桶名为空或未命中 → 不采集（fail-closed）；排除桶仍优先 |
| 台账 | 未命中置 `SKIPPED`、`chunkCount=0`、`lastError=文件桶未纳入知识库白名单`，并删除既有索引块 |
| 接口 | `/agent/knowledge/files/{id}/reindex` 对白名单外文件返回 `false`，重复调用幂等 |
| 文档 | `docs/design/doc-service.md`、`docs/plans/wave5.53-archive-file-knowledge.md`（D8a）、`docs/ops/agent-index-ops.md`、需求登记 |
| 冒烟 | 新增 `scripts/smoke/bw-agent-knowledge-bucket-smoke.ps1`（13 项） |
| 归属人隔离（方案 B） | 文件知识写入索引字段 `owner_id`（上传者）；BM25 与向量两条通道统一按「无归属人（公共知识）或归属人 == 当前用户」过滤；档案知识按密级权限码共享，不写归属人 |

默认值取舍：`bluewhale` 是文件服务默认业务桶，`whale-archive` 是档案快照桶；`whale-doc` 继续由 `excluded-buckets` 排除。`whale-im`、`oa-*` 等桶默认不采集，需显式登记。

## 三、验证

- `mvn -pl whale-services/whale-agent -am test -Dtest=FileKnowledgeSyncServiceTest`
  → `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`（新增 4 项桶策略用例）。
- 归属人隔离（方案 B）：
  - `AgentIndexOwnerTest` 3 项——归属 7 与归属 8 的文件互不可见、无归属上下文仅见公共知识、向量通道同样按归属人过滤；
  - `FileKnowledgeSyncServiceTest` 断言普通文件写入上传者 `7`、档案（含原件归档锁定）写入 `null`；
  - 合并执行 `-Dtest=FileKnowledgeSyncServiceTest,AgentIndexOwnerTest,AgentIndexVectorTest,AgentSearchServiceTest`
    → `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0`。
  - 上下文透传：`/agent/search` 取网关 `X-User-Id`，`/agent/chat` 取会话所属用户；内网 `/agent/tools` 不设归属人，按 fail-closed 仅见公共知识。
- 冒烟脚本 13 项断言（需启动 gateway/auth/file/agent）：
  1. 登录、`/auth/me` 租户上下文；
  2. 上传至白名单外桶 `kb-private-*` → `reindex=false`；
  3. 台账 `SKIPPED`、原因匹配、`kind=file` 检索无命中；
  4. 上传至 `bluewhale` → `reindex=true`、台账 `INDEXED`、`chunkCount>0`、检索命中且 `kind=file`；
  5. 白名单外文件重复重建仍 `false`；
  6. 收尾删除文件元数据。

## 四、风险与遗留

1. **前缀匹配**：白名单按桶名前缀命中，登记 `oa` 会同时放行 `oa-private`。配置时应使用足够精确的前缀，或在后续改为精确匹配 + 正则。
2. **默认桶归属人隔离（方案 B 已实施）**：`bluewhale` 中的个人上传文件写入 `owner_id`，仅上传者本人可检索；档案知识按密级权限码在同租户共享。历史索引缺少 `owner_id`，在回填前仍按公共知识可见，升级后须执行 `POST /agent/knowledge/rebuild`。
3. **历史索引**：升级后需执行一次 `POST /agent/knowledge/rebuild`，否则白名单外桶的旧索引块仍存在，直至对应文件再次变更或重建。
4. **无热更新**：白名单变更需重启 `whale-agent`。

## 五、结论

通过。文件知识采集从「排除少数桶」收敛为「只采集显式白名单桶」，并叠加方案 B 的归属人隔离：白名单内个人文件仅上传者可见，档案按密级权限码共享。与文档侧「仅公开已发布页面」的安全口径一致；单元测试与冒烟断言覆盖核心分支，历史索引清理（白名单外桶 + `owner_id` 回填）已列入上线步骤。
