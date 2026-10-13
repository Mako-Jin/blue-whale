# AGENTS.md — 给 AI 助手的项目约定

本文件是 AI 助手在本仓库工作时的**硬性约定**。开始任何任务前先读它。

## 必读文档

| 文档                 | 内容                                          |
| -------------------- | --------------------------------------------- |
| `README.md`          | 项目结构、六条铁律、技术栈                    |
| `docs/README.md`     | 文档规范（「改动 → 需同步哪些文档」对照表）   |
| `CONTRIBUTING.md`    | 环境、Git 钩子、提交规范、契约更新流程        |
| `docs/decisions/`    | 已定架构决策（ADR），动手前先看有没有相关约束 |
| `docs/architecture/` | 分层与依赖方向的落地细则（后端改模块前必看）  |

## 硬性要求

1. **每完成一步实质性改动，同步更新对应文档**——对照表见 `docs/README.md` 第二节。文档与代码走同一次提交。
2. **提交信息必须符合 Conventional Commits**（`commit-msg` 钩子会拦截）。
3. **不要提前空建**未被使用的包 / 模块——按业务需要创建。
4. **不混用两端构建体系**：前端只用 pnpm，后端只用 Maven。
5. **不手写前端 SDK**——它由 `docs/api` 的 OpenAPI 契约生成。改后端接口后必须跑 `scripts/export-openapi.sh` + `pnpm --filter common-api gen:api`，并与代码同一次提交（流程见 `CONTRIBUTING.md` 第七节）。
6. **前端调接口一律走 `common-api`**，禁止直接 `fetch`；错误按 `HttpError.kind` 分支处理，禁止匹配错误文案。
7. **后端只用 JDK 21+** —— enforcer 已在构建期强制（低于 21 会直接失败并给出提示），但**运行 `java -jar` / 脚本时没有这道门**：本机 `JAVA_HOME` 可能指向 JDK 17，启动或跑 `scripts/export-openapi.sh` 前用 `mvn -v` / `java -version` 确认。
8. **重大技术决策要写 ADR**，模板见 `docs/decisions/TEMPLATE.md`。写完**必须**把它登记进
   `docs/README.md` §四 的索引 —— 漏了会红：`node scripts/verify-docs-index.mjs` 查这个（CI 也跑）。
9. **CI 的入口只能是 `.github/workflows/`**（GitHub Actions 的平台硬要求，`infra/` 装不了它）。
   **真机层（九个 `scripts/verify-*.mjs` 浏览器脚本，清单见 `scripts/README.md` 的「前置条件」列）刻意不在必过项里** —— 它们依赖真实浏览器、
   GPU、冷启动 dev server 与端口占用，进去只会得到随机红。改 workflow 里的命令前，
   **先把那条命令在本机跑一遍**（CI 配置本身没有测试）。见 `docs/decisions/0036`。

## 已知的环境坑（排查前先看这里）

| 现象                                                                                                           | 原因                                                                                                                                                                                                                                     | 处理                                                                                                                                                                                                        |
| -------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `java -jar` 启动报「Port 56491 already in use」                                                                | 执行环境注入了 `SERVER__PORT` 环境变量                                                                                                                                                                                                   | 命令行显式覆盖：`--server.port=8080`（命令行参数优先级最高）                                                                                                                                                |
| `curl localhost` 报 `upstream connect failed`                                                                  | 请求走了环境的 HTTP 代理                                                                                                                                                                                                                 | 加 `--noproxy '*'`                                                                                                                                                                                          |
| 本机后端启动失败 / 端口异常                                                                                    | 同上两类问题                                                                                                                                                                                                                             | 先区分是代码问题还是环境问题，**不要**对着端口反复排查                                                                                                                                                      |
| `mvn clean` 报「另一个程序正在使用此文件」                                                                     | 正在运行的后端进程持有 target 下的 jar                                                                                                                                                                                                   | 先结束 java 进程再构建；Windows 下运行中的 jar 无法删除                                                                                                                                                     |
| Boot 4 应用启动失败，`NoClassDefFoundError: ...WebMvcProperties`                                               | springdoc 用了 2.x（只支持 Boot 3）                                                                                                                                                                                                      | springdoc 必须用 3.x；版本对应表见 `docs/decisions/0008`                                                                                                                                                    |
| 依赖树里同时出现 `tools.jackson` 与 `com.fasterxml.jackson.databind`                                           | Boot 4 的常态：Jackson 3 是默认，Jackson 2 由 springdoc / MyBatis 传递引入                                                                                                                                                               | 代码 import 一律用 `tools.jackson.*`；注解（`@JsonIgnore`）继续用 `com.fasterxml.jackson.annotation`（包名没变）                                                                                            |
| mysql profile 启动报 `Unsupported Database: MySQL 8.0`                                                         | Flyway 10 起各数据库支持拆成独立模块，`flyway-core` 只内置 H2 / SQLite                                                                                                                                                                   | 必须显式引 `org.flywaydb:flyway-mysql`（H2 profile 一切正常，只在切库时炸）                                                                                                                                 |
| 切到 mysql / dameng 后怎么都登不进去（库里 `bw_auth_user` 是空的）                                             | **种子账号只在 h2 profile 自动种**。h2 每次启动都是空库、不种就没账号可登录（而建账号的接口本身要求已登录）；mysql / dameng 是真实库、数据留得住，应用不该每次启动都去补一个固定口令的管理员，所以本 profile 里种子默认关闭              | 显式种一次（幂等，重复执行无副作用）：`--spring.profiles.active=mysql --bluewhale.auth.seed-admin.enabled=true --bluewhale.auth.seed-auditor.enabled=true`。见 `application-mysql.yml` 的「前置准备（二）」 |
| 启动报 `Migration checksum mismatch for migration version 1`                                                   | **修改了已执行过的迁移脚本**。Flyway 用校验和守护"迁移脚本是历史"                                                                                                                                                                        | 正确做法是新增 `V2__xxx.sql`；本地开发库可重建（`DROP DATABASE bluewhale; CREATE ...`）绕过，有数据的环境不行                                                                                               |
| **启动**报 `Found more than one migration with version 1`                                                      | 新增建表脚本时**从 V1 起编号**。所有模块共用同一个 `spring.flyway.locations`，版本号是**全仓唯一**的                                                                                                                                     | 改成全仓当前最大号 + 1。事件表排在 `V2` 就是这个原因。见 `docs/decisions/0009` 的「补充」章节                                                                                                               |
| 某个查询在 H2 上正常、切 MySQL 报 `ERROR 1064`                                                                 | 列名或**别名**撞了 MySQL 保留字（如 `release`）。H2 不认这些保留字，所以接口测试全绿                                                                                                                                                     | 见下条——这是本项目最隐蔽的一类差异                                                                                                                                                                          |
| 切到达梦后报「表或视图不存在」                                                                                 | 达梦 profile 下 **Flyway 是关闭的**（Flyway 官方没有达梦模块），表结构不由应用创建                                                                                                                                                       | 先执行交付脚本：`BW_DB_PASSWORD=xxx bash scripts/init-dameng.sh`，或由 DBA 执行 `db/migration/dameng/V1__*.sql`。见 `docs/decisions/0010`                                                                   |
| 达梦上插入中文报「数据超长」，但字数明明没超                                                                   | 达梦 `VARCHAR(n)` 默认按**字节**算，而 Java 侧的截断按**字符**算（UTF-8 汉字 3 字节）                                                                                                                                                    | 达梦脚本用 `VARCHAR(n CHAR)` 显式声明字符语义（`dameng/V1__*.sql` 已处理）                                                                                                                                  |
| 达梦分页查询变慢、`Page` 似乎没生效                                                                            | URL 没写成 `jdbc:dm://` 开头，MyBatis-Plus 认不出方言，分页拦截器**静默跳过**（不报错）                                                                                                                                                  | 保持 `jdbc:dm://` 前缀；`DatasourceDialectTest` 会断言这一点                                                                                                                                                |
| `vite build` 报 `[less] variable @xxx is undefined`，但 dev server 与测试全绿                                  | **复制版式时引了隔壁应用才有的 LESS 令牌**。dev server 按需编译（没人访问那条样式分支就不编译），单元测试不碰样式 —— 只有生产构建会编译全部样式                                                                                          | 复制版式后**必须实际跑 `pnpm --filter <应用> build`**。各应用的语义令牌以自身的 `src/styles/variables.less` 为准，不要跨应用引用                                                                            |
| 基座点导航后**一直显示"加载中"**，控制台却没有任何报错                                                         | 子应用**应用名不一致**。`vite-plugin-qiankun` 按名字在 `window` 上挂生命周期壳，名字对不上就永远等不到回调，因此静默                                                                                                                     | 基座 `platform-portal/src/micro/apps.ts` 的 `name` 与子应用 `vite.config.ts` 里 `qiankun(APP_NAME, …)` **逐字一致**。改名两处同改，见 AGENTS「微前端」条                                                    |
| 子应用被基座加载时报 `SyntaxError: Cannot use import statement outside a module`                               | `@vitejs/plugin-react` 往 `<head>` 塞的 Fast Refresh preamble 是**内联 module 脚本**，而 qiankun 用 `import-html-entry` 取脚本文本 `eval` 执行，`eval` 出来的是普通脚本                                                                  | 子应用 `vite.config.ts` 的 `plugins` 里必须有 `qiankunDevPreamble()`（来自 `common-config/vite`）                                                                                                           |
| 子应用**单跑**（直接开 `:3300` 这类地址）时**白屏**，控制台报 `@vitejs/plugin-react can't detect preamble. Something is wrong.` | 上一条那处改写把 preamble 变成了 **async IIFE**，于是**丢掉了"模块脚本按文档顺序求值"这条规范保证** ⇒ 入口的 `import('…/src/main.tsx')` 与 `import('/@react-refresh')` 抢跑；冷启动的开发服务器上后者要先做依赖预构建、明显更慢，抢输就白屏（**刷新一次就好** —— 那时服务器已经热了） | **先别改代码**：`qiankunDevPreamble()` 现在会让入口脚本先等 preamble 装好（`docs/decisions/0035`）。若仍出现，确认子应用 `plugins` 里还挂着它、且没有被别的插件覆盖掉这段改写；**只在独立运行时出现**，跑在基座里不会遇到 |
| 基座请求子应用端口失败（CORS / 连接被拒）                                                                      | 基座按**固定 `devPort`** 去找子应用，端口漂移或未开 CORS                                                                                                                                                                                 | 子应用 `server: { port, strictPort: true, cors: true }`；端口与基座 `apps.ts` 的 `devPort` 保持一致                                                                                                         |
| 改子应用**组件**不生效，但改**样式**立即生效                                                                   | **已知限制**：被基座加载时子应用的 React 组件级热更新不生效（独立运行时正常），根因未定位                                                                                                                                                | 改组件时单独跑该子应用（`pnpm --filter platform-log-console dev`）。记录在 `docs/decisions/0012` 的「已知限制」，不是配置问题                                                                               |
| three 应用一进页面就显示"无法创建 WebGL 上下文"，详情是 `Cannot read properties of null (reading 'precision')` | **同一张 canvas 被复用**。`forceContextLoss()` 会把 canvas 的上下文永久标记为 lost，此后 `getShaderPrecisionFormat()` 返回 `null`；而 React 开发期 StrictMode **双跑 effect**（建 → 毁 → 建），组件用 JSX 持有固定 `<canvas>` 第二次必踩 | 画布由 `createStage` 创建、随舞台销毁，**不接受外部传入**。jsdom 测试测不出来（无 GPU 走降级分支），必须在真实浏览器验证。见 `docs/decisions/0013` 第 4 条                                                  |
| three 报 `PCFSoftShadowMap has been removed` / `Clock: This module has been deprecated`                        | three r186 移除/弃用了一部分 API，写上只得到运行时警告并**静默退回**，看起来生效了其实什么都没设                                                                                                                                         | 阴影用 `PCFShadowMap`、计时用核心的 `Timer`（不用 `Clock`）。新增 three 能力前先查当前版本的弃用清单                                                                                                        |
| 子应用里"页面上的值变了，右下角调试标记不变"（或反过来）                                                       | **qiankun 全局状态每个子应用只有一个回调槽位**，第二次 `onGlobalStateChange` 直接覆盖第一个，先注册的静默失效                                                                                                                            | 一律用 `common-tools/micro` 的 `onHostGlobalStateChange()`（桥已做多路复用），不要直接调 `props.onGlobalStateChange`。见 `docs/decisions/0013` 第 7 条                                                      |

## 写数据库相关的代码前，先记住五件事

1. **新增落库字段，先查 MySQL 保留字。** `release`、`rank`、`groups`、`system`、`window` 都是常见踩坑词。`RELEASE` 是 MySQL 8 的保留字而 H2 不认——写错了表现为"H2 正常、MySQL 建表失败"。
2. **不要用 `@TableField` 把列名映射成与属性名不同的名字。** MyBatis-Plus 会**自动补 `列名 AS 属性名` 的别名**，别名若撞保留字照样 1064。让属性名与列名同构（`appRelease → app_release`，与 `traceId → trace_id` 一致）。
3. **这类问题不要指望业务用例发现** —— 默认 profile 是 H2，它不认这些保留字，所有接口测试都会是绿的。只能对**生成的 SQL** 做断言（见 `ClientLogEntityMappingTest`）。
4. **现在有两个库要查保留字，而且结论不可互推。** `RELEASE` 是 MySQL 8 的保留字，达梦完全不认；反过来 `LEVEL` 是达梦的关键字（属非保留、可作列名），MySQL 也不管。达梦的查法：`SELECT KEYWORD FROM V$RESERVED_WORDS WHERE RESERVED='Y'`。
5. **新增建表迁移脚本时，版本号取全仓当前最大号 + 1。** 所有模块共用同一个 `spring.flyway.locations`，版本号**不是"每个模块各从 V1 开始"**。从 V1 起编号会让 Flyway 在**启动阶段**直接失败：`Found more than one migration with version 1`。三套方言（h2 / mysql / dameng）用**同一个**版本号，忘一套的症状只在那一个库上出现。

> 数据源、profile 与表结构变更的完整说明见 `server/README.md` 第六节、`docs/decisions/0009`（H2 / MySQL）与 `docs/decisions/0010`（达梦）。

## 微前端（改基座或子应用前先看）

结构：`platform-portal`（:3000）是唯一基座，用 `loadMicroApp` 挂载子应用；`platform-log-console`（:3200）、`platform-event-console`（:3300）、`platform-environment`（:3400）、`platform-admin`（:3500）、`platform-audit-console`（:3600）既是子应用**也保留独立运行**。

- **应用名、端口、路由前缀是隐式契约**：三者在基座 `src/micro/apps.ts` 与子应用各自的 `vite.config.ts` 里成对出现，写错**不会报错**、只表现为"加载中"。改动清单见 `CONTRIBUTING.md` 的「微前端相关的改动」表。
- **子应用的 `base` 分环境**：dev 必须是 `/`（否则热更新静默失效），build 才带前缀。不要"顺手统一"。
- **子应用必须能独立跑**：入口判断 `qiankunWindow.__POWERED_BY_QIANKUN__` 做分支，通信一律走 `common-tools/micro`，不要在子应用里 `import` 基座的任何东西（违反铁律 5）。
- **全局状态订阅必须走桥**：`onHostGlobalStateChange()`，不要直接调 `props.onGlobalStateChange`（单槽位，会互相覆盖）。
- **决策不许散写成实现细节**：挂载方式、沙箱策略、全局状态、`vite-plugin-qiankun` 的取舍在 `docs/decisions/0012`；三维应用与 WebGL 渲染生命周期在 `docs/decisions/0013`；多场景注册表与切换管线在 `docs/decisions/0014`；可操控场景（主人公、相机跟随、小地图）在 `docs/decisions/0015`；登录态与权限归属在 `docs/decisions/0018`；审计管道与子应用形态在 `docs/decisions/0019`。改这些先改 ADR。

## 后端写接口与审计（改 `sts-server-auth` / `sts-server-audit` 前先看）

- **新增 / 修改写接口必须标 `@Audited`**，并同时补动作常量（`AuthAuditActions`）与资源常量（`AuthResources`）。**三套标识形状相近、混用是静默的**：资源 `auth:user`（`AuthResources`）≠ 权限码 `auth:user:create`（`AuthPermissions`）≠ 动作 `auth.user.create`（`AuthAuditActions`）。把资源填成权限码 → "按资源过滤审计"永远查不到东西且不报错。`AuthTaxonomyTest` 钉住三者的形状与归属。
- **查询接口（`GET`）不标** `@Audited`。要记"谁看了什么"必须另起一条管道与另一张表（ADR-0019 第 2 条）。
- **审计没有写入接口，也不该有**：`AuditLogService` 无写方法、实体不继承 `BaseEntity`、表无 `deleted` 列，`POST /api/audit/logs` 返回 `40500`。有人想加写接口，先回答"审计记录该不该由调用方补写"。
- **拦截器顺序只能用 `WebInterceptorOrder` 的常量**（`AUDIT = -100 < AUTHORIZATION = -50 < DEFAULT = 0`），**禁止手写数字**。审计必须排在鉴权**之前** —— 排在后面时"越权尝试"会静默丢失（Spring 的 `interceptorIndex` 只回调已成功执行过 `preHandle` 的拦截器）。
- **给 `GlobalExceptionHandler` 加处理器时，必须同时调 `ResponseOutcome.markFailure(...)`**。本仓"业务失败仍返回 HTTP 200"，漏了不报错，只让那一类失败被审计记成 `SUCCESS`。
- **审计属性只在 `afterCompletion` 写**，因此"接口返回"与"审计可见"之间没有强顺序；断言审计必须按唯一条件过滤，不要假设立刻可见。

## 三维场景（改 `platform-environment` 前先看）

- **加场景的顺序**：实现 `src/scene/scenes/<id>.ts` → 在 `types.ts` 的 `SceneId` 加字面量 → 在 `catalog.ts` 的 `REGISTRY` 注册一行。注册表用 `Record<SceneId, SceneDefinition>` 承接，漏实现或拼错 id 是**编译期报错**（不是运行时白屏）。
- **切场景只换场景图，不换渲染器**：`stage.loadScene()` 里先释放旧场景、后构建新场景（反过来峰值显存是两份之和）。渲染器 / 画布 / 相机 / 控制器 / 环境贴图必须复用，否则会积累 WebGL 上下文。切场景要**重置渲染选项**为该场景声明的默认值。
- **散布禁用 `Math.random()`**：一律用 `common.ts` 的 `createPrng(seed)`（固定种子），否则画面不可复现、无法断言。测试里有"两次构建位姿指纹逐字一致"的断言钉住。
- **新增场景后先跑 `pnpm --filter platform-environment test`**：`scenes.test.ts` 会把通用契约（纯函数动画、确定性布局、释放干净、线框开关）自动套到新场景上。

## 可操控场景（主人公，见 ADR-0015）

- **交互是场景的能力，不是舞台的能力**：实现 `SceneBuild.createControls(context)` 才有主人公；舞台负责控制器的生命周期与相机跟随，**场景不碰帧循环**。
- **相机跟随必须"整体平移"**：`stage.ts` 的 `syncFocus` 把相机与轨道目标挪同样的位移。只改 `target` 不动相机会变成"原地转头"，人一走远就只剩俯视小点。**控制器不得直接改相机位姿** —— 相机由舞台持有并终身复用（ADR-0013）。
- **角度约定全应用统一**：`heading`/`azimuth` 绕 Y 轴、0 = 朝 +Z、方向向量 `(sin a, cos a)`；世界 -Z 是北。改错符号的症状是"按上往后走"或小地图"东在西边"—— 有测试钉着，但新写代码时先对约定。
- **右键射线打地面平面（y=0），不打网格**；画布尺寸为 0 时必须返回 `null`（否则 NaN 进内核）。有控制器的场景 `orbit.enablePan` 会被关掉，右键让给设终点。
- **移动算法只写进 `player/motor.ts`（纯内核）**：不碰 three、不碰 DOM。要改移动手感（速度、转身、障碍推出）只改那里，`tests/player-motor.test.ts` 是行为契约。
- **小地图自绘 canvas、不走 React 状态**；小地图地物与碰撞矩形**同源**（场景里声明一份），两份数据必然不同步。
- **新增可操控场景**：在"新增场景"四步之上，声明可行走范围与障碍（一份）+ 实现 `createControls`。

## 目录边界（勿越界）

```
web/packages/*  ──▶ 被引用，不可反向依赖 web/platform/*
web/platform/*  ──▶ 应用，互不引用
server/         ──▶ sts-app → sts-server → sts-components → sts-common → sts-parent
                    （单向；禁止反向、禁止跨层跳跃，细则见 docs/decisions/0005）
```

## 项目记忆

- `.workbuddy/memory/MEMORY.md` —— 长期项目约定（技术栈、踩坑、铁律）
- `.workbuddy/memory/YYYY-MM-DD.md` —— 每日工作日志（**追加，不覆盖**）

实质性工作完成后更新记忆；日常寒暄与简单查询不必记录。
