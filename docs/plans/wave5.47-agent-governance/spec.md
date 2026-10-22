# Wave 5.47 轨 E：治理、评估与文档 — 实施规范

- 日期：2026-10-22
- 上游规划：[wave5.47-project-agent.md](../wave5.47-project-agent.md) 第 241–247 行（E1–E4）
- 验收口径：扫描结果无明文密码；越权访问 runtime 返回 403；评测报告归档 `docs/reviews/`；黄金集 ≥80%、结构化 ≥95%、引用有效率 100%

## 一、现状结论（已核实）

| 项 | 现状 | 缺口 |
|---|---|---|
| 权限点 | `whale-auth/init.sql` 已有 `sys:agent:chat` / `search` / `index:rebuild` / `runtime:read`（id 100–103） | 无 agent 菜单种子 |
| runtime 可见性 | `FactRetriever.collectServices` 无条件查询 `RUNTIME_SERVICE`；`AgentFactController` 无过滤 | 需按 `sys:agent:runtime:read` 过滤，越权 403 |
| 敏感文件 | `RepositoryScanner` 已排除 `.env*`/`*.pem`/`*.key`/`*credentials*`/`application-prod*`；`SensitiveContentMasker` 已遮蔽 password/secret/token/apiKey | 需留验证证据（索引产物扫描） |
| 审计 | `AuditLog` + `AuditLogAspect` 可复用；`bluewhale_agent` 库无 `audit_log` 表 | 补 DDL + 查询接口标注 |
| 评测 | `scripts/` 为空；无黄金集 | 新建 40 题 + 评测脚本 |
| 文档 | roadmap 轨 E 未勾选；devlog 无轨 E；service-catalog 无 whale-agent 条目 | 补齐 |

## 二、E1 权限与 runtime 可见性

### 2.1 文件与改动

1. `backend/whale-services/whale-agent/src/main/java/com/bluewhale/agent/support/web/UserContext.java`
   - 新增 `public static boolean hasPermission(HttpServletRequest request, String code)`：
     `WebHeaders.splitCodes(WebHeaders.header(request, WebHeaders.PERMISSION_CODES)).contains(code)`。
2. `backend/whale-services/whale-agent/src/main/java/com/bluewhale/agent/service/FactStoreService.java`
   - 新增重载 `query(String factType, String factKey, Integer limit, boolean includeRuntime)`；
     原三参方法委托新方法（`includeRuntime = true`）。
   - `includeRuntime == false` 且 `factType` 为空时，追加 `wrapper.ne(AgentFact::getFactType, "RUNTIME_SERVICE")`。
3. `backend/whale-services/whale-agent/src/main/java/com/bluewhale/agent/controller/AgentFactController.java`
   - 方法签名增加 `HttpServletRequest httpRequest`。
   - 常量 `RUNTIME_TYPE = "RUNTIME_SERVICE"`、`RUNTIME_PERMISSION = "sys:agent:runtime:read"`。
   - 判定 `boolean runtimeAllowed = UserContext.hasPermission(httpRequest, RUNTIME_PERMISSION)`。
   - 显式请求 runtime 且无权限：`throw new BizException(ErrorCode.FORBIDDEN)`（HTTP 403）。
   - 查询调用改为 `factStoreService.query(type, key, limit, runtimeAllowed)`。
4. `backend/whale-services/whale-agent/src/main/java/com/bluewhale/agent/support/qa/FactRetriever.java`
   - `retrieve(String question)` 委托 `retrieve(question, true)`。
   - 新增 `retrieve(String question, boolean includeRuntime)`；`collectServices(question, merged, includeRuntime)`
     仅在 `includeRuntime` 时查询 `RUNTIME_SERVICE`。
5. `backend/whale-services/whale-agent/src/main/java/com/bluewhale/agent/service/ChatService.java`
   - `chat(ChatRequest request, Long userId, boolean includeRuntime)`；
     `answer(conversation, question, scope, includeRuntime, emitter)`；
     `collectCitations(question, scope, intent, includeRuntime)` 透传给 `factRetriever.retrieve`。
6. `backend/whale-services/whale-agent/src/main/java/com/bluewhale/agent/controller/AgentChatController.java`
   - 计算 `runtimeAllowed` 并传入 `chatService.chat(...)`。
7. `backend/whale-services/whale-auth/src/main/resources/db/init.sql`
   - 菜单种子追加：
     - `(9, 0, '知识助手', '/agent', NULL, 'message', 2, 'DIR', NULL, 1, 1)`
     - `(10, 9, '知识问答', '/agent/chat', 'agent/chat/index', 'comment', 1, 'MENU', 'sys:agent:chat', 1, 1)`
8. 新建 `backend/whale-services/whale-auth/src/main/resources/db/migration-5.47.sql`
   - `INSERT IGNORE` 上述两行菜单；注释说明幂等与授权由角色界面完成。

### 2.2 验证

- 无 `X-Permission-Codes` 或缺少 `sys:agent:runtime:read` 时：`GET /agent/facts?type=RUNTIME_SERVICE` → 403；
  不带 `type` → 结果中不含 `RUNTIME_SERVICE`。
- 携带 `sys:agent:runtime:read` → 正常返回。
- `POST /agent/chat` 对不含 runtime 权限的用户，引用中不出现 `RUNTIME_SERVICE` 事实。

## 三、E2 脱敏与查询审计

### 3.1 文件与改动

1. `backend/whale-services/whale-agent/src/main/resources/db/init.sql`
   - 追加 `audit_log` 建表（与 `whale-audit/init.sql` 同构，append-only：无 `update_time` / `deleted`）。
2. 新建 `backend/whale-services/whale-agent/src/main/resources/db/migration-5.47.sql`
   - `CREATE TABLE IF NOT EXISTS audit_log (...)`，供存量库升级。
3. `AgentSearchController#search`：`@AuditLog(operation = "知识检索", type = OperateType.QUERY, recordParams = true, recordResult = false)`。
4. `AgentFactController#facts`：`@AuditLog(operation = "事实查询", type = OperateType.QUERY, recordParams = true, recordResult = false)`。
5. `AgentChatController#chat`：`@AuditLog(operation = "知识问答", type = OperateType.QUERY, recordParams = true, recordResult = false)`。

> 说明：`AuditLogAspect` 已内置 `SensitiveDataMasker`，问题文本中的密钥样式会被遮蔽；仅记录查询元数据，不记录回答正文。

### 3.2 验证

- 启动后调用 `/agent/search`、`/agent/facts`，查询 `bluewhale_agent.audit_log` 应出现 `operate_type=QUERY` 记录。
- 索引产物扫描：对 `data/agent-index` 下全部文件执行 `password|secret|token|accessKey|apiKey` 赋值模式扫描，
  命中必须为 `***` 或键名，不允许出现真实值。
- 单测 `SensitiveContentMaskerTest` 保持通过（已存在）。

## 四、E3 黄金集与评测脚本

### 4.1 新增文件

- `docs/agent/golden-set.json`：40 题，4 类各 10 题。
  - 类别：`structured`（服务/端口/路由/依赖）、`schema`（表/字段）、`code`（代码定位）、`doc`（文档/流程）。
  - 单题结构：
    ```json
    {
      "id": "S01",
      "category": "structured",
      "question": "whale-agent 服务端口是多少？",
      "expect": {
        "intent": "STRUCTURED",
        "facts": [{"type": "PORT", "key": "whale-agent", "contains": "8090"}],
        "pathsAny": ["deploy", "application.yml"],
        "keywords": ["8090"],
        "minCitations": 1
      }
    }
    ```
  - 断言口径：`facts` 命中 ≥1；`keywords` 命中 ≥1（不区分大小写）；`minCitations` 为引用条数下限；
    `pathsAny` 为证据路径可选命中项。
- `scripts/agent-eval.ps1`：
  - 参数：`-BaseUrl`（默认 `http://localhost:8080`）、`-Username`/`-Password`（默认 admin/123456）、
    `-GoldenSet`（默认 `docs/agent/golden-set.json`）、`-OutFile`（默认 `docs/reviews/2026-10-22-wave5.47-agent-eval.md`）。
  - 流程：登录取 token → 逐题 `POST /agent/chat`（`stream=true`，`Accept: text/event-stream`）→
    解析 `meta`/`delta`/`done` → 汇总回答文本与引用 → 按 `expect` 断言 → 输出 Markdown 报告。
  - 指标：总体正确率、结构化类正确率、引用有效率（引用路径存在于仓库）。
  - 阈值：总体 ≥80%、结构化 ≥95%、引用有效率 100%；任一不达标 `exit 1`。

### 4.2 验证

- `pwsh -File scripts/agent-eval.ps1` 生成报告；无模型 Key 时走降级摘要，仍可校验事实与引用。
- 报告归档 `docs/reviews/2026-10-22-wave5.47-agent-eval.md`。

## 五、E4 文档

- `docs/plans/roadmap.md`：轨 E 勾选并附验收摘要。
- `docs/devlog/2026-10-22.md`：追加「三、Wave 5.47 轨 E」章节（范围、变更点、验证结果）。
- `docs/service-catalog.md`：补 whale-agent（8090、`/agent/**`、`bluewhale_agent`、前端 8004）。
- `docs/README.md`：目录索引补 `docs/agent/`、`docs/ops/`。
- 新建 `docs/ops/agent-index-ops.md`：索引目录、重建命令、状态查询、故障排查、脱敏与权限说明。

## 六、执行与提交

1. 后端改动完成后：`mvn -o -q -pl whale-services/whale-agent -am -DskipTests package`（JAVA_HOME=java21）编译校验。
2. 重启 whale-agent（8090）后按 §2.2 / §3.2 验收。
3. 执行 `scripts/agent-eval.ps1` 生成评测报告。
4. 文档同步后 `git add` → commit（`feat(agent): 治理与评测（Wave 5.47 轨 E）`）→ `git push origin main`。
