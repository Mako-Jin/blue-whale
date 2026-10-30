# 工作空间待办 / 阻塞 / 漏洞 / 任务总清单（2026-10-30）

> 扫描方式：`git fetch --all --prune` + 工作区只读扫描（代码标记、清单文档、构建配置、CI、依赖与仓库卫生），未修改任何业务代码。
> 代码基线：`main` = `origin/main` = `d4fb07f6`，`git rev-list --left-right --count origin/main...HEAD` = `0 0`，工作区干净。
> 工具链：Node v24.14.1 / pnpm 10.33.0 / JDK 21.0.8。
> 本机环境：Docker 守护进程未启动；`127.0.0.1:3306/6379/8848` 均未监听，故本轮**未执行**构建、测试、E2E、compose 与容器验证。
> 扫描范围：`backend/`（1678 个 Java 文件）、`frontend/`（403 个 TS/TSX，不含 node_modules）、`scripts/`（41 个 ps1/sh）、`infrastructure/`、`deploy/`、`.github/workflows/`、`docs/`、仓库卫生；排除 `target/`、`dist/`、`node_modules/`。

---

## 一、总览

| 类别 | 数量 | 说明 |
|---|---:|---|
| 待办（可执行） | 28 | 代码/前端/文档/仓库卫生中仍开放的条目 |
| 阻塞 | 8 | 环境、外部凭据、用户决策、镜像源能力缺失 |
| 漏洞 / 安全缺口 | 7 | 2 项高危、4 项中危、1 项低危 |
| 任务（工作包） | 6 | 把待办与漏洞按可独立验收的批次归并 |

代码级欠账很少：注释型 `TODO` 全仓仅 2 条，位于 `frontend/games/whale-games-core/src/engine.ts:26,34`（该包已按 [frozen-optional-modules.md](../design/frozen-optional-modules.md) 冻结、无消费方）；检索到的其余 `TODO` 均为 PM 状态分类常量或前端路由/文案标识，不是欠账。真正积压集中在安全边界、前端测试与文档/仓库卫生。

---

## 二、待办（TODO，无外部阻塞即可开工）

### 2.1 安全与一致性（优先）

| ID | 优先级 | 事项 | 证据 / 现状 | 验收口径 |
|---|---|---|---|---|
| T-01 | P1 | 修复网关 `discovery.locator` 改写路径绕 `internal-deny` | `backend/whale-services/whale-gateway/src/main/resources/application.yml:31-34`（`enabled: true`）；`InternalApiDenyGlobalFilter.java:80` 只匹配原始 `path`；`/whale-file/file/internal/**` 可被 `RewritePath` 还原为 `/file/internal/**` 后直达下游 | 对 `/whale-file/file/internal/**` 等三种服务名前缀形态均 403；单测覆盖原始路径与改写后路径 |
| T-02 | P1 | 前端令牌从 `localStorage` 迁移到 HttpOnly Cookie（或明确风险接受） | `frontend/shared-packages/whale-auth/src/createAuthStorage.ts:69-94` 及 admin/portal/ops/platform 子应用共 18 处 `localStorage.getItem/setItem`；access + refresh 均明文 | 二选一：① refresh 入 HttpOnly、access 短时内存化并补 CSRF；② 记录「XSS 风险接受」决策与补偿措施（CSP/审计） |
| T-03 | P1 | 去掉默认管理员口令与 smoke 硬编码 | `backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/init.sql:385` 内置 `admin` bcrypt 值；`scripts/smoke/*.ps1` 中 33 处 `123456` | 首次启动强制改密或改用随机初始化口令；smoke 从环境变量取账号 |
| T-04 | P2 | 内网接口直连业务端口无认证（纵深防御） | 仅部署边界文档 [network-boundary.md](../design/network-boundary.md)，代码侧 `SecurityConfig.java` 未对 `/internal/**` 做来源校验；10-27 S-06 已按「上线必检」结案 | 生产网络策略 + 服务侧来源白名单（`X-Internal-Token` 或 mTLS）；或复核上线检查脚本 `bw-network-boundary-check.ps1` 实际执行 |
| T-05 | P2 | 文件知识库个人文件仍为租户级公开池（方案 B） | [2026-10-29-agent-knowledge-bucket-policy.md](2026-10-29-agent-knowledge-bucket-policy.md) §四.2：`bluewhale` 桶内个人文件对同租户可问答用户可见，需索引「可见范围/归属人」并在查询期过滤 | 知识块带 `owner/visibility`，检索按用户过滤；越权用例覆盖 |
| T-06 | P2 | 白名单桶前缀匹配过宽 + 无热更新 | 同上 §四.1/§四.4：登记 `oa` 会同时放行 `oa-private`；白名单改动需重启 | 精确匹配/正则；配置刷新或重启步骤写入运维手册 |

### 2.2 前端与测试

| ID | 优先级 | 事项 | 证据 / 现状 | 验收口径 |
|---|---|---|---|---|
| T-07 | P1 | 前端单测补量 | 全仓仅 1 个唯一单测：`frontend/elemental/whale-ui/src/components/FormRenderer/linkage.test.ts`（node_modules 内为软链副本）；CI 已接 `turbo run test` | 核心共享包与关键页面补 vitest；覆盖率门禁可量化 |
| T-08 | P1 | Playwright E2E 未进 CI | 8 个 spec 已就绪（`frontend/e2e/tests/`），`frontend-ci.yml:64-67` 注释明确「依赖后端就绪，维持本地手动」 | 增加可选 job（夜间/手动触发）或容器化后端后进主门禁 |
| T-09 | P2 | 6 个 >600 行巨型组件拆分 | `whale-doc/DocEditor.tsx` 1073、`FormDataList.tsx` 848、`InfoPanel.tsx` 788、`FormRenderer/index.tsx` 741、`admin/job/JobList.tsx` 636、`FormDesigner.tsx` 635 | 按容器/视图/状态拆分，拆分后构建 + lint + 回归通过 |
| T-10 | P2 | qiankun 首屏重复拉取与公共依赖未 external | `frontend/platform-apps/whale-platform/src/qiankun/index.ts:48` `prefetch: 'all'`；全仓 `vite.config.ts` 无 `external` 配置（仅 portal 有 `rollupOptions`） | 关闭全量预取或按需；react/antd 外置或共享 chunk 策略生效并测量首屏 |
| T-11 | P3 | 前端环境变量文档覆盖不全 | 仅 `frontend/ops/.env.example`、`frontend/platform-apps/whale-platform/.env.example` 两份；子应用 `VITE_*` 无集中说明 | 每个可独立部署的 app 补 `.env.example` 或统一在根文档登记 |
| T-12 | P3 | `shamefully-hoist=true` | `frontend/.npmrc` | 评估关闭，验证 workspace 依赖可解析 |

### 2.3 后端与基础设施

| ID | 优先级 | 事项 | 证据 / 现状 | 验收口径 |
|---|---|---|---|---|
| T-13 | P2 | Hikari 连接池零显式配置 | 全后端 `application.yml` 检索 `hikari/maximum-pool-size/minimum-idle` = 0 命中 | 按服务给出显式上限/超时/泄漏检测，并写入配置基线 |
| T-14 | P3 | 唯一显式 `failFast=false` 调用的放行语义待确认 | 注解默认已为 `true`（`DistributedLock.java:41`）；`PermissionCacheMutex.java:27` 显式 `failFast=false`，取锁失败仅 warn 后继续 | 确认该缓存互斥放行可接受并加注释；其余调用禁止 `failFast=false`（可加 ArchUnit/Checkstyle 约束） |
| T-15 | P2 | Grafana 仪表盘未挂载 | `infrastructure/prometheus/grafana/` 仅 `placeholder.txt` + `provisioning/datasources/prometheus.yml`；compose 只挂 provisioning | 增加 dashboards provisioning 与至少 1 个看板，容器内加载验证 |
| T-16 | P3 | Nacos 版本双轨 | 容器编排 `nacos/nacos-server:v2.4.0`；需求登记册记录本机原生 3.2.4 | 统一版本或明确容器/本机差异与验收口径 |
| T-17 | P3 | AI 质量门禁缺失 | `ai/whale-ai/pyproject.toml` 仅 `pytest` 配置，无 ruff/mypy | CI 增加 ruff + mypy（或记录豁免） |
| T-18 | P3 | 架构测试仅 1 个模块 | 全仓仅 `whale-service-file-architecture-test` | 对 core 服务补分层依赖测试（DDD 四层：interfaces/application/domain/infrastructure） |
| T-19 | P3 | 多数据库（国产化）未实施 | [register.md](../requirements/register.md) REQ-ARCH-002「设计已定稿，未实施」 | 按 [multi-database.md](../design/multi-database.md) T1–T6 分批，先方言去 MySQL 专有语法 |

### 2.4 文档与仓库卫生

| ID | 优先级 | 事项 | 证据 / 现状 | 验收口径 |
|---|---|---|---|---|
| T-20 | P1 | `docs/architecture.md` 严重滞后 | 对 `whale-doc/pay/message/config/contract/alert/monitor/archive` 检索命中 0；后端已有 24 个模块/服务 | 补服务全景、限界上下文、端口/库/路由，并与 service-catalog 对齐 |
| T-21 | P2 | `docs/README.md` 6 个「待建」文档仍不存在；README 目录树缺 `ai/` | `docs/README.md` 列 backend/frontend/devops/api/database/ai 均标「待建」；README 目录树无 `ai/`（正式服务 8092） | 补最小可用文档或删除「待建」条目；README 补 `ai/`、`tools/`、`data/` |
| T-22 | P2 | 缺 `LICENSE` 与 `.gitattributes` | 两个文件均不存在（`Test-Path` = False） | 明确许可证（License TBD）与换行/二进制规则 |
| T-23 | P2 | `.trae/` 14 个 IDE 私有文件入库 | `git ls-files .trae` = 14 | 决定保留（则登记为文档源）或移出并加 ignore |
| T-24 | P2 | 根目录运行时残留约 102 MiB | `logs/`、`data/`、`.tmp/` 共 226 文件/107,236,033 B；根目录另有 `.e2e-form.log`、`monitor-boot.log`、`dump.rdb` 等 | 清理并确认 `*.log`/`dump.rdb` 不入库（已 ignore） |
| T-25 | P3 | `tools/` 17.4 GiB 安装包/解压体位于工作区 | `tools/` = 18,639,961,273 B（docker/logs/minio-data/monitoring/ollama/wsl） | 移到工作区外或纳入独立数据盘 |
| T-26 | P3 | 文档重复 49 组 / 108 份；wave 编号冲突 | `docs/**/*.md` 哈希重复统计：49 组、108 文件；`wave5.35` 同时指 Sentinel 评估与工作流网关，`wave5.61` 同时有 design/plan/review | 历史文档保留口径已在 roadmap 确认（originals 不清理），仅需登记；编号冲突补别名说明 |
| T-27 | P3 | git 历史死重与作者身份不统一 | `git count-objects -vH` pack 23.75 MiB（另有 406 个松散对象）；`git shortlog -sne` 两个名字共用 `846288535@qq.com`（`w13259720691` 206 / `846288853` 98） | 评估 filter-repo（需维护窗口）或仅记录；`.mailmap` 统一作者 |
| T-28 | P3 | smoke 公共库未完全收敛 | 32 个 smoke 脚本均 dot-source `scripts/smoke/lib/common.ps1`（共 58 处引用），但仍有 11 个脚本各自定义 `Invoke-Api/Invoke-Sql/Invoke-Query` 等本地函数 | 抽净重复函数并保持 32 个 smoke 脚本语法通过 |

---

## 三、阻塞（无法在本机/当前授权下完成）

| ID | 阻塞项 | 影响 | 解除条件 |
|---|---|---|---|
| B-01 | Docker 守护进程未启动（`npipe:////./pipe/dockerDesktopLinuxEngine` 不存在） | 无法构建镜像、`docker compose config/up`、`nginx -t`、容器级 smoke | 启动 Docker Desktop 或提供独立构建环境 |
| B-02 | MySQL 3306 / Redis 6379 / Nacos 8848 未监听 | 无法跑后端测试中的实机用例、网关 smoke、E2E、服务启动 | 启动本机中间件（`scripts/dev/whale-stack.ps1`）或用容器编排 |
| B-03 | V-09 生产 compose 实跑缺 Let's Encrypt 证书 | 生产覆盖层只能 `config` 校验，不能 `up` | 宿主机挂载 `fullchain.pem`/`privkey.pem`；仓库仅跟踪 `infrastructure/docker/nginx/ssl/README.md`，本地自签证书 CN=bluewhale.local、有效期至 2029-01-25，未入库（私钥亦不入库） |
| B-04 | 外部供应商凭据缺失（CA/TSA、实名/意愿、短信/SMTP、esign 沙箱） | S-04d-2、S-04e-2、F-03b、V-06、V-11 的真实联调无法关闭 | 2026-10-28 已决策「短期仅 mock/模拟」，转商务条件成熟后另立任务 |
| B-05 | Gitee Go 流水线 | 不影响交付 | 已决策 won't do，CI 全量依赖 GitHub Actions |
| B-06 | 本机 npm 镜像不支持 audit 端点 | 无法生成依赖漏洞清单：`ERR_PNPM_AUDIT_ENDPOINT_NOT_EXISTS`（`registry.npmmirror.com/-/npm/v1/security/audits/quick`） | 切换官方 registry 或启用 CI 依赖审查（PR 已接 `dependency-review-action`） |
| B-07 | Agent 知识索引一次性重建需维护窗口 | W5.75 白名单上线后旧索引块仍存在 | 服务可用后执行 `POST /agent/knowledge/rebuild`，并核对白名单外桶检索为 0 |
| B-08 | 待用户拍板的决策 | 阻塞对应改造开工 | ① localStorage→HttpOnly 方案；② 文件知识个人可见范围方案 B；③ LICENSE/.trae/重复文档保留口径；④ Nacos 版本策略；⑤ 默认管理员口令处理方式 |

---

## 四、漏洞 / 安全缺口

| ID | 等级 | 问题 | 证据 | 建议 |
|---|---|---|---|---|
| V-01 | **高** | 网关服务发现路由改写可绕过内网接口屏蔽 | `application.yml:31-34` locator 开启；`InternalApiDenyGlobalFilter` 只匹配原始路径（`:80`）；服务名前缀路由 `/whale-file/**` + 内部 `RewritePath` 后命中 `/file/internal/**` | 在路由过滤链末端再校验一次改写后路径，或将 `internal-deny` 改为按服务名 + 下游路径双重匹配；补绕过用例 |
| V-02 | **高** | 直连业务端口的 `/internal/**` 无服务侧认证 | 现有防护只在网关；`network-boundary.md` 属部署边界；业务服务端口（8080–8095）可被直连 | 服务侧内网令牌/mTLS；或上线前强制执行网络策略并留存验证记录 |
| V-03 | 中 | access/refresh token 明文存 `localStorage` | `frontend/shared-packages/whale-auth/src/createAuthStorage.ts:69-94` 等 18 处 | 迁移 HttpOnly（refresh）+ 短时 access + CSP；见 T-02 |
| V-04 | 中 | 内置默认管理员口令与 smoke 硬编码 | `init.sql:385` bcrypt 种子；33 处 smoke 明文 | 首次改密/随机口令/环境变量注入；见 T-03 |
| V-05 | 中 | 文件知识库个人文件对同租户可见 | [bucket-policy 报告](2026-10-29-agent-knowledge-bucket-policy.md) §四.2 | 索引期写归属 + 查询期过滤；见 T-05 |
| V-06 | 低 | 白名单桶前缀匹配过宽、配置需重启生效 | 同报告 §四.1/§四.4 | 精确匹配/正则，配置热更新或明确重启步骤 |
| V-07 | 中（能力） | 依赖/镜像漏洞扫描覆盖不足 | `.github/workflows/security-scan.yml` 仅 gitleaks + dependency-review + CodeQL（CodeQL 仅每周/手动）；无 Trivy/OWASP dependency-check；本机 `pnpm audit` 端点不可用（B-06） | 增加镜像 Trivy 与周期依赖扫描；或在独立环境跑 OWASP dependency-check |

> 已复核不属于漏洞的项：`compose` 已无 `0.0.0.0` 端口绑定（`127.0.0.1:` 全覆盖）；Actuator 仅暴露 `health,info,prometheus`（网关另加 `routeGovernance`）；`alert` 默认 webhook token 仅 dev profile 放行，非 dev 启动即拒绝；`.gitignore` 的 `/data/`、`/out/` 等已根锚定；Nginx 已补安全头与隐藏文件 deny；私钥未入库。

---

## 五、任务（建议工作包）

| 任务包 | 包含条目 | 目标 | 依赖 |
|---|---|---|---|
| K-1 网关与内网安全收口 | V-01、V-02、T-01、T-04 | 关闭 2 个高危缺口，补齐绕过与来源校验用例 | 服务可启动（B-02） |
| K-2 前端登录态与测试基线 | V-03、T-02、T-03、T-07、T-08 | HttpOnly 方案落地、前端单测/E2E 可量化、默认口令下线 | 用户决策（B-08①） |
| K-3 前端性能与结构 | T-09、T-10、T-12、T-11 | 拆分巨型组件、关闭全量预取、依赖外置、env 文档补齐 | 无 |
| K-4 后端与基础设施基线 | T-13、T-14、T-15、T-16、T-18、T-19 | Hikari/锁默认值/架构测试/Grafana/Nacos/多数据库分批 | T-19 需独立立项 |
| K-5 文档与仓库卫生 | T-20、T-21、T-22、T-23、T-24、T-25、T-26、T-27、T-28 | 架构文档补齐、LICENSE/.gitattributes、清理工作区与作者统一 | 用户决策（B-08③④） |
| K-6 环境验证与上线证据 | B-01、B-02、B-03、B-07、V-07 | 起 Docker/中间件 → 后端测试 + E2E + 镜像矩阵 + 生产 compose + 索引重建 + 安全扫描 | 环境与证书 |

---

## 六、已闭环（本轮不再计入待办）

- 10-29 审计 P0 全部：支付并发竞态、前端 ErrorBoundary、CI 前端测试门禁、`.gitignore` 根锚定、qiankun entry 配置化。
- 10-29 审计 P1 多数：token.ts 抽共享包、ops 监控地址环境变量化、compose 回环绑定 + 弱口令移除、CI 安全扫描工作流、`docker-deploy` 测试门禁、env 模板统一、alert webhook token 启动守卫。
- P2：MapStruct 全量替换 `BeanUtils`、支付状态枚举化、smoke 公共库、LocalChunkUploadStore 流泄漏、auth SQL 日志改 Slf4jImpl、useImData 定时器清理、Nginx 安全头、prometheus 告警规则加载、3 个旧 Dockerfile 删除。
- 待办清单 [pending-issues-2026-10-27.md](../plans/pending-issues-2026-10-27.md) 仅剩 V-09（阻塞于证书）。
- Wave 5.74 Wiki 文档服务 M0–M5 完成；`.trae/specs` 三份 spec 任务/检查项全部勾选。

---

## 七、建议执行顺序

1. **K-1**（V-01/V-02）：高危且改动局部，先补失败用例再修过滤器/路由。
2. **K-2 的 T-03**（默认口令）：不依赖用户决策，先做随机化/首登改密。
3. **B-01/B-02 环境恢复** → 跑后端测试 + 现有 8 条 E2E，建立本轮验证基线。
4. **K-5/T-20 + T-22 + T-24**：低风险、收益直观，先把文档与仓库卫生拉回可维护状态。
5. **K-2 的 T-02/V-03、K-4 的 T-13/T-14**：等用户决策/配置基线确认后实施。
6. V-09/B-03、B-07、V-07 留到独立环境与维护窗口。

> 单一待办入口仍以 [pending-issues-2026-10-27.md](../plans/pending-issues-2026-10-27.md) 为准；本文件是 2026-10-30 的只读盘点快照，不替代该入口。
