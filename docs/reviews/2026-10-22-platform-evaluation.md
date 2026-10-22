# 蓝鲸平台阶段性评估报告（Objective Review）

> 评估日期：2026-10-22 ｜ 评估对象：`D:/BlueWhale/BlueWhale` @ `e4744a1`（Wave 5.47）
> 评估方式：静态代码审计 + 构建/配置核验 + 文档交叉比对（未做全量 `mvn verify` 与浏览器回归）
> 结论口径：**以"能否支撑真实业务交付"为唯一标尺**，不以"文档写得多完整"计分

---

## 一、总体判断

**这是一套工程纪律远超业务深度的平台骨架。骨架质量在中上，产品价值尚未被验证。**

| 指标 | 数值 |
|---|---|
| 后端 | 28 Maven 模块 / 16 个可运行服务 / 655 个 Java 文件 / 62,365 行主代码 |
| 接口 | 132 个 REST 端点 / 27 个 Controller / 29 个 ServiceImpl |
| 前端 | 16 个 workspace 包 / 10 个应用 / 104 个 TS(X) 文件 / 23,129 行 |
| 测试 | 120 个测试类 / 20,128 行（含真实 MySQL/Redis 的 Live IT） |
| 文档 | 218 个 Markdown / 28,310 行 |
| 提交 | 78 次，单分支 `main`，2 个作者身份 |
| 业务应用 | erp / mes / oa / pdm 各 **1 个类、26 行** |

**综合评分：6.0 / 10**

| 维度 | 分数 | 一句话依据 |
|---|---|---|
| 架构设计 | 8.0 | 命名铁律与服务边界贯彻到位，横切能力自研且带降级语义 |
| 后端工程质量 | 7.5 | 分层规整、Javadoc/Checkstyle 全覆盖；个别 God Service |
| 前端工程质量 | 5.5 | 设计器完成度高，但 0 测试、lint 空转、5 份重复请求层 |
| 测试有效性 | 6.0 | 后端有真 IT，但集中在 auth/audit/starter，业务侧薄 |
| 工程化 / CI | 4.0 | 4 条流水线齐全，但**主仓在 Gitee，GitHub Actions 不触发** |
| 文档 | 8.5 | 有偏差台账与复核清单，罕见；但存在版本漂移与副本冗余 |
| 交付 / 运维 | 4.5 | Compose 与脚本完整；K8s 仅一个 namespace，Dockerfile 只有 1 个服务 |
| 业务完整度 | 3.0 | 四个业务应用是空壳，无库、无路由、无页面 |
| 安全 | 7.0 | 黑名单/轮换/占位校验/脱敏/标识符白名单齐全；默认口令与 CORS 缺失 |

---

## 二、做得好的地方（附证据）

### 1. 架构约束是"可执行的"，不是墙上的标语

`docs/architecture.md` 把「服务自持前缀、网关不剥前缀」写成 REQ-ARCH-003，并附了 Wave 5.1f 的**反面案例与实测证据**（`POST /auth/login` 被 StripPrefix 改写为 `/login` → 401）。命名规则同样落到门禁 E15。

> 大多数项目的架构文档只写"应该怎样"，这套文档写的是"我们错在哪、怎么发现的、怎么防复发"。这是真正的工程资产。

### 2. 横切能力自研且考虑降级

`whale-spring-boot-starter`（4,463 行）里落了分布式锁、幂等、缓存一致性、审计切面、API 日志、限流、TraceId。关键点是**降级语义被显式设计并告警**：

- `SecurityDegradationAlerter`：Redis 不可用时黑名单校验放行，但打 `SECURITY_DEGRADATION` 标记 + 计数
- 网关 `RateLimitGlobalFilter`：Sentinel 未接入时用 Redis 计数器兜底

### 3. 深水区做对了：动态表单与工作流

这两块是最容易做烂的地方，但实现路径是对的：

- **表单**：元数据三层分离（列定义 / 视图 / 选项）、`table:form 1:N`、**版本化物理表** `form_data_{code}_v{version}`、发布期 `ALTER` 迁移
- **工作流**：状态机内核 + BPMN 2.0 双向 + 归一化 JSON 单一真相源 + 会签/加签/转办/回退/条件分支
- **多租户**：SQL 层强制 `WHERE tenant_id = ? AND deleted = 0`（`JdbcFormDataEngine#update/selectFields`），不是靠应用层"记得过滤"

### 4. SQL 注入面是关掉的

动态 DDL 天生高风险，但这里做了三层防护：

```java
// FormTableDdlGenerator
Pattern PUBLISHABLE_CODE = Pattern.compile("^[a-z][a-z0-9_]{0,39}$");
// JdbcFormDataEngine
Pattern TABLE_PATTERN = Pattern.compile("^form_data_[a-z0-9_]+_v[0-9]+$");
jdbcTemplate.update(sql, values.toArray());   // 值全部走占位符
```

表名/列名走白名单正则，值走 `PreparedStatement`。这是很多"低代码平台"翻车的地方。

### 5. 文档里的"自我打脸"能力

`docs/service-catalog.md` §九 有 C-01 ~ C-07 偏差台账，明确写着"缺 5 条网关路由""whale-auth 端口硬编码""Prometheus 仅规划"。`plans/next-steps.md` 开头直接标注"已归档、多处结论过期、不得作为待办依据"。

> 一个项目最危险的不是有缺陷，而是不知道有缺陷。这套文档在这点上是清醒的。

---

## 三、结构性问题（按优先级）

### P0-1 质量门禁是装饰性的，"全绿"是自证的

| 证据 | 事实 |
|---|---|
| `git remote -v` | `origin` → **gitee.com**，`github` → 备份仓 |
| `.github/workflows/code-quality.yml` 头注释 | "本仓库 origin 指向 Gitee，GitHub Actions 不会自动触发" |
| 16 个 `package.json` | 全部 `"lint": "echo 'lint: noop'"` |
| `frontend-ci.yml` | `pnpm -r lint` → 实际什么都没跑，却 `continue-on-error: false` |
| `code-quality.yml` | Checkstyle 为**报告模式**，仅 error 级阻断 |
| 前端测试 | `find -name "*.test.*"` → **0 个**；无 Playwright / Vitest / Jest 依赖 |

现实链路是：**推送 Gitee → 无自动流水线 → 人工跑 `mvn verify` → devlog 里写 "BUILD SUCCESS"**。文档里那些"292 用例全绿"是真的，但它们只在你手动跑的那一刻为真，且无法阻止下一次回归。

这比"没有 CI"更危险——它让人误以为有。

### P0-2 业务价值空心化：90% 的能力，0% 的业务

```
backend/whale-apps/whale-erp   26 行（1 个启动类）
backend/whale-apps/whale-mes   26 行
backend/whale-apps/whale-oa    26 行
backend/whale-apps/whale-pdm   26 行
```

前端 erp/oa/mes/pdm 各 392 行 / 9 文件，`diff` 显示只有包名、端口、api 文件名不同——**同一份模板复制了四次**。

配套缺口（文档已承认）：
- 4 个应用**无数据源、无建库**
- 网关**缺 5 条路由**（`/erp/**`、`/mes/**`、`/pdm/**`、`/oa/**`、`/config/**`）

也就是说：**"平台"能登录、能配权限、能画表单、能跑审批，但没有任何一个真实业务跑在上面。** 这是当前最大的风险——不是技术风险，是价值证明风险。

### P0-3 构建不可复现

```bash
$ mvn -v
Apache Maven 3.8.1
Java version: 17.0.5        # 项目要求 Java 21
```

- 无 `.mvn/`（jvm.config / toolchains.xml）
- `mvnw` 与 `mvnw.cmd` 被写进 `.gitignore`（CI 里靠 `mvn -N wrapper:wrapper` 现场生成）
- 脚本里**没有任何 `JAVA_HOME` 声明**（`grep -r JAVA_HOME scripts/` 为空）

本机 `D:/tools/installer/jdk/java21` 存在，说明是靠 shell 环境变量"碰巧"对的。换台机器 / 换个人，第一步就卡在 `release version 21 not supported`。

### P1-1 服务粒度与团队规模错配

16 个服务、10 个前端应用、132 个接口，服务间调用靠 `lb://`，但实际是**单机 + 单人/双人**推进。代价已经显形：

- 审计跨库不可见（`whale-audit` 默认读 `bluewhale_audit`，看不到 auth 写的日志，文档 §风险 10 已记录）
- 每个新能力要动 4 处：模块 / 端口 / 网关路由 / 前端 base —— C-03 就是这么攒下 5 条欠账的

微服务的收益（独立扩缩容、团队自治、故障隔离）在这个规模下一个都兑现不了，成本（分布式的每一笔账）却全额支付。

### P1-2 前端架构债

| 问题 | 证据 |
|---|---|
| 请求层 5 份各自实现 | `admin/ops/erp/oa/form-designer` 的 `api/http.ts` MD5 全不同 |
| 无数据层 | 全仓 `package.json` 无 `@tanstack/react-query` / `zustand` / `redux` |
| 巨型组件 | `WorkflowDesigner.tsx` 1,074 行；`TaskCenter.tsx` 668 行；`Canvas.tsx` 663 行 |
| 无 lint | 16 个包全部 noop |
| 无类型契约 | 后端无 springdoc/knife4j 实际依赖（仅 parent 里 `dependencyManagement`），前端手写 DTO |

### P1-3 文档成为"第二份真相"，且已与代码冲突

| 位置 | 文档说 | 代码/实际 |
|---|---|---|
| `README.md` | Spring Boot **4.0.0-M3** | `whale-parent/pom.xml` → **4.0.8** |
| `README.md` | Nacos **2.3** | `service-catalog` → 本机 **3.2.4** |
| `README.md` | MySQL **8.4** | 本机 **8.0.45** |
| `README.md` | "生产环境使用 Kubernetes 编排" | `infrastructure/k8s/` 只有 `namespace.yaml` + `placeholder.txt` |
| `architecture.md` | "集成 Sentinel 限流熔断" | `service-catalog` → "未引入 Sentinel" |

另外 `docs/whale-auth/originals/` 与 `docs/whale-audit/originals/` 是原文件的**副本**（729 行 devlog 存了 3 份），28K 行文档里有相当比例是重复归档。文档的**信噪比**在下降。

### P1-4 可观测性只有"零件"，没有"系统"

- `infrastructure/prometheus/prometheus.yml` 存在，但 **compose 未定义 Prometheus 服务**
- Grafana：目录里只有一个 `placeholder.txt`
- 无 Micrometer 指标埋点（只有 traceId + 自研 `whale-monitor` 273 行）
- 无 SLO / 无告警规则 / 无日志聚合（RabbitMQ 汇聚仍是"规划"）

故障发生时，能查的是"有没有异常日志"，而不是"哪条链路慢、哪个依赖抖"。

### P2 级问题（记录，不必立刻动）

1. **技术选型激进**：Spring Boot 4.0.8 + Cloud 2025.1.x + Jackson 3 + Boot 4 移除 `starter-aop`，依赖生态（MyBatis-Plus 3.5.17 / Redisson 4.8 / knife4j 4.5）随时可能踩坑——Redisson 已经踩过一次（3.34.1 → 4.8.0）。
2. **God Service**：`ProcessTaskServiceImpl` 706 行 34 个方法，`approve/reject/transfer/delegate/rollback/addSign` 全塞在一个类里。
3. **安全默认值**：`MYSQL_PASSWORD:root` 遍布 9 个 yml；JWT 默认占位符（有非 dev 拒绝启动保护，但 dev 默认就是弱值）。
4. **CORS 缺失**：全仓 `grep cors` → 0 命中。dev 靠 vite proxy、生产靠 Nginx 同源，一旦要独立域名/本地 file 调试就断。
5. **仓库卫生**：根目录有 `derby.log`、`data/agent-index`、`work/Tomcat`、`.trae/`、`.workflow/`（后者是另一套 CI 副本）。
6. **单分支直推主干**：78 次提交全在 `main`，无 PR / 无 review 记录（虽然 docs/reviews 有自评审）。

---

## 四、优化建议

### 阶段一：两周内止血（成本低、收益立即兑现）

**1. 让门禁真的跑起来（P0）**

```yaml
# 任选其一，别两个都不做
A. 主仓迁 GitHub，Actions 直接生效
B. 留 Gitee，写 .workflow/ 下的 Gitee Go 流水线并实测触发一次
```

同时：

```json
// 16 个 package.json 统一替换
"lint": "eslint src --max-warnings 0"
```

> 验收标准：故意提交一个 `var x = 1;` 未使用变量，流水线必须变红。**红的门禁才叫门禁。**

**2. 固化构建环境（P0）**

```bash
backend/.mvn/jvm.config        # -Xmx2g 等
backend/maven-toolchains.xml   # 声明 jdk 21
# .gitignore 移除 mvnw / mvnw.cmd，把 wrapper 提交入库
```

`scripts/*.ps1` 开头显式 `$env:JAVA_HOME = "D:\tools\installer\jdk\java21"`，并在 `check-env.ps1` 加一条 JDK 版本断言。

**3. 三条 E2E 冒烟替代人工"6/6 PASS"**

用 Playwright 写三条，挂进流水线：

```
① 登录 → 401 拦截 → 登录成功 → 权限码渲染
② 建表 → 加列 → 发布 → 填单 → 查列表（表单闭环）
③ 发起流程 → 审批 → 会签 → 通过（工作流闭环）
```

这三条一旦自动化，Wave 5.x 每次"浏览器端到端复验"的人工成本直接归零。

### 阶段二：1–2 个月结构性调整

**4. 砍服务：16 → 12**

把 `whale-erp / mes / oa / pdm` 四个 26 行空壳合并为 `whale-biz`（或先删，等有真实需求再拆）。理由：

- 它们现在贡献 0 行业务逻辑，却贡献 4 个端口、4 个前端包、4 条待补路由、4 个待建库
- 真正的业务边界此刻并不清晰，**过早拆分的边界一定是错的**

> 判断标准很简单：一个服务如果删掉它，系统功能没有任何变化，它就不该存在。

**5. 前端收口：从"10 个 app"到"1 个 shell + N 个页面包"**

- 抽 `@bluewhale/app-shell`：登录态、布局、路由、权限、错误边界
- 抽 `@bluewhale/http`：统一 `{code,message,data}` 解析 + Bearer 注入 + 401 静默刷新 + `X-Trace-Id`
- 引入 TanStack Query：把散落的 `useEffect + useState + fetch` 收敛成 query/mutation（现在每个页面都在手写 loading/error）
- `WorkflowDesigner.tsx` 拆成 `store/`（画布状态）+ `panels/`（属性面板）+ `canvas/`

**6. 建立"唯一真相"机制**

- `README.md` 里的版本表、端口表、组件表**全部改为链接**到 `service-catalog.md` / `pom.xml`
- 加一条 `scripts/check-doc-consistency.ps1`：解析 `whale-parent/pom.xml` 的版本 vs 文档中的版本字符串，不一致直接失败
- `docs/*/originals/` 副本改为 git 历史引用（`git log -- docs/xxx.md`），不再物理复制

### 阶段三：3–6 个月，从"平台"变"产品"

**7. 选一个真实业务，打通到能用**

建议 **OA 请假** 或 **MES 工单**（二选一，别贪多）：

```
表单设计 → 发布物理表 → 发起申请 → 多级审批（含会签/转办）
→ 待办中心 → 消息通知 → 归档查询 → 导出 Excel
```

目标：**12 个页面、1 个真实用户能连续用 7 天不出阻断性缺陷**。这件事做成之前，不建议再加任何平台级能力。

**8. 观测闭环（最小可用）**

- `docker-compose.yml` 补 `prometheus` + `grafana`，`prometheus.yml` 抓各服务 `/actuator/prometheus`
- 每个服务加 `micrometer-registry-prometheus`
- 定义 3 个 SLO：登录 P95 < 500ms、表单提交成功率 > 99.5%、审批流转 P95 < 2s

**9. API 契约自动化**

```xml
<!-- 各服务 pom 引入 -->
springdoc-openapi-starter-webmvc-ui
```

前端用 `openapi-typescript` 生成类型，消灭手写 DTO——现在 132 个接口全靠人肉对齐，是前端最容易出 bug 的地方。

**10. 拆 God Service**

`ProcessTaskServiceImpl` 按动作拆成 `ApproveCommand / RejectCommand / CounterSignCommand / TransferCommand`，每个类只依赖它需要的那几个 Mapper。706 → 每个 100 行左右。

---

## 五、如果只做三件事

1. **让流水线真的会红**（否则后面所有质量投入都会被下一次回归吃掉）
2. **砍掉四个空壳业务应用**（减去的是"看起来很大"的幻觉）
3. **打通一个真实业务闭环**（这是从"技术演示"到"产品"的唯一分界线）

---

## 六、写在最后

这个项目最难得的不是代码量，而是**过程留痕**：每一次踩坑、每一个偏差、每一条"残余"，都被诚实地写进了 devlog。这在个人项目里极罕见，也是它未来能长大的真正基础。

它现在的问题也不是"做得不够多"，而是**做得太宽、验证得太少**——16 个服务、10 个前端、28K 行文档，却没有一条被真实用户连续使用 7 天的业务链路。

下半场的胜负手不在"再加一层能力"，而在"把已有的能力，钉进一个真实场景里"。

---

*评估人：星枢 ｜ 复核建议：每季度重跑一次本文档，用实测替换估算*
