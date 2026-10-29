# 知识 Agent 索引运维手册

> 适用服务：`whale-agent`（端口 8090，网关前缀 `/agent/**`）
> 索引实现：内嵌 Apache Lucene 10.2.2（BM25，SmartChinese + Standard 双分析器；KNN 向量 + RRF 融合）
> 关联规划：[wave5.47-project-agent.md](../plans/wave5.47-project-agent.md)

---

## 一、索引目录与元数据

| 项 | 默认值 | 配置项 |
|---|---|---|
| 仓库根目录 | 启动目录向上探测（含 `backend/pom.xml` 与 `docs/`） | `whale.agent.repo-root` / `WHALE_AGENT_REPO_ROOT` |
| 索引目录 | `${repo-root}/data/agent-index` | `whale.agent.index-dir` / `WHALE_AGENT_INDEX_DIR` |
| 单文件上限 | 2 MB | `whale.agent.ingest.max-file-size` |
| 启动即建索引 | `true` | `whale.agent.ingest.build-on-startup` |
| 定时增量扫描 | 1800 秒（0 关闭） | `whale.agent.ingest.scan-interval-seconds` |
| 检索默认/最大 TopK | 8 / 50 | `whale.agent.search.default-top-k` / `max-top-k` |
| 向量通道开关 | `true` | `whale.agent.search.vector-enabled` / `WHALE_AGENT_SEARCH_VECTOR_ENABLED` |
| 双通道候选数 | 50 | `whale.agent.search.candidate-top-k` |
| RRF 常数 k / 双通道权重 | 60 / 1.0 与 1.0 | `whale.agent.search.rrf-k` / `bm25-weight` / `vector-weight` |
| embedding 提供方 | `auto`（有 Key 走 OpenAI 兼容，否则本地哈希） | `whale.agent.embedding.provider` / `WHALE_AGENT_EMBEDDING_PROVIDER`（`hash\|openai\|auto\|none`） |
| embedding 地址 / Key / 模型 | 空（回退 `llm.*`）/ 空 / `text-embedding-3-small` | `whale.agent.embedding.base-url` / `api-key` / `model` |
| embedding 维度 | `0`（用提供方默认，哈希默认 256） | `whale.agent.embedding.dimension` |
| embedding 超时 / 批量 | 20000 ms / 16 | `whale.agent.embedding.timeout-millis` / `batch-size` |
| 问答证据数上限 | 8 块 | `whale.agent.qa.evidence-top-k` |
| 自主工具编排 | `true`（需模型 Key） | `whale.agent.qa.agentic-enabled` / `WHALE_AGENT_QA_AGENTIC_ENABLED` |
| 编排最大工具轮数 | 4（代码硬上限 12） | `whale.agent.qa.max-tool-steps` / `WHALE_AGENT_QA_MAX_TOOL_STEPS` |

索引元数据落 `bluewhale_agent.agent_index_meta`（单行）：`status`（READY/BUILDING/FAILED/EMPTY）、
`index_version`、`file_count`、`chunk_count`、`last_build_at`、`build_millis`、`dimension`（索引实际向量维度，BM25-only 为 0）。

向量通道说明：

- 每个知识块写入 `KnnFloatVectorField`（余弦）以及 `vector_dim` / `embed_sig`（`提供方:维度`，如 `hash:256`）存储字段。
- 检索为 **BM25 topN ∪ 向量 topN → RRF 融合**（`weight/(k+rank)`，按块 id 去重）；任一路为空时直接返回另一路。
- 切换 embedding 提供方/维度后，下一次定时增量扫描检测到签名不一致会**自动升级为全量重建**；
  同维度换模型不会自动重建，需手工 `POST /agent/index/rebuild`。
- `GET /agent/index/status` 的 `dimension` / `embedding` 字段可直接确认当前索引的向量状态。

---

## 二、常用接口

| 操作 | 接口 | 权限 |
|---|---|---|
| 查询索引状态 | `GET /agent/index/status` | 登录即可 |
| 全量重建索引 | `POST /agent/index/rebuild` | `sys:agent:index:rebuild` |
| 纯检索调试 | `GET /agent/search?q=...&topK=8&kind=code,doc` | `sys:agent:search` |
| 事实查询 | `GET /agent/facts?type=PORT&key=whale-agent` | `sys:agent:search`（运行时事实另需 `sys:agent:runtime:read`） |
| 流式问答 | `POST /agent/chat`（SSE） | `sys:agent:chat` |

示例（直连服务，便于排障；生产走网关 `http://<host>:8080`）：

```powershell
curl.exe -s -H "X-User-Id: 1" -H "X-Permission-Codes: sys:agent:index:rebuild" `
  -X POST "http://localhost:8090/agent/index/rebuild"

curl.exe -s -H "X-User-Id: 1" -H "X-Permission-Codes: sys:agent:search" `
  "http://localhost:8090/agent/search?q=%E7%AB%AF%E5%8F%A3&topK=5"
```

重建为异步受理（已有任务在执行时返回 `false`），完成后状态回到 `READY`。

---

## 三、重建与校验步骤

1. 记录重建前状态：`GET /agent/index/status`（`fileCount` / `chunkCount` / `indexVersion` / `dimension` / `embedding`）。
2. 触发重建：`POST /agent/index/rebuild`。
3. 轮询状态直到 `READY`（BUILDING 时前端徽标每 4 秒刷新）。
4. 抽查检索：`GET /agent/search?q=<关键词>&topK=5`，确认路径与行号合理。
5. 抽查问答：`POST /agent/chat`，确认 `meta.intent` 与 `done.citations` 非空。

> 重建会短暂增加磁盘 IO；全仓约 1500 文件 / 9300 块（含 256 维向量），本机通常 9–13 秒完成。

---

## 四、故障排查

| 现象 | 排查 | 处置 |
|---|---|---|
| 状态 `EMPTY` | 索引目录为空或首次启动未完成 | 手动触发重建；检查 `index-dir` 权限 |
| 状态 `FAILED` | 查看服务日志 `AgentIndexService` / `KnowledgeIngestService` | 修复后重建；必要时删除索引目录再启动 |
| 检索结果为空 | `q` 过于宽泛或索引未就绪 | 补充服务名/表名/接口路径等关键词；先重建 |
| 业务文件检索不到 | 文件桶未命中知识库白名单 | 核对 `WHALE_AGENT_KNOWLEDGE_INCLUDED_BUCKETS`；台账 `lastError=文件桶未纳入知识库白名单` 即为此因，见 §六 |
| 问答返回「知识库中未找到」 | 同上，或 scope 限制了类型 | 放开检索范围（`scope` 为空=全部） |
| 问答显示「模型未接入」 | 未配置 `whale.agent.llm.api-key` | 属预期降级（返回检索摘要），配置 Key 后恢复模型生成 |
| 问答没有调用工具 | 模型未配置，或 `agentic-enabled=false` | 自主编排仅在模型可用时启用；确认 Key 与 `whale.agent.qa.agentic-enabled` |
| 回答只有「已检索到的资料」 | 编排中模型最终返回 `null` 或空文本 | 检查模型网关连通性与超时；`degraded=true` 时属预期兜底 |
| 引用被丢弃 | `CitationValidator` 校验路径/行号不存在 | 重建索引；确认文件仍在仓库中 |
| 构建耗时长 | 目录被误纳入（如 node_modules、仓库根 `tools/`） | 检查排除清单（见 §五） |
| 状态 `dimension=0` / `embedding=none` | 向量通道被关闭，或索引为 BM25-only 旧索引 | 检查 `whale.agent.embedding.enabled` / `provider`；重建索引补齐向量 |
| 检索明显退化、日志出现「向量检索失败」 | 维度不一致或查询向量化异常 | 对比 `index/status` 的 `dimension` 与 embedding 实际维度，重建索引 |
| 远程 embedding 一直未启用 | `provider=openai` 但 `api-key` 为空（日志有降级 warn） | 配置 `WHALE_AGENT_EMBEDDING_API_KEY`（或复用 `llm.api-key`）后重启 |

---

## 五、安全约束

1. **敏感文件不入索引**：`RepositoryScanner` 排除 `.env*`、`*.pem`、`*.key`、`*credentials*`、`application-prod*`，
   以及 `target/`、`node_modules/`、`dist/`、`build/`、`.git/`、`data/`、`logs/` 等目录；
   仓库根下的 `tools/`、`work/`、`.workbuddy/`（本地工具/临时目录，不入仓）也整体排除。
2. **内容脱敏**：`SensitiveContentMasker` 对 `password|passwd|secret|token|access_key|api_key` 的赋值形式统一替换为 `***`。
3. **运行时事实隔离**：`RUNTIME_SERVICE` 事实需 `sys:agent:runtime:read`；
   无权限时显式查询返回业务码 403（平台 `GlobalExceptionHandler` 统一 HTTP 200 + `code=403`），
   未指定类型时自动剔除该类事实，问答检索同样不透传。
4. **查询审计**：`/agent/chat`、`/agent/search`、`/agent/facts` 标注 `@AuditLog(type=QUERY)`，
   落 `bluewhale_agent.audit_log`（仅元数据，参数经脱敏）。

---

## 六、文件知识库桶白名单

文件知识采集采用 **default-deny 白名单**：只有命中 `whale.agent.knowledge.included-buckets`
（`WHALE_AGENT_KNOWLEDGE_INCLUDED_BUCKETS`，逗号分隔、桶名前缀匹配）且不在
`excluded-buckets`（`WHALE_AGENT_KNOWLEDGE_EXCLUDED_BUCKETS`）中的桶才会入库。

| 项 | 默认值 | 配置项 |
|---|---|---|
| 入库桶白名单 | `bluewhale,whale-archive` | `whale.agent.knowledge.included-buckets` / `WHALE_AGENT_KNOWLEDGE_INCLUDED_BUCKETS` |
| 排除桶（优先） | `whale-doc` | `whale.agent.knowledge.excluded-buckets` / `WHALE_AGENT_KNOWLEDGE_EXCLUDED_BUCKETS` |
| 单文件上限 / 抽取上限 | 20 MB / 100 万字符 | `whale.agent.knowledge.max-file-size` / `max-chars` |

规则与处置：

1. 白名单为空、桶名为空或未命中时，一律 **不采集**（fail-closed）；对应台账置 `SKIPPED`，
   `lastError=文件桶未纳入知识库白名单`，并删除既有索引块。
2. 排除桶优先于白名单：即使已加入白名单，命中 `excluded-buckets` 仍不入库。
3. 新增知识库桶需显式加入白名单（环境变量）并重启服务（无热更新），随后执行
   `POST /agent/knowledge/rebuild` 重建文件知识。
4. 排查：`GET /agent/knowledge/files/page?status=SKIPPED` 查看原因；`GET /agent/search?q=<关键词>&kind=file`
   确认白名单外内容不可见。
5. 冒烟：`pwsh -File scripts/smoke/bw-agent-knowledge-bucket-smoke.ps1`（白名单外跳过、白名单内可检索、重复重建幂等）。

---

## 七、评测

```powershell
pwsh -File scripts/agent-eval.ps1
```

默认 40 题黄金集（`docs/agent/golden-set.json`），报告输出 `docs/reviews/<日期>-wave5.47-agent-eval.md`；
阈值：总体 ≥80%、结构化 ≥95%、引用有效率 100%，不达标脚本以非零码退出。
