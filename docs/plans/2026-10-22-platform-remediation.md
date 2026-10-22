# 平台评审整改任务清单

> 来源：[docs/reviews/2026-10-22-platform-evaluation.md](../reviews/2026-10-22-platform-evaluation.md)（workbuddy 阶段性评估，评估基线 `e4744a1`）
> 整理日期：2026-10-22 ｜ 状态：**待排期**（尚未进入 roadmap 执行序列）
> 筛选口径：只保留「本机（无 Docker）可验证」「不破坏既有 Wave 规划」「能直接降低回归或交付风险」的项；与现有既定决策冲突的项单列说明。

## 一、结论速览

| 报告建议 | 结论 | 处理方式 |
|---|---|---|
| 让质量门禁真正生效（P0-1） | **采纳（最高优先，已完成）** | Gitee Go 为预付费服务未开通，改走 GitHub Actions 镜像仓；三条流水线触发、变红、恢复绿均已实测 |
| 固化构建环境（P0-2） | **采纳（已完成）** | 根目录入库 mvnw（3.9.9）+ `.mvn/jvm.config`，脚本显式 `WHALE_JAVA_HOME`，`check-env` 加 JDK 21 断言 |
| 三条 E2E 冒烟（阶段一-3） | **采纳（分两批）** | 先登录 + 表单闭环 2 条，工作流闭环随后；Playwright 挂进同一条流水线 |
| 文档「唯一真相」（阶段二-6） | **采纳（拆分）** | 先做 README 版本表改链接 + 一致性校验脚本；`originals/` 副本清理需单独确认 |
| 前端请求层 5 份 → 1 份（P1-2） | **采纳** | 抽 `@bluewhale/http`，先替换 3 个平台应用，再收 admin/ops |
| 巨型组件拆分（P1-2） | **采纳（P2 级）** | `WorkflowDesigner` 1074 行拆 store/panels/canvas |
| 可观测性最小闭环（P1-4） | **部分采纳** | 代码侧加 `micrometer-registry-prometheus` 并验证 `/actuator/prometheus`；compose 栈因本机无 Docker 转后续环境 |
| 商业应用空心化（P0-2） | **调整** | **不删空壳**，改为「冻结扩张 + 选定 OA 请假打通一条真实闭环」 |
| 16 → 12 服务合并（阶段二-4） | **不采纳** | 合并/删除会牵动端口、路由、前端包、建库四处，收益低于迁移成本；冻结即可 |
| 主仓迁 GitHub（阶段一-1 A） | **不采纳** | 主仓仍在 Gitee；GitHub 直连不稳定，仅新建镜像仓 `Mako-Jin/blue-whale-platform`（remote `ci`）承载 CI |
| TanStack Query / 全量数据层（阶段二-5） | **暂缓** | 先统一请求层，再按页面复杂度评估，避免一次性大改 |
| API 契约自动化（阶段三-9） | **暂缓（P2）** | springdoc 需先验证 Spring Boot 4 + Jackson 3 兼容性 |
| God Service 拆分（阶段三-10） | **暂缓（P2）** | `ProcessTaskServiceImpl` 706 行，待业务闭环稳定后拆 |
| 技术选型回退（P2） | **不采纳** | Boot 4 / Cloud 2025 已全量落地并验证，回退成本不可接受 |
| CORS 缺失、默认口令、仓库卫生（P2） | **采纳** | 低成本、可直接改 |

## 二、P0：先让「验证」可信

### P0-1 CI 真正生效（GitHub Actions，已完成 2026-10-22）
- **原计划**：走 Gitee Go（`.workflow/` 已有映射）。**实际受阻**：Gitee Go 为预付费增值服务，仓库未开通，无法免费触发。
- **最终路线**：新建 GitHub 私有镜像仓 `Mako-Jin/blue-whale-platform`（remote `ci`），复用 `.github/workflows/`；Gitee `origin` 仍为主托管。
- **已完成**
  1. 17 个前端包 `lint` 由 `echo 'lint: noop'` 改为 `eslint src --max-warnings 0`，清零存量告警（react-refresh / no-console / exhaustive-deps）。
  2. `pnpm format` 统一 69 个文件格式，`format:check` 可在 CI 阻断。
  3. 两条 workflow 改用 `pnpm/action-setup@v4`（原 corepack 方案在 setup-node 缓存前不可用）。
  4. Checkstyle 17 处 error 级违规清零；`checkstyle:check` 绑定到 `validate` 阶段（不解析模块依赖，干净仓库可直接执行），CI 改为 `./mvnw -B -ntp validate`。
  5. 修复 `FormTableDefinitionServiceImplTest` 的 MyBatis-Plus lambda 缓存顺序依赖（Linux/Windows 测试顺序差异导致 CI 偶发失败）。
- **实测结果**
  - 触发：push `3e273bd` / `c4fd77b` → Backend CI、Frontend CI、Code Quality 均被触发。
  - 阻断：故意提交未使用变量 + 缺 Javadoc 的类（`f9bbcbd`）→ **三条流水线全部 failure**。
  - 恢复：revert 后（`93018b3`）→ **三条流水线全部 success**。
- **验收**：✅ 已完成（正向绿、反向红闭环）。
- **规模**：M ｜ **依赖**：无 ｜ **附录**：GitHub 网络间歇超时，push 需重试；日常需 `git push origin main` + `git push ci main`。

### P0-2 构建可复现（已完成 2026-10-22）
- **实际落点调整**：计划的 `backend/mvnw` 改为**仓库根** `mvnw`。根 `pom.xml` 是聚合入口，CI 与 Checkstyle 的 `configLocation`（`${maven.multiModuleProjectDirectory}`）都以仓库根为基准；在 `backend/` 单独生成 wrapper 反而会让 `validate` 阶段找不到规则集。
- **已完成**
  1. 根目录生成并入库 Maven Wrapper（wrapper 3.3.4 only-script + Maven 3.9.9，阿里云镜像）：`mvnw` / `mvnw.cmd` / `.mvn/wrapper/maven-wrapper.properties`；`.gitignore` 移除 `.mvn/`、`mvnw`、`mvnw.cmd` 三条忽略规则。
  2. 新增根 `.mvn/jvm.config`：`-Xmx2g`。
  3. `infrastructure/scripts/check-env.ps1`（计划写作 `scripts/check-env.ps1`，实际文件在 `infrastructure/scripts/`）新增 JDK 21 断言：`JAVA_HOME/bin/java` 优先、缺省回退 PATH 上的 `java`，主版本非 21 计入失败并以退出码 1 结束。
  4. `scripts/dev/{whale-stack,whale-stack534,whale-hold,whale-start-all}.ps1` 头部统一 `$env:JAVA_HOME`：优先 `WHALE_JAVA_HOME`，缺省回退 `D:\tools\installer\jdk\java21` 并校验存在；Nacos 2.4 需要的 JDK 17 单独由 `WHALE_NACOS_JAVA_HOME` 提供。
  5. `backend-ci.yml` / `code-quality.yml` 删除冗余的 `mvn -N wrapper:wrapper` 兜底分支，仅保留 `chmod +x mvnw`（Windows 提交不保留可执行位）。
- **实测结果**
  - 清空 `JAVA_HOME` 后执行 `.\mvnw.cmd -v` → `Apache Maven 3.9.9` + `Java version: 21.0.8`（首次自动下载 3.9.9 到 `~/.m2/wrapper`）。
  - `check-env.ps1`：`JAVA_HOME` 指向 JDK 17 → `[FAIL] JDK 21 ... 当前为 17`，退出码 1；指向 JDK 21 → JDK / MySQL / Redis / Nacos 四项 PASS，退出码 0。
- **备注**：Gitee 已推送（`d70d07b` / `c3c47ae`）；GitHub `ci` 推送时 443 连续超时，待网络恢复后补推 `git push ci main`，届时会触发 Backend CI / Code Quality 在 Linux runner 上验证入库后的 wrapper。
- **验收**：✅ 已完成。
- **规模**：S ｜ **依赖**：无

### P0-3 E2E 冒烟（Playwright，两批；第一批已完成 2026-10-22）
- **步骤**
  1. ✅ 新建 `frontend/e2e/`（独立 workspace 包 `@bluewhale/e2e`），Playwright + `webServer` 自动拉起 admin（8001）与表单设计器（8002）。
  2. ✅ 第一批两条：① 未登录 → 跳登录 → 登录成功 → 菜单按权限渲染；② 建表 → 加列 → 发布 → 填单 → 列表可见。
  3. ⏳ 第二批：发起流程 → 审批 → 会签 → 通过（工作流闭环）。
  4. ✅ 本地入口 `pnpm e2e`（前端根目录），失败产出 trace/截图；CI 挂载待全栈 job 就绪。
- **已完成（第一批）**
  - 用例 A 以 `/auth/me` 返回的顶层菜单为期望集合，逐项比对侧边栏文本；`sys:agent:chat → 知识助手` 可见，并验证 `系统管理 → 用户管理` 动态路由。
  - 用例 B 的建表/加列/发布/填报/列表/详情全部走真实 UI；字段配置走 API（@dnd-kit 合成 PointerEvent 不可自动化），保存语义与设计器 `persist()` 一致；唯一编码 + `afterAll` 尽力清理。
  - `globalSetup` 对网关 8080 / auth 8081 / form 8084 做快速失败自检并输出启动清单。
  - 实测：`pnpm e2e` → **2 passed（15.6s）**；临时隐藏 `知识助手` → 用例 A **失败**，恢复后通过；`lint` / `format:check` 通过。
- **验收**：✅ 第一批达成（`pnpm e2e` 通过 + 反向验证失败）。第二批待续。
- **规模**：L ｜ **依赖**：P0-1（需流水线可跑）；本机跑 E2E 需先启动 whale-form（8084）。
- **CI 决策**：整改清单步骤 4 允许本地 `pnpm e2e` 口径；GitHub Actions 暂无 MySQL/Redis/Nacos 全栈 job，第一批不新增 workflow，留待第二批与全栈 job 一并挂载。

### P0-4 文档唯一真相
- **现状**：`README.md` 仍写 Spring Boot `4.0.0-M3`、Cloud `2024.0.0-M3`、Nacos `2.3`，与 `whale-parent/pom.xml`（4.0.8 / 2025.1.2）不一致；`architecture.md` 写「集成 Sentinel」而实际未引入。
- **步骤**
  1. README 版本表、端口表改为链接 `service-catalog.md` / `pom.xml`，删除硬编码版本。
  2. 修正 `architecture.md` 的 Sentinel 表述为「自研限流 + resilience4j 熔断，Sentinel 未引入」。
  3. 新增 `scripts/check-doc-consistency.ps1`：从 `whale-parent/pom.xml` 解析 Boot/Cloud 版本，与 README、architecture 中的版本字符串比对，不一致退出码 1。
  4. `docs/*/originals/` 副本清理：**需用户确认**（建议改为 git 历史引用），确认后单独执行。
- **验收**：脚本在人为改错版本时失败；全仓检索 `4.0.0-M3` 为 0 命中。
- **规模**：S ｜ **依赖**：无

## 三、P1：降低结构性成本

### P1-1 前端统一请求层
- **现状**：`admin / ops / erp / oa / form-designer / workflow-designer / agent / portal` 各自实现 `api/http.ts`。
- **步骤**
  1. 在 `frontend/shared-packages/` 新建 `@bluewhale/http`：`{code,message,data}` 解析、Bearer 注入、401 单飞刷新重放、`X-Trace-Id`、`downloadBlob`、`streamSse`。
  2. 先替换 3 个平台应用（form-designer / workflow-designer / agent），保留各应用 `api/*.ts` 业务层不动。
  3. 再替换 admin / ops 及 4 个业务应用，删除重复实现。
- **验收**：全仓 `api/http.ts` 仅剩共享包一处；三个应用登录、401 刷新、SSE 流式回归通过。
- **规模**：M ｜ **依赖**：无

### P1-2 安全默认值与 CORS
- **步骤**
  1. 9 个 yml 的 `MYSQL_PASSWORD: root` 改为 `${MYSQL_PASSWORD:}` 或 dev 专用占位并注释；启动时对空值给出明确报错。
  2. 网关新增可配置 CORS（`whale.gateway.cors.allowed-origins`，dev 默认 `http://localhost:*` 的具体白名单），生产由 Nginx 同源兜底。
- **验收**：不设环境变量时启动报错信息明确；`OPTIONS` 预检在允许来源下返回正确头，非白名单来源被拒。
- **规模**：S ｜ **依赖**：无

### P1-3 可观测性最小闭环（代码侧）
- **步骤**
  1. 各业务服务 pom 引入 `micrometer-registry-prometheus`，暴露 `/actuator/prometheus`（网关与 auth 优先）。
  2. 定义 3 个 SLO 指标口径并写入 `docs/ops/`：登录 P95、表单提交成功率、审批流转 P95。
  3. Prometheus + Grafana compose 服务：**本机无 Docker，转具备 Docker 的环境执行**，本轮只交付配置与抓取规则。
- **验收**：`curl /actuator/prometheus` 返回 `http_server_requests_*` 指标；compose 配置静态校验通过。
- **规模**：M ｜ **依赖**：无（compose 部分依赖 Docker 环境）

### P1-4 仓库卫生
- **步骤**
  1. `.gitignore` 补充 `derby.log`、`work/`、`data/agent-index/`、`.workbuddy/`；确认 `.trae/specs/` 需保留入库。
  2. 清理工作区已跟踪的构建产物（`work/Tomcat` 等），仅保留源码与配置。
- **验收**：`git status` 干净；新克隆仓库无运行期产物。
- **规模**：S ｜ **依赖**：无

## 四、P2：结构性优化（业务稳定后再做）

### P2-1 巨型组件拆分
- `WorkflowDesigner.tsx`（1074 行）→ `store/`（画布状态与历史栈）+ `panels/`（属性面板）+ `canvas/`；`TaskCenter.tsx`（668 行）、`Canvas.tsx`（663 行）同法。
- **验收**：单文件 ≤400 行；工作流设计器浏览器回归通过。
- **规模**：M

### P2-2 God Service 拆分
- `ProcessTaskServiceImpl`（706 行 / 34 方法）按动作拆 `ApproveCommand / RejectCommand / CounterSignCommand / TransferCommand`，每个类只依赖必要 Mapper。
- **验收**：现有单测全绿；`/workflow/task/*` 行为不变。
- **规模**：M

### P2-3 API 契约自动化
- 试点 1 个服务引入 springdoc-openapi，验证 Spring Boot 4 + Jackson 3 兼容；前端用 `openapi-typescript` 生成类型。
- **验收**：`/v3/api-docs` 可访问；生成类型可编译。
- **规模**：M

### P2-4 分支与评审策略（可选）
- 关键变更开 `wave5.x-*` 分支 + PR，要求 CI 绿后合并；日常文档/小修仍可直推 `main`。
- **验收**：下一次 Wave 交付走分支 + PR 各一次。
- **规模**：S

## 五、业务价值：不删空壳，改为「冻结 + 打通一条」

- **冻结**：`whale-erp / mes / oa / pdm` 四个空壳**不删除、不合并**为 `whale-biz`，暂停新增端口/路由/页面；路线图既有 Phase 4 计划不变。
- **打通**：从 OA 请假 / MES 工单中**二选一**，按「表单设计 → 发布物理表 → 发起申请 → 多级审批（会签/转办）→ 待办中心 → 归档查询 → 导出 Excel」走通。
- **验收**：12 个页面，1 名真实用户连续使用 7 天无阻断性缺陷；补齐对应网关路由与建库。
- **前置决策**：业务方向需用户确认（OA 请假 vs MES 工单）。

## 六、建议执行顺序

```
P0-1 CI 生效 ──► P0-2 构建可复现 ──► P0-3 E2E（第一批）
        │                                  │
        └──► P0-4 文档一致性 ──────────────┘
                                            ▼
                        P1-4 仓库卫生 / P1-2 安全默认值（可并行）
                                            ▼
                        P1-1 统一请求层 ──► P2-1 巨型组件拆分
                                            ▼
                        P1-3 可观测性 ──► 业务闭环（OA/MES 二选一）
```

## 七、明确不做

| 项 | 原因 |
|---|---|
| 主仓迁 GitHub | 本机 HTTPS 直连超时；Gitee Go 映射文件已就绪 |
| 4 个空壳服务合并为 `whale-biz` / 直接删除 | 牵动端口、网关路由、前端包、建库四处；收益不抵迁移成本，冻结即可 |
| 技术栈回退（Boot 4 / Cloud 2025 / Jackson 3） | 已全量落地并验证，回退成本不可接受 |
| TanStack Query 全量引入 | 先统一请求层，再按页面复杂度评估 |
| `docs/*/originals/` 立即删除 | 属文档资产处置，需用户确认后单独执行 |
