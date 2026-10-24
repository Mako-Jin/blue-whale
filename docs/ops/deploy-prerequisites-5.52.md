# Wave 5.52 部署前置检查单（2026-10-24）

> 口径：规划 §11.3「部署前置」逐项落地与实机核验记录。本机为原生进程环境（MySQL 8.0.45:3306，无 Docker）。
> 结论：**三库建库/增量脚本、`agent_operation` 列扩展、IM 幂等键与回填、只读账号与白名单视图均已就位**；本轮补齐了 2 个未入仓的权限种子并回填权限展示属性。

---

## 一、执行项与核验结果

| # | 项 | 脚本 / 位置 | 执行方式 | 核验结果 |
|---|---|---|---|---|
| 1 | `bluewhale_im` 建库与 DDL | [whale-im/db/init.sql](../../backend/whale-services/whale-im/src/main/resources/db/init.sql) | `Create Table If Not Exists`，可重复执行 | 6 张表齐全：`im_conversation`、`im_conversation_member`、`im_message`、`im_agent_binding`、`im_agent_run`、`audit_log` |
| 2 | IM 既有库增量（幂等键 + 评价） | [migration-5.52-im-loose-ends.sql](../../backend/whale-services/whale-im/src/main/resources/db/migration-5.52-im-loose-ends.sql) | 仅对旧库执行一次（MySQL 8 不支持 `ADD COLUMN IF NOT EXISTS`） | `im_conversation.pair_key` + `uk_pair_key` 存在；`im_agent_run.feedback` 存在；`SINGLE/BOT` 的 `pair_key` 空值为 **0** |
| 3 | `bluewhale_agent` 建库与 DDL | [whale-agent/db/init.sql](../../backend/whale-services/whale-agent/src/main/resources/db/init.sql) | 新库直接执行；**既有库禁止整份重跑**（含 `DROP TABLE`） | 7 张表齐全；`agent_operation` 具备 `target / confirmed_by / result_code / elapsed_ms` 四列（既有库已按列 `ALTER` 补齐） |
| 4 | `whale-auth` 增量权限种子 | [migration-5.52.sql](../../backend/whale-services/whale-auth/src/main/resources/db/migration-5.52.sql) | `INSERT IGNORE` + 仅补空属性，幂等 | 4 个权限码均存在且 `path/method` 已回填：`sys:agent:chat`、`sys:im:chat`、`sys:ai:data:read`、`sys:ai:ops:form` |
| 5 | 只读账号 `whale_ai_ro` | [data-query-ro.sql](../../backend/whale-services/whale-agent/src/main/resources/db/data-query-ro.sql) | 以 root 执行 | 已收紧为 `@'localhost'` + `@'127.0.0.1'`，删除 `@'%'`；口令已轮换为 24 位随机强口令；**未授予库级权限**，仅两个视图 `SELECT`。实测：视图查询成功，查询基表 `agent_fact` 报 `ERROR 1142` |
| 6 | 白名单视图 | 同上 | `CREATE OR REPLACE VIEW` | 两视图存在；T19 实机 14 例已验证「非白名单表/写 SQL/多语句 → 403」；本轮再次经 `/agent/tools/data-query` 实测返回 5 行聚合结果（20ms） |

### 1.1 本轮修正的脚本缺口

- `sys:ai:data:read`（只读数据通道，T14）与 `sys:ai:ops:form`（确认令牌执行权限，T15）此前**仅存在于运行库**，未进入 `init.sql` / `migration-5.52.sql`，全新部署会缺权限行；本轮按段位约定补为 `104` / `105`。
- 运行库中 4 个 5.52 权限码的 `path/method` 为空（手工登记导致），已在迁移脚本中以「仅当为空才回填」的方式补齐，不覆盖既有值。

---

## 二、执行命令（目标环境按序执行）

```sql
-- 1) IM（新库）
SOURCE backend/whale-services/whale-im/src/main/resources/db/init.sql;
-- 1b) IM（既有库，仅执行一次）
SOURCE backend/whale-services/whale-im/src/main/resources/db/migration-5.52-im-loose-ends.sql;

-- 2) Agent（新库；既有库只按列 ALTER，勿整份重跑）
SOURCE backend/whale-services/whale-agent/src/main/resources/db/init.sql;
-- 2b) Agent 只读账号与视图（root 执行，先改强口令与主机范围）
SOURCE backend/whale-services/whale-agent/src/main/resources/db/data-query-ro.sql;

-- 3) Auth 权限种子（幂等）
SOURCE backend/whale-services/whale-auth/src/main/resources/db/migration-5.52.sql;
```

校验 SQL：

```sql
SELECT id, perm_code, path, method, status
FROM bluewhale_auth.sys_permission
WHERE perm_code IN ('sys:agent:chat','sys:im:chat','sys:ai:data:read','sys:ai:ops:form');

SELECT COUNT(*) FROM information_schema.columns
WHERE table_schema='bluewhale_agent' AND table_name='agent_operation'
  AND column_name IN ('target','confirmed_by','result_code','elapsed_ms');  -- 期望 4

SELECT COUNT(*) FROM bluewhale_im.im_conversation
WHERE deleted=0 AND type IN ('SINGLE','BOT') AND pair_key IS NULL;         -- 期望 0
```

---

## 三、生产环境开关

| 开关 | 默认 | 生产建议 | 作用 |
|---|---|---|---|
| `WHALE_AGENT_QA_ENGINE` | `legacy` | `langchain`（联调验证通过后切换） | Java 内置问答 / 委托 `ai/whale-ai`（8092） |
| `WHALE_AGENT_DATA_ENABLED` | `false` | 按需 `true`，且必须同时配置下方四项 | 开启只读数据通道 |
| `WHALE_AGENT_DATA_URL` / `USERNAME` / `PASSWORD` | 空 / `whale_ai_ro` / 空 | 指向 `bluewhale_agent`，口令同步为强口令 | 只读连接（独立连接，不参与主事务） |
| `WHALE_AGENT_DATA_ALLOWED_VIEWS` | 两个汇总视图 | 保持白名单，勿放宽到基表 | SQL 守卫的视图白名单 |
| `WHALE_AGENT_OPS_ENABLED` | `false` | 按需 `true` | 平台写操作确认流程 |
| `WHALE_AGENT_OPS_TARGET_FORM` | `http://127.0.0.1:8084` | 指向实际 `whale-form` 地址 | 下游写目标 |
| `WHALE_AGENT_LLM_BASE_URL` | `https://api.openai.com/v1` | 本机 Ollama：`http://127.0.0.1:11434/v1` | OpenAI 兼容端点 |
| `WHALE_AGENT_LLM_API_KEY` | 空 | 云端填真实密钥；Ollama 填任意非空值（如 `ollama`） | 未配置时回答自动降级为检索摘要 |
| `WHALE_AGENT_LLM_MODEL` | `gpt-4o-mini` | 本机：`qwen2.5:1.5b-instruct` | 模型名 |
| `WHALE_AGENT_LLM_TIMEOUT_MILLIS` | `60000` | CPU 推理建议 `180000` | 单次模型调用超时 |
| `WHALE_AGENT_BUILD_ON_STARTUP` | `true` | 大知识库可改 `false` + 手动重建 | 启动即构建索引 |
| `WHALE_IM_MEMBER_VALIDATION_ENABLED` | `true` | 保持 `true` | 会话/加群前校验用户目录 |
| 网关 `whale.gateway.internal-deny.patterns` | 含 `/user/internal/**` | 保持 | 阻止外部访问内网接口 |

> 安全默认：`data` / `ops` / `mcp` 三个通道**默认全部关闭**，仅在显式配置凭据后开启，未配置不会误放行。

---

## 四、本机 LLM 实测（Ollama）

硬件：i7-7700K（4C/8T）+ 16 GB RAM，**无独立 GPU**，纯 CPU 推理。
模型选择：`qwen2.5:1.5b-instruct`（986 MB，Q4 量化，中文能力可用）；`qwen2.5:3b-instruct`（约 1.9 GB）为质量优先选项，CPU 单次回答约慢一倍。

| 项 | 配置 |
|---|---|
| 服务 | Ollama 0.33.3，`http://127.0.0.1:11434` |
| `whale-agent`（17084） | `QA_ENGINE=langchain`、`LLM_BASE_URL=http://127.0.0.1:11434/v1`、`LLM_API_KEY=ollama`、`LLM_MODEL=qwen2.5:1.5b-instruct`、`LLM_TIMEOUT=180000`、`DATA_ENABLED=true`、`OPS_ENABLED=true` |
| `ai/whale-ai`（5556） | 同一组 `WHALE_AGENT_LLM_*`（Python 侧复用相同前缀） |
| 只读账号 | `whale_ai_ro`@`localhost`/`127.0.0.1`，新强口令已同步至 `WHALE_AGENT_DATA_PASSWORD` |

实测结果：

- `POST /agent/chat`（SSE）：`meta.degraded=false`，`tool knowledge_search` 命中 8 条，`delta` 输出真实模型文本，`done` 标记 `degraded=false`、`tokens=58`、`elapsedMillis=31270`、带 1 条 `README.md` 引用 → **真实模型回答已验证，不再降级**。
- `POST /agent/tools/data-query`：返回 `code=0`、`views=[v_ai_agent_fact_summary]`、`count=5`、`elapsedMillis=20` → **新口令下只读通道正常**。

---

## 五、仍需在目标环境完成

| # | 事项 | 原因 |
|---|---|---|
| 1 | 若应用跨主机/容器访问 MySQL，按实际网段追加 `'whale_ai_ro'@'<网段>'` | 本机已收紧为 `localhost` / `127.0.0.1`，禁止回退到 `%` |
| 2 | 新环境执行 `data-query-ro.sql` 前替换 `CHANGE_ME_STRONG_PASSWORD` 并同步 `WHALE_AGENT_DATA_PASSWORD` | 强口令属环境机密，不入仓 |
| 3 | 云端模型部署时替换 `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL` | 本机验证使用 Ollama；云端需按供应商填写 |
| 4 | 既有库 `agent_operation` 的按列 `ALTER` | 新库由 `init.sql` 直接建全列；旧库禁止整份重跑 |
