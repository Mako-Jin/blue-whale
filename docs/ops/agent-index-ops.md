# 知识 Agent 索引运维手册

> 适用服务：`whale-agent`（端口 8090，网关前缀 `/agent/**`）
> 索引实现：内嵌 Apache Lucene 10.2.2（BM25，SmartChinese + Standard 双分析器）
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
| 问答证据数上限 | 8 块 | `whale.agent.qa.evidence-top-k` |

索引元数据落 `bluewhale_agent.agent_index_meta`（单行）：`status`（READY/BUILDING/FAILED/EMPTY）、
`index_version`、`file_count`、`chunk_count`、`last_build_at`、`build_millis`。

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

1. 记录重建前状态：`GET /agent/index/status`（`fileCount` / `chunkCount` / `indexVersion`）。
2. 触发重建：`POST /agent/index/rebuild`。
3. 轮询状态直到 `READY`（BUILDING 时前端徽标每 4 秒刷新）。
4. 抽查检索：`GET /agent/search?q=<关键词>&topK=5`，确认路径与行号合理。
5. 抽查问答：`POST /agent/chat`，确认 `meta.intent` 与 `done.citations` 非空。

> 重建会短暂增加磁盘 IO；全仓约 1200 文件 / 8000 块，本机通常 3–10 秒完成。

---

## 四、故障排查

| 现象 | 排查 | 处置 |
|---|---|---|
| 状态 `EMPTY` | 索引目录为空或首次启动未完成 | 手动触发重建；检查 `index-dir` 权限 |
| 状态 `FAILED` | 查看服务日志 `AgentIndexService` / `KnowledgeIngestService` | 修复后重建；必要时删除索引目录再启动 |
| 检索结果为空 | `q` 过于宽泛或索引未就绪 | 补充服务名/表名/接口路径等关键词；先重建 |
| 问答返回「知识库中未找到」 | 同上，或 scope 限制了类型 | 放开检索范围（`scope` 为空=全部） |
| 问答显示「模型未接入」 | 未配置 `whale.agent.llm.api-key` | 属预期降级（返回检索摘要），配置 Key 后恢复模型生成 |
| 引用被丢弃 | `CitationValidator` 校验路径/行号不存在 | 重建索引；确认文件仍在仓库中 |
| 构建耗时长 | 目录被误纳入（如 node_modules） | 检查排除清单（见 §五） |

---

## 五、安全约束

1. **敏感文件不入索引**：`RepositoryScanner` 排除 `.env*`、`*.pem`、`*.key`、`*credentials*`、`application-prod*`，
   以及 `target/`、`node_modules/`、`dist/`、`build/`、`.git/`、`data/`、`logs/` 等目录。
2. **内容脱敏**：`SensitiveContentMasker` 对 `password|passwd|secret|token|access_key|api_key` 的赋值形式统一替换为 `***`。
3. **运行时事实隔离**：`RUNTIME_SERVICE` 事实需 `sys:agent:runtime:read`；
   无权限时显式查询返回业务码 403（平台 `GlobalExceptionHandler` 统一 HTTP 200 + `code=403`），
   未指定类型时自动剔除该类事实，问答检索同样不透传。
4. **查询审计**：`/agent/chat`、`/agent/search`、`/agent/facts` 标注 `@AuditLog(type=QUERY)`，
   落 `bluewhale_agent.audit_log`（仅元数据，参数经脱敏）。

---

## 六、评测

```powershell
pwsh -File scripts/agent-eval.ps1
```

默认 40 题黄金集（`docs/agent/golden-set.json`），报告输出 `docs/reviews/<日期>-wave5.47-agent-eval.md`；
阈值：总体 ≥80%、结构化 ≥95%、引用有效率 100%，不达标脚本以非零码退出。
