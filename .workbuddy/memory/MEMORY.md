# BlueWhale — 项目长期备忘

## 项目定位
大型全栈项目，前后端分离。工作空间：`D:\workspace\BlueWhale`。

## 参考项目（舰长指定的结构范本）
- `D:\workspace\platform-terminator` — 纯前端 monorepo：pnpm workspace、`common-*` 公共包、按业务域分组、qiankun 微前端、React
- `D:\workspace\SystemTerminator` — 纯后端 Maven 多模块：`sts-*` 体系，包名 `com.yaocode.sts`
> 新需求优先参考这两个项目的既有做法，不要另起结构。

## 顶层划分
`web/`（前端·pnpm）· `server/`（后端·Maven，包名 `com.bluewhale.sts`）· `docs/ scripts/ infra/`（横向层）

## Git 与文档约定
- 仓库根 `D:\workspace\BlueWhale`，分支 `main`
- Husky 钩子目录 `web/.husky`，用 Git 原生 `core.hooksPath` 挂载；沙箱下 husky 自动安装会因 `spawn git EBUSY` 失败 → 手动挂钩；钩子脚本内先 `cd "$(git rev-parse --show-toplevel)/web"`
- `.gitattributes` 强制 LF（husky shell 脚本变 CRLF 会在 Linux 上执行失败）
- `blender_mcp/` 已忽略（第三方独立仓库，舰长决定不纳入主仓）
- 提交信息走 Conventional Commits（`commit-msg` 钩子拦截）
- **舰长要求：每一步都完善文档**；对照表在 `docs/README.md`，ADR 在 `docs/decisions/`，开发与 AI 约定在 `CONTRIBUTING.md` / `AGENTS.md`
- 铁律：改代码必须同步改文档，同一次提交完成

## 技术栈
- 前端：React 19 + Vite 8 + TS **6.0.3**（7.x 不被 typescript-eslint 支持）+ React Router 7
- 微前端 qiankun 2.x；包管理 pnpm workspace + Turborepo；样式 Less
- 规范：ESLint 10（flat config）+ Prettier 3 + Stylelint 17 + commitlint/Husky/lint-staged + Vitest 5
- 后端：Java 21（enforcer 强制）+ Spring Boot 4.1.1（Spring 7 · Tomcat 11 · Jackson 3）+ MyBatis-Plus 3.5.17（Maven 多模块）
- 数据源：H2 内存库（默认 profile）+ 本机 MySQL 8.0.26（`mysql` profile）+ **达梦 DM8**（`dameng` profile，国产库）；H2/MySQL 表结构由 **Flyway 12** 管理，达梦走交付脚本
- 契约：OpenAPI 3.1（`docs/api/`，springdoc 3.1.1 生成）

## Spring Boot 4 升级要点（2026-10-09 定 · ADR-0008）
- **starter 坐标三处随 Boot 大版本一起动**：`web→webmvc`、`boot3-starter→boot4-starter`、springdoc `2.x→3.x`（2.x 配 Boot 4 启动即失败：WebMvcProperties 换包）；MockMvc 测试需另加 `spring-boot-starter-webmvc-test`，注解换包到 `org.springframework.boot.webmvc.test.autoconfigure`
- **Jackson 3**：databind/core 迁到 `tools.jackson.*`，**注解仍在** `com.fasterxml.jackson.annotation`（包名与 2.x 共用）；classpath 上 Jackson 2 因 springdoc/MyBatis 传递仍在，import 认准 tools.jackson；`JacksonException` 是 unchecked
- **enforcer**：`requireJavaVersion [21,)` 继承到所有子模块；message 里**禁写 `${java.version}`**——会被 Maven 属性插值吃掉（取到 POM 里的 21，不是运行 JVM），提示会自相矛盾
- **契约升 OpenAPI 3.1**：任意值字段从 `Record<string, never>` 变 `unknown`；`common-api` 的 JsonValue 收紧层保留，分页 records 元素类型裂缝已用 `WithJsonRecords` 修复并加断言

## 前端结构
- `packages/` 公共包（`common-*`）、`platform/` 应用、`elemental/` 预留
- 实际存在：`common-config`（共享工程配置）· `common-tools`（公共能力包，6 模块：
  logger / env / http / json / report / events）· `common-api`（契约装配）·
  `platform-portal`（:3000 qiankun 主应用）· `platform-log-console`（:3200 日志演示）·
  `platform-event-console`（:3300 行为事件演示）；根 `web/eslint.config.js`
- 规划的 `common-{theme,components}`、`platform-{user,admin}` **按需再建**，不空建
- 规范只维护一份在 `common-config`：子项目 `stylelint.config.js` 一行 re-export；`eslint.config.js` 须调用 `createEslintConfig()`
- **各应用的 LESS 语义令牌各自一份**（`src/styles/variables.less`），**禁止跨应用引用** —— 引了只有 `vite build` 会报，dev 与测试都发现不了

## 公共依赖与公共包治理（2026-10-09 定 · ADR-0002）
- **依赖版本统一**：`web/pnpm-workspace.yaml` 的 `catalog:` 段是唯一来源，各 `package.json` 写 `"catalog:"`
- **不建"存放依赖的包"** —— 会产生幽灵依赖（库可被引用却没声明，运行时解析不到）。新增依赖：先写 catalog 再引用
- **公共代码**按能力建包 `packages/common-<能力>`；建包前先问"是否只有一个应用会用"
- 新建公共包的 `eslint.config.js` 需 `...createEslintConfig()` 再追加自定义规则（如日志包关 `no-console`）

## logger（common-tools）关键决策
- 7 级严重度（trace/debug/info/success/perf/warn/error）+ 阈值过滤；**过滤前置于格式化**
- 级别映射到 `console.debug/log/warn/error`（参考实现全走 log，导致筛选与堆栈定位失效）
- 渲染三态自适应：`css`（浏览器 `%c`）/ `ansi`（终端真彩）/ `plain`（管道、`NO_COLOR`）
- 命名空间层级 `child()`、`withContext()`、主题按级别按字段深合并、`LogSink` 可替换输出通道
- 消息里的 `%o`/`%c` 必须转义（用零宽空格隔断，否则会吞掉后续参数）
- 参考实现共修复 16 项问题，清单见 `web/packages/common-tools/README.md`
- 演示：`pnpm --filter common-tools playground`（:3100），把控制台输出镜像渲染到页面

## 输出通道两个钩子，分工不可混（2026-10-09 定 · ADR-0004）
- `LogSink` 消费**渲染后的样子**（含 `%c` 样式串）→ 控制台、测试捕获、页面镜像
- `onRecord` 消费**结构化事实** `LogRecord` → 上报、落库、聚合
- **上报必须走 `onRecord`**：sink 级别有损（`info`/`success` 都压成 `'log'`），元数据被拼进字符串，只能反向解析
- `onRecord` 派发点在**级别过滤之后、格式化之前**；内部异常**静默吞掉**（防上报失败递归成雪崩）
- `LogRecord` 含 `level/namespace/timestamp/message/data/context` + `traceId/sessionId/route/release`
- 服务端聚合与查询**已实现**（`sts-server-log`）；**采样与脱敏未实现**，等有真实生产流量再做；生产可复用成熟 APM（Sentry/RUM）

## 配置分域（2026-10-09 定 · ADR-0003）
- 三层：**密钥级**（Vault/KMS）· **部署级**（Nacos/Apollo）· **业务运行期**（自研，走 OpenAPI 契约）
- **自研范围只有业务运行期配置**；下两层自研是浪费，密钥层写错就是安全事故
- 客户端配置五条底线：首屏零阻塞、兜底默认值在客户端、版本化+缓存+变更通知、配置项有 schema 与类型、环境物理隔离
- 禁止：万能配置接口（长成上帝对象）、命名风格并存、无变更审计、两端各自解释语义

## 前端踩坑备忘
- stylelint 的 `extends` 必须 `require.resolve` 转绝对路径（pnpm 隔离）
- TS 6 起 `baseUrl` 弃用
- ESLint 10 要求 flat config **默认导出数组**，不能导出函数
- ESLint 只从**当前工作目录**向上找配置（stylelint 按文件位置）→ 工作区根也要有 `eslint.config.js`
- typescript-eslint 必须显式 `tsconfigRootDir`；`projectService: false` 实测无效
- **沙箱限制**：Node `spawn git` 报 `EBUSY`；`turbo run` 报 `os error 231`（管道句柄耗尽）。均为环境限制，逐包直跑脚本均通过

## 后端分层（依赖只能自上而下）
`sts-app` → `sts-server` → `sts-components` → `sts-common` → `sts-parent`；`sts-parent` 零代码，版本号只出现一次

### 已落地模块（2026-10-09 · ADR-0005）
```
server/  pom.xml(聚合) → sts-parent
  → sts-common/{basic, web, db}
  → sts-server/sts-server-log（日志域）
  → sts-app/sts-app-server（唯一可启动进程，H2 内存库）
```
- groupId **按层分级**（`com.bluewhale.sts.common.basic`…），`<version>` 只在 `sts-parent` 出现
- `sts-common-basic`：`R<T>` · `ResultCode`（5 位 = HTTP 3 位 + 业务 2 位）· `BusinessException`；**刻意零依赖**，唯一例外是 `jackson-annotations`（provided，仅为 `@JsonIgnore`）
- `sts-common-web`：`GlobalExceptionHandler` + CORS + 自动配置（`AutoConfiguration.imports`），依赖即生效
- `sts-common-db`：MyBatis-Plus 3.5.17 拦截器（坐标 `mybatis-plus-spring-boot4-starter`）；**分页/防全表更新需另引 `mybatis-plus-jsqlparser`**，漏引运行时才报错
- 模块内按**特性（feature）分包**（`clientlog/` 下 Controller/Service/Mapper/VO），**不用** `controller/service/repository` 顶层分包
- `sts-components/` **尚未创建**，按需再建，不预置空壳

## 数据源与迁移（2026-10-10 定 · ADR-0009）
- **双 profile**：`h2`（默认，内存库，零依赖可跑）+ `mysql`（本机 MySQL，数据落盘）；切换是**启动参数**不是改文件
  - `BW_DB_PASSWORD=xxx java -jar … --spring.profiles.active=mysql`
  - 库名 `bluewhale`（utf8mb4 / utf8mb4_0900_ai_ci），已在本机 MySQL 8.0.26 上建好；本机还有另一个项目的 11 个库（`yc-sts-db-*` / `sts-db-*`），**勿动**
  - 密码走环境变量 `BW_DB_PASSWORD`，不进 git（ADR-0003）
- **建表机制 = Flyway 12**（不再是 `spring.sql.init`）：脚本放表结构所属模块
  `src/main/resources/db/migration/{h2,mysql}/V<n>__xxx.sql`，`spring.flyway.locations` 按 profile 指向厂商目录；脚本内**不写** `IF NOT EXISTS`（会掩盖"表在但结构不对"）
- **两个必踩的依赖坑**（都是 H2 正常、切库才炸）：
  - `flyway-mysql` 必须显式引 —— Flyway 10 起数据库支持模块化，core 只内置 H2/SQLite，缺了报 `Unsupported Database: MySQL 8.0`
  - `mysql-connector-j`（Boot 纳管 9.7.0）runtime 范围，放 `sts-app-server`
- **MySQL 保留字陷阱（最重要）**：`release` 是 MySQL 8 保留字、H2 不认 → 列名必须避开常见保留字（`release`/`rank`/`groups`/`system`/`window`）
  - **且不能只改列名**：MP 发现"列名 ≠ 属性名"会自动补 `列名 AS 属性名`，别名撞保留字照样 1064
  - 正解：**属性名与列名同构**（`appRelease` ← 推导 `app_release`，与 `traceId → trace_id` 一致），**刻意不加 `@TableField`**；对外字段仍是 `release`
  - 守卫：`ClientLogEntityMappingTest` 直接断言生成的 SQL 含 `app_release`、不含 `AS release`（H2 测不出来，只能对生成物断言）
- **迁移脚本一旦执行过就不能改**（校验和不匹配拒绝启动）；本地重建库绕过，有数据的环境新增 V2
- `#{it.xxx}` 这类 OGNL 属性引用**编译器管不到**，实体改属性名后要全局搜一遍

## 达梦 DM8（2026-10-10 定 · ADR-0010）
- 三 profile 并列：`h2`（默认）/ `mysql` / `dameng`；驱动 `com.dameng:DmJdbcDriver18`（**Boot 的 BOM 不纳管**，`sts-parent` 手工收口），驱动类 `dm.jdbc.driver.DmDriver`
- **MyBatis-Plus 原生内置 `DbType.DM`**（`JdbcUtils` 正则 `:dm\d*:` 匹配 URL）→ 分页/防全表更新**零代码改动**；枚举里还有 `KINGBASE_ES`/`OPENGAUSS`/`GAUSS`/`OCEAN_BASE`/`XU_GU`/`SINODB` 等一串国产库，**换库先查这张表**
- **Flyway 官方无达梦模块**（42 个 `flyway-*` 构件里没有）→ 达梦 profile `flyway.enabled=false`，DDL 走 `scripts/init-dameng.sh` 交付脚本；已否决"引第三方 db-migration"与"自研 DatabaseType（300–400 行、绑 Flyway internal API）"
- 方言：`IDENTITY(1,1)` · **`VARCHAR(n CHAR)`（默认按字节，中文会「数据超长」）** · `COMMENT ON COLUMN` 独立语句 · 无 ENGINE/CHARSET · 标识符不加引号转大写
- 保留字：本表零冲突；**`RELEASE` 达梦不认、`LEVEL` 是达梦非保留关键字** → 两库结论**不可互推**
- `DatasourceDialectTest` 钉住三 profile 的 URL → `DbType`；**达梦本身未经真实实例验证**（10 条清单在 ADR-0010）

## 契约链（2026-10-09 定 · ADR-0006）
- 代码 → springdoc → `scripts/export-openapi.sh`（端口 18080）→ `docs/api/openapi.json` → `openapi-typescript` → `common-api/src/generated/schema.d.ts`
- 生成物**入库**：前端构建不依赖后端可运行；契约变化在 review 里显式可见
- 路径常量 `satisfies keyof paths`；`startTime` 等字段用 `expectTypeOf` 钉住（springdoc 会把 query 位带 `@Schema` 的 `Long` 推平成 string）
- **openapi-typescript 的坑**：`Object` 字段渲染成 `Record<string, never>` → `common-api/types.ts` 的 `JsonValue` 只对 `data`/`context`/`logs` 修正
- openapi-typescript 7.13 peer 要 TS ^5.x，项目是 6.0.3 → 有警告但功能正常

## 前端请求分层（2026-10-09 定 · ADR-0007）
- `common-tools/http` = 传输内核（不认识业务接口）；`common-api` = 契约装配（只做接线）
- 三件收口：解包 `R<T>`、`code!==0` 抛异常、超时+取消合并一个 AbortController
- 六类错误 `business/http/network/timeout/abort/parse` + `retryable` 判据；**内核不做自动重试**（幂等边界属业务语义）
- **超时必须先于取消分类**（两者都是 abort 实现）；`traceIdProvider` 注入 X-Trace-Id，不自动生成
- 日志上报通道 `LogReporter`：批量 20 / 3s / 缓冲 500 / 退避封顶 30s / 不抛异常不自我记录

## 行为事件管道：第三条独立管道（2026-10-10 定 · ADR-0011）
- 三条管道并列：**技术日志**（排查故障/可采样）· **审计日志**（合规/后端拦截器为权威）· **行为事件**（分析用户行为/**不可采样**/契约极稳定）
- **内容不同 ⇒ 独立模块独立表；通道相同 ⇒ 必须复用** `common-tools/json` + `common-tools/report`
- 前端形态：**语义化 `track()` + 内部总线**，不做通用全局事件总线
  - 事件名**永久契约**：`^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`，前后端**同一条正则**；非法名丢弃且只报一次
  - 中间件（补齐上下文/屏蔽/脱敏）与多订阅者都是**内核内部实现**，业务只见 `track()`
  - **登录先 updateContext 再 track；登出先 track 再 clearUser()** —— 顺序反了不报错，只丢"谁登出了"
- 关键边界：**双时间戳**（`clientTime` 不可信仅展示 / `serverTime` 是排序过滤唯一依据）·
  `event_id` **不建唯一索引**（一条重复不能让整批 INSERT 失败）· `propsVersion` **按次传** ·
  无 `traceId`（契约没这列）· `deviceId` + `userId` **同时挂**（否则漏斗口底对不上）
- 卸载兜底用 `fetch(keepalive)` **不用** `sendBeacon`（后者发 JSON 触发预检 → 静默失败）；keepalive 上限 64KB，按体积裁剪且优先保住最近批次
- 前端 `EventEnvelope.clientTime` ↔ 契约 `ClientEventItem.timestamp`，改名在接线层 `toReportItem`；**写错不报错，只有该列为空**
- 聚合接口**刻意不接受 `eventName` 过滤**（它本身按事件名分组）；返回字段是 `eventNameCount`
- 后端模块 `sts-server-track`，表 `bw_client_event`（维度列 + `props_json`），`V2__create_bw_client_event.sql`
- **未做**（ADR-0011 列了 8 条）：采样 · 服务端二次脱敏 · 事件注册表 · Kafka/ClickHouse · 数据生命周期 · 同意管理与按用户删除 · 服务端 propsVersion 校验 · 达梦事件表实例验证

## 日志控制台 demo（platform-log-console，:3200）
- 左栏客户端视角 / 右栏服务端视角并排 —— 只看一边无法判断上报成没成
- `emit` 不能写 `logger[level](...)`：`perf` 签名是 `(label,value,unit)`，须显式 switch 分派
- 已用真实浏览器（Chrome headless + CDP）验证全链路：点击 → 上报 → 落库 → 表格刷新
- 姊妹页 `platform-event-console`（:3300）是同一思路用于**行为事件**：演示身份缝合 / 脱敏 / 屏蔽；
  两条管道独立 ⇒ 两个应用，不是一个应用里两个页签

## 微前端接入（2026-10-10 定 · ADR-0012）
- **之前的前提是错的**：`platform-portal` 只是装了 qiankun 依赖，全仓 `registerMicroApps`/`initGlobalState` 零命中；两个控制台是独立应用（无生命周期导出、vite 未配 base）。本轮才真正接线
- 基座用 **`loadMicroApp`** 而不是 `registerMicroApps` + `start`：挂载点由路由出口给（位置唯一、无竞态），后者要常驻容器且必须先于 `start()` 存在
- **`sandbox: false`**：JS 沙箱靠 `with(window)` 劫持全局，**包不住 ESM 模块作用域**，对 Vite 子应用本就无效；关掉只损失 `document`/`history`/定时器补丁
- 子应用接入 **`vite-plugin-qiankun`**（不做 UMD）。它做两件事：把 `<body>` 里 `<script type="module">` 改成动态 `import()`；在 `window` 上按应用名挂"等 qiankun 回调"的生命周期壳
- **`import-html-entry` 用 `eval` 执行脚本文本 → module script 跑不了**：这是必须用插件的根本原因
- **应用名是隐式契约**：基座 `micro/apps.ts` 的 `name` 与子应用 `qiankun(APP_NAME,…)` 逐字一致；不一致**不报错、只卡"加载中"**。（同名对：`platform-log-console` / `platform-event-console`）
- 子应用 `base` **分环境**：dev 必须 `/`（否则 `useDevMode` 定位不到 `/@vite/client`，热更新**静默失效**），build 才带 `/log-console/` `/event-console/`
- **`initGlobalState` 不返回 `getGlobalState`**：只有 `onGlobalStateChange`/`setGlobalState`/`offGlobalStateChange` → 基座自留 `currentState` 副本填缺口。（参考项目 `platform-terminator` 把不存在的 `actions.getGlobalState` 传给子应用，是**真 bug**）
- 子应用侧通信全走 **`common-tools/micro`**（`bindHost`/`unbindHost`/`getHostGlobalState`）：结构化类型、**不 import qiankun**；子应用之间禁止互相引用 ⇒ 只能下沉到 `packages/`
  - 调试设施：`window.__MICRO_HOST__`（props 原件）+ 右下角状态标记（验证全局状态下发）
  - `unbindHost` **先取父节点再清 `hostProps`**，反过来会退化成 body → 留下"卸载不干净"假象
- **React Fast Refresh preamble 修复**：`@vitejs/plugin-react` 往 `<head>` 塞内联 module 脚本，插件不管它，`eval` 时抛 `Cannot use import statement outside a module` → `common-config/vite` 导出 `qiankunDevPreamble()` 改写成 `await import()`；`apply:'serve'`，生产不需要
- 样式隔离**指望不上框架**（`experimentalStyleIsolation` 依赖 `<style>` 在入口 HTML 里，Vite dev 由 JS 插入 `document.head` 且跑在真实 window 上）
- **已知限制（根因未定位）**：被基座加载时子应用 **React 组件级热更新不生效**（样式热更新正常、子应用独立运行正常）。A/B 对照确认与 preamble 修复无关。当前工作方式：改组件时单独跑子应用
- 端到端验证脚本 `scripts/verify-micro-frontend.mjs`（真实 Chrome + CDP，端口 9333，会就地改源码探 HMR 再还原）
- 端口：基座 :3000 · 日志 :3200 · 事件 :3300；子应用须 `strictPort: true` + `cors: true`（基座按固定端口找它）
- **未做**：`platform-user`/`platform-admin` 未创建 · 生产部署静态目录与网关规则 · 子应用样式前缀去重 · 子应用错误边界

## 环境坑（已查清，勿再排查）
- 后端启动报 Port 56491：沙箱注入 `SERVER__PORT` 环境变量 → 命令行 `--server.port=8080` 覆盖
- `curl localhost` 报 upstream connect failed：环境 HTTP 代理 → 加 `--noproxy '*'`
- 本机 `JAVA_HOME` 默认 JDK 17 → 后端构建须 `JAVA_HOME=/d/tools/installer/jdk/java21`
- 约定：**业务失败也返回 HTTP 200**，结果只由 `code` 表达（前端判 `code === 0`）；`R.isSuccess()` 必须 `@JsonIgnore` 否则泄漏 `success` 字段
- 构建：PATH 上是 JDK 17，**必须** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn …`
- `mvn clean` 报「另一个程序正在使用此文件」：上一轮启动的后端占着 jar →
  `jps -l` 找到 PID 后 `taskkill //F //PID <pid>`（Git Bash 里斜杠写 `//`）
- **PowerShell 工具在本沙箱不可用**（`LsSDK_CreateSandbox(ConPTY) 失败`）→ 改用 Bash
- Maven 本地仓库在 **`D:\repository\maven`**（不是 `~/.m2`）

## 日志双管道：技术日志 ≠ 审计日志（2026-10-09 定）
- **技术日志**（前端上报）：排查故障 · 短保留期 · 采样 · 前端驱动
- **审计日志**：合规追溯 · 长保留期 · 全量不可改 · **后端拦截器自动产生**（前端不知道最终是否落库成功，且易被篡改；前端上报只作"业务意图"补充）
- 推进顺序：**S1 后端骨架（已完成）→ S2 OpenAPI 契约 → S3 日志服务垂直切片**
- S1 刻意不上 Nacos/网关/Feign/Sentinel，等真需要跨服务调用再引

## 六条铁律
1. 两端不共用构建体系（pnpm / Maven 互不侵入）
2. 跨端只认 OpenAPI 契约；前端 SDK 禁止手写
3. 跨端共享物只放 `docs/`
4. `packages/*` 不得反向依赖 `platform/*`
5. `platform-user` 与 `platform-admin` 互不引用，复用下沉到 `packages/`
6. 后端模块依赖禁止反向、禁止跨层跳跃

## 沟通偏好
- 舰长偏好可视化方案与可运行演示，讲方案配图优于纯文字
- 给方案时先说结论和理由，再给细节
