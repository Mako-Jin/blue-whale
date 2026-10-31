# 盘点整改批次检查报告（安全快修 / 知识桶匹配 / 文档同步 / 供应链扫描）

> 日期：2026-10-31 ｜ 范围：本轮工作区盘点中「无环境依赖」的 P1/P2 项（用户指令「按顺序处理」第一批）
> 关联清单：[pending-issues-2026-10-27.md](../plans/pending-issues-2026-10-27.md) §五 ｜ 关联 devlog：[2026-10-31.md](../devlog/2026-10-31.md)
> 说明：本报告采用盘点的 `N-xx` 编号；文中出现的「盘点 V-06/V-07」分别对应本文 `N-04/N-05`，与 pending-issues 旧 `V-xx` 序列不重号。

## 一、变更摘要

| 项 | 变更 | 文件 | 结论 |
|---|---|---|---|
| N-01 | 移除本机 MySQL 口令 `515322` 默认值，缺失 `MYSQL_PASSWORD` 即失败 | `scripts/dev/run-music.ps1`（原 23 行）、`scripts/smoke/bw-contract-m2-smoke.ps1`（原 17 行） | 完成 |
| N-02 | 移除评测脚本默认口令 `123456`：改为 `-Password` 或 `WHALE_EVAL_PASSWORD`，缺失即失败；用法示例改占位符 | `scripts/agent-eval.ps1`（参数默认值、头部用法、启动前置校验） | 完成 |
| N-03 | 非 dev 环境 Cookie `Secure=false` 直接拒绝启动 | `whale-service-auth-infrastructure/.../config/SecretPlaceholderChecker.java`；`.../test/.../SecretPlaceholderCheckerTest.java` | 完成（单测 4/4） |
| N-04 | 知识桶白名单由「隐式前缀」改为「精确匹配 + 显式 `*` 通配」，排除列表同语义；`AgentProperties` 加 `@RefreshScope` 支持 Nacos 热刷新 | `whale-agent/.../service/FileKnowledgeSyncService.java`、`.../config/AgentProperties.java`、`FileKnowledgeSyncServiceTest.java` | 完成（单测 17/17） |
| N-05 | 新增 Trivy 文件系统扫描（SARIF 入 Security 面板）+ OWASP Maven 依赖扫描（周/手动） | `.github/workflows/security-scan.yml` | 完成（首批留档口径） |
| N-06 | 文档状态同步（音乐 M0–M5、上线清单、auth 拆分/解耦状态） | `docs/architecture.md`、`docs/service-catalog.md`、`docs/plans/roadmap.md`、`docs/ops/music-service-ops.md`、`docs/specs/2026-10-26-whale-service-auth-split/tasks.md`、`docs/specs/2026-10-27-auth-layer-decoupling/spec.md` | 完成 |
| N-07（部分） | `skills/*.zip` 加入 `.gitignore`（`.trae` 16 文件为 2026-10-22 既定保留，不按缺陷处理） | `.gitignore` | 部分完成（工作区文件未移动） |

## 二、关键实现说明

### 2.1 N-03 非 dev Cookie Secure fail-fast

`SecretPlaceholderChecker` 原只校验 JWT 占位密钥，现同点校验 `whale.auth.cookie.secure`：

- 非 dev + JWT 仍为占位值 → 抛 `IllegalStateException`（原有行为）；
- 非 dev + JWT 已覆盖但 `secure=false` → 抛 `IllegalStateException`，提示 `WHALE_AUTH_COOKIE_SECURE=true`；
- dev 保持放行（仅 WARN），避免本地 HTTP 联调持续 401。

测试新增 1 例「非 dev + 自定义密钥但 Cookie Secure 关闭 → 拒绝」，原 3 例同步适配新构造签名。

### 2.2 N-04 知识桶匹配

- 默认**精确匹配**：`bluewhale` 不再放行 `bluewhale-private`；
- 仅当配置项显式包含 `*` 时按通配符全串匹配（如 `oa-*`），实现以 `Pattern.quote` 分段拼接，避免正则注入；
- 排除列表复用同一匹配器，保持「排除优先于白名单」；
- 测试：原「前缀匹配」用例改为 `oa-*` 通配，并新增「不隐式前缀」负向用例；
- 配置热刷新：`AgentProperties` 标注 `@RefreshScope`，Nacos 配置变更后知识桶白名单/排除列表无需重启即可生效（`V-06` 热更新项闭环，配置值仍待按真实桶名核对）。

### 2.3 N-05 供应链扫描

- `trivy-fs`：全仓 fs 扫描，`severity=CRITICAL,HIGH`，SARIF 上传 + 工件留档；首批 `exit-code=0`，基线清理后改 `1` 阻断；
- `backend-dependency-check`：`mvn org.owasp:dependency-check-maven:check`，`failBuildOnCVSS=11` 且 `continue-on-error`，报告 HTML/JSON 工件留档；配置 `NVD_API_KEY` 可加速；
- `frontend-audit` 增加阻断步骤 `pnpm audit --prod --audit-level=critical`（阻断仅针对生产依赖的 critical 漏洞，dev 依赖告警仍只留档）。

### 2.4 N-06 文档同步

- 音乐：`architecture.md` 改「M0–M5 已落地，可上线」；`service-catalog.md` 220/221 行由 M1 改 M0–M5；`roadmap.md` Wave 5.75 勾选并写明收口证据与 3 项剩余；
- 上线清单：按 devlog/M5 报告证据勾选 10 项；保留 3 项未勾（前端逐页点检 B-09、Docker preflight 仅等价静态核对、浏览器人工回归 B-09）；
- auth 拆分 tasks：加状态说明（勾选未回填，拆分已落地，以代码为准）；解耦 spec：状态由「待批准」改为「已实施（T1–T6 全完成）」。

## 三、验证记录

- `mvn -o -pl whale-services/whale-service-auth/whale-service-auth-starter -am test -Dtest=SecretPlaceholderCheckerTest` → **Tests run: 4, Failures: 0, Errors: 0**。
- `mvn -o -pl whale-services/whale-agent -am test -Dtest=FileKnowledgeSyncServiceTest` → **Tests run: 17, Failures: 0, Errors: 0**（含新增精确匹配负向用例与 `oa-*` 通信用例；同时验证 `@RefreshScope` 引入可编译）。
- `pnpm --filter @bluewhale/whale-ui test` → **34/34 通过**（新增 `values.test.ts` 17 例）。
- `pnpm --filter @bluewhale/whale-ui lint` → **0 警告**；`pnpm turbo run test` → **9/9 任务成功**。
- 三个改动脚本以 UTF-8 读取后 `Parser::ParseInput` → **语法错误 0**。
- `git status` 改动均为预期文件（未提交）。
- `docker compose -f infrastructure/docker/docker-compose.yml config -q`（临时环境变量）→ **exit=0**。
- `security-scan.yml` 与 `code-quality.yml` 经 `js-yaml` 解析 → **YAML OK**。

## 四、遗留与建议

1. **补跑验证**：`actionlint`（或 GitHub 侧 dry-run）校验 `security-scan.yml`；`docker compose config` 与 YAML 解析已在续修批次通过（见 §六）。
2. **环境阻塞未解**：B-01（Docker 全链路；compose 需 `.env` 且本机 3306/6379/8848 被宿主服务占用）、B-09（Playwright 浏览器未安装）、B-10（`whale-message` 8094 未起服）。
3. **N-04 剩余**：`included-buckets` 生产值需与文件服务实际桶名逐一对齐后确认（热刷新已通过 `@RefreshScope` 生效，Nacos 侧需确认配置确实在 `whale.agent` 前缀下）。
4. **N-07 剩余**：`skills/` 下 5 个 zip 仍物理存在；`.trae` 按 2026-10-22 决策保留入库。
5. **V-05（盘点编号）个人可见范围**：现有 `AgentIndexService#ownerFilter` 已提供安全默认——「有 `owner_id` 的文档仅本人可见，无 `owner_id` 的文档全员可见」；共享/部门桶是否对全员开放仍待产品决策（B-08①），本轮不改默认。
6. **大项剩余**：前端页面级单测与覆盖率（T-07）、E2E 入 CI（T-08）、其余服务越层专项（T-18 已在 agent/archive/im 收口）、前端页面 env 模板（T-10）。

## 五、风险提示

- N-01 后，`run-music.ps1` 与合同 M2 冒烟脚本**不再开箱即用**，必须显式注入 `MYSQL_PASSWORD`；这是有意的安全取舍。
- N-03 后，任何以非 dev profile 启动 auth 的本地环境若未设 `WHALE_AUTH_COOKIE_SECURE=true` 将**启动失败**；部署清单需确认该环境变量已下发。
- N-04 后，若生产实际桶名与 `included-buckets` 不完全一致（原依赖前缀兜底），文件知识同步会**少采集**；上线前需按真实桶名核对，必要时补显式 `*` 模式。

## 六、续修批次（第二批/T-18 增量）

### 6.1 分层越层治理

| 点位 | 变更 | 门禁 |
|---|---|---|
| `AgentFactController` | `GET /agent/facts` 响应装配下沉到 `FactStoreService#queryVo`，controller 不再 import `AgentFact` | `AgentArchitectureTest` 全量 `controller_must_not_depend_on_entity` |
| `AgentIndexController` | 索引状态改用 `AgentIndexMetaService#snapshot`（新增 `IndexMetaVO`），controller 不再 import `AgentIndexMeta` | 同上 |
| `AgentKnowledgeController` | 文件/文档台账分页映射下沉到 `AgentKnowledgeFileService#pageVo`、`AgentKnowledgeDocService#pageVo`，controller 不再 import 实体 | 同上 |
| `ArchiveInternalController` | 文件密级查询由直接注入 `ArchiveFileMapper` 改为 `ArchiveItemService#security` | `ArchiveArchitectureTest` 新增 `controller_must_not_depend_on_mapper` |
| `ImConversationController` | 创建会话装配下沉到 `ImConversationService#createVo`；发送链路（落库 + 机器人钩子 + 视图装配）收敛到新增 `ImMessageSendService#send`，controller 不再依赖实体 | `ImArchitectureTest` 新增 `controller_must_not_depend_on_entity` |

### 6.2 质量门禁与供应链

- `code-quality.yml` 的 `ai-pytest` 增加 mypy 步骤并转为**阻断**（固定 `mypy==2.4.1`），`ai/whale-ai/pyproject.toml` 增加 `[tool.mypy]`；本机全量检查 0 错误；
- `security-scan.yml` 的 `frontend-audit` 增加 `pnpm audit --prod --audit-level=critical` 阻断步骤（N-10 运行期口径）；
- `.gitignore` 忽略 `skills/*.zip`。

### 6.3 新增/调整测试

- `FactStoreServiceTest`（新增 2 例）：VO 装配（ID 字符串化、JSON 值解析、采集时间格式化）与非 JSON 值回退；
- `AgentFactControllerTest`（新增 3 例）：运行时权限透传、无权限全量剔除运行时事实、显式运行时类型 403 且不触达服务层；
- `AgentIndexMetaServiceTest`（新增 2 例）：快照装配、数据库不可用回落 `null`；
- `ArchiveItemServiceTest`（新增 1 例）：`security` 委托文件 Mapper；
- `ImMessageSendServiceTest`（新增 1 例）：发送后触发机器人并返回装配视图；
- `ImConversationControllerTest`：创建/发送用例改为桩 `createVo` / `ImMessageSendService#send` 返回 VO。

### 6.4 验证记录

- `mvn -o -pl whale-services/whale-agent -am test`（全模块）→ **exit=0**（含全部单测、ArchUnit、Checkstyle 门禁）。
- `mvn -o -pl whale-services/whale-agent -am test -Dtest=AgentArchitectureTest,FactStoreServiceTest,AgentFactControllerTest,FileKnowledgeSyncServiceTest -Dsurefire.failIfNoSpecifiedTests=false` → **exit=0**（首轮 26 例仅 1 例 Mockito 匹配器用法错误，修正后全绿）。
- `mvn -o -pl whale-services/whale-archive -am test -Dtest=ArchiveArchitectureTest,ArchiveItemServiceTest` → **exit=0**。
- `mvn -o -pl whale-services/whale-im -am test -Dtest=ImConversationControllerTest,ImMessageSendServiceTest,ImArchitectureTest` → **exit=0**。
- `py -3.12 -m pip install mypy` 首轮失败（`files.pythonhosted.org` 读超时），长超时重试成功；`mypy 2.4.1` 全量检查 `--ignore-missing-imports` → **exit=0**，故 T-17 由基线转阻断。
- `code-quality.yml` / `security-scan.yml` 经 `js-yaml` 解析 → **YAML OK**。
- Playwright Chromium 下载仍在后台运行，`ms-playwright` 目前仅有 `.links/__dirlock`，B-09 仍阻塞。
