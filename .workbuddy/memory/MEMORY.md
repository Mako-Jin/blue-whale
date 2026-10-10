# BlueWhale — 项目长期备忘

> 只留三类：**必须遵守的约定** · **花过代价的坑** · **ADR 索引**。
> 论证过程在 `docs/decisions/`，日常流水在 `.workbuddy/memory/YYYY-MM-DD.md`。
> 越靠前越常用；尾部截断不会伤到约定与坑。

## 1 定位与铁律

全栈前后端分离。工作空间 `D:\workspace\BlueWhale`，分支 `main`。

- `web/`（前端·pnpm）· `server/`（后端·Maven，包名 `com.bluewhale.sts`）· `docs/ scripts/ infra/`（横向层）
- 结构范本（舰长指定）：`D:\workspace\platform-terminator`（前端 monorepo / qiankun / React）、`D:\workspace\SystemTerminator`（后端 Maven 多模块 `com.yaocode.sts`）
- **六条铁律**：① 两端不共用构建体系 ② 跨端只认 OpenAPI 契约（前端 SDK 禁手写）③ 跨端共享物只放 `docs/` ④ `packages/*` 不得反向依赖 `platform/*` ⑤ `platform-user` 与 `platform-admin` 互不引用，复用下沉 `packages/` ⑥ 后端模块禁止反向依赖与跨层跳跃

## 2 Git 与文档

- Husky 在 `web/.husky`，用 `core.hooksPath` 挂载（沙箱下自动安装会 `spawn git EBUSY` → 手动挂钩）；脚本内先 `cd "$(git rev-parse --show-toplevel)/web"`
- `.gitattributes` 强制 LF；`blender_mcp/` 已忽略；Conventional Commits
- **舰长要求：每一步都完善文档**。对照表 `docs/README.md`，ADR `docs/decisions/`，约定 `CONTRIBUTING.md` / `AGENTS.md`。**改代码必须同步改文档，同一次提交完成**

## 3 坑表（已查清，勿再排查）

**环境** —— **PowerShell 工具在本沙箱不可用**（`ConPTY` 建沙箱失败）→ 一律用 Bash · Node `spawn git` 报 `EBUSY`、`turbo run` 报 `os error 231`（环境限制，逐包直跑脚本均通过）· 后端启动报 Port 56491 → 沙箱注入了 `SERVER__PORT`，用 `--server.port=8080` 覆盖 · `curl localhost` 报 upstream connect failed → 加 `--noproxy '*'` · **dev server 在 Bash 调用里用 `&` 会被杀**（端口变 000、页面空载，症状是端到端脚本报「舞台未就绪」）→ 长驻服务必须以后台任务方式起 · 废弃的 Chrome 会占住 CDP 调试端口 → 换端口或 `taskkill` 清 PID

**后端** —— 构建必须 `JAVA_HOME="D:/tools/installer/jdk/java21" mvn …`（PATH 上是 JDK 17）· `mvn clean` 报「另一个程序正在使用此文件」→ `jps -l` 找 PID 后 `taskkill //F //PID <pid>` · Maven 本地仓库在 **`D:\repository\maven`**（不是 `~/.m2`）

**前端** —— stylelint 的 `extends` 必须 `require.resolve` 转绝对路径（pnpm 隔离）· TS 6 起 `baseUrl` 弃用 · ESLint 10 要求 flat config **默认导出数组**（不能导出函数）· ESLint 只从**当前工作目录**向上找配置 → 工作区根也要有 `eslint.config.js` · typescript-eslint 必须显式 `tsconfigRootDir`（`projectService: false` 无效）

## 4 ADR 索引（正文都在 `docs/decisions/`）

0001 架构基线与技术选型 · 0002 公共包与公共依赖治理 · 0003 配置分域与自研边界 · 0004 前端日志上报 · 0005 后端模块划分与依赖约束 · 0006 接口契约的生成与消费 · 0007 前端请求分层与错误模型 · 0008 Spring Boot 4 升级与 JDK 21 门禁 · 0009 多数据源共存与 Flyway 迁移 · 0010 达梦 DM8 支持与边界 · 0011 行为事件管道与埋点内核 · 0012 微前端接入与子应用改造 · 0013 WebGL 渲染生命周期 · 0014 多场景架构与切换管线 · 0015 场景交互与主人公操控

## 5 技术栈与前端结构

- 前端 React 19 + Vite 8 + TS **6.0.3**（7.x 不被 typescript-eslint 支持）+ React Router 7；qiankun 2.x；pnpm workspace + Turborepo；Less
- 规范 ESLint 10（flat config）+ Prettier 3 + Stylelint 17 + commitlint / Husky / lint-staged + Vitest 5
- 后端 Java 21（enforcer）+ Spring Boot 4.1.1（Spring 7 · Tomcat 11 · Jackson 3）+ MyBatis-Plus 3.5.17
- 契约 OpenAPI 3.1（`docs/api/`，springdoc 3.1.1 生成）

`packages/`（公共包 `common-*`）· `platform/`（应用）· `elemental/`（预留）

| 包                       | 说明                                                 | 端口             |
| ------------------------ | ---------------------------------------------------- | ---------------- |
| `packages/common-config` | 共享工程配置（规范唯一来源）                         | —                |
| `packages/common-tools`  | logger / env / http / json / report / events / micro | playground :3100 |
| `packages/common-api`    | 契约装配（只接线）                                   | —                |
| `platform-portal`        | qiankun 基座                                         | :3000            |
| `platform-log-console`   | 技术日志演示                                         | :3200            |
| `platform-event-console` | 行为事件演示                                         | :3300            |
| `platform-environment`   | 三维环境（three.js）                                 | :3400            |

- 规划的 `common-{theme,components}`、`platform-{user,admin}` **按需再建，不空建**
- 规范只在 `common-config` 维护一份：子项目 `stylelint.config.js` 一行 re-export；`eslint.config.js` 须调 `createEslintConfig()`
- **各应用 LESS 令牌各自一份**（`src/styles/variables.less`），**禁止跨应用引用**（引了只有 `vite build` 报）
- `web/pnpm-workspace.yaml` 的 `catalog:` 是依赖版本唯一来源，各 `package.json` 写 `"catalog:"`；**不建「存放依赖的包」**（会生幽灵依赖）；公共代码按能力建 `packages/common-<能力>`，先问「是否只有一个应用会用」（ADR-0002）

## 6 后端（ADR-0005 / 0008 / 0009 / 0010）

依赖只能自上而下：`sts-app` → `sts-server` → `sts-components` → `sts-common` → `sts-parent`（零代码，版本号只出现一次）。

```
server/ pom.xml(聚合) → sts-parent
  → sts-common/{basic, web, db}
  → sts-server/{sts-server-log, sts-server-track}
  → sts-app/sts-app-server（唯一可启动进程）
```

- groupId **按层分级**；`<version>` 只在 `sts-parent`
- `sts-common-basic`：`R<T>` / `ResultCode`（5 位 = HTTP 3 位 + 业务 2 位）/ `BusinessException`；**刻意零依赖**（唯一例外 `jackson-annotations` provided）
- `sts-common-web`：`GlobalExceptionHandler` + CORS + 自动配置，依赖即生效
- `sts-common-db`：MyBatis-Plus 拦截器（`mybatis-plus-spring-boot4-starter`）；**分页 / 防全表更新需另引 `mybatis-plus-jsqlparser`**
- **模块内按特性分包**（`clientlog/` 下 Controller/Service/Mapper/VO），不用 `controller/service/repository` 顶层分包；`sts-components/` 未创建
- **业务失败也返回 HTTP 200**，只由 `code` 表达（前端判 `code === 0`）；`R.isSuccess()` 必须 `@JsonIgnore`
- **Boot 4 升级**：starter 坐标三处一起动（`web→webmvc`、`boot3-starter→boot4-starter`、springdoc `2.x→3.x`，2.x 配 Boot 4 启动即失败）；MockMvc 需另加 `spring-boot-starter-webmvc-test`，注解换包到 `…boot.webmvc.test.autoconfigure`
- **Jackson 3**：databind/core 迁到 `tools.jackson.*`，**注解仍在** `com.fasterxml.jackson.annotation`；classpath 上 Jackson 2 仍在（springdoc/MyBatis 传递），import 认准 tools.jackson
- **enforcer** `requireJavaVersion [21,)` 继承到所有子模块；message 里**禁写 `${java.version}`**（被 Maven 插值吃掉，取到 POM 的 21，提示自相矛盾）
- 契约升 3.1：任意值字段 `Record<string, never>` → `unknown`；分页 records 元素类型裂缝用 `WithJsonRecords` 修好

### 数据源

- 三 profile：`h2`（默认，内存库，零依赖可跑）/ `mysql`（本机 8.0.26）/ `dameng`（达梦 DM8）。切换是**启动参数** `BW_DB_PASSWORD=xxx … --spring.profiles.active=mysql`；密码不进 git。库名 `bluewhale`；本机另有别的项目 11 个库（`yc-sts-db-*` / `sts-db-*`）**勿动**
- **建表 = Flyway 12**：脚本在表所属模块 `src/main/resources/db/migration/{h2,mysql}/V<n>__xxx.sql`，`spring.flyway.locations` 按 profile 指目录；**不写 `IF NOT EXISTS`**；**执行过的脚本不能改**（校验和不匹配拒绝启动）
- `#{it.xxx}` 这类 OGNL 引用**编译器管不到**，改实体属性名后要全局搜一遍
- **三个必踩依赖坑**（H2 正常、切库才炸）：① `flyway-mysql` 必须显式引（Flyway 10 起 DB 支持模块化，core 只内置 H2/SQLite，缺了报 `Unsupported Database: MySQL 8.0`）② `mysql-connector-j` runtime 范围，放 `sts-app-server` ③ 达梦 `com.dameng:DmJdbcDriver18`（**Boot 的 BOM 不纳管**，`sts-parent` 收口），驱动类 `dm.jdbc.driver.DmDriver`
- **MySQL 保留字（最重要）**：列名避开 `release` / `rank` / `groups` / `system` / `window`；**不能只改列名** —— MP 见「列名 ≠ 属性名」会自动补 `列名 AS 属性名`，别名撞保留字照样 1064。正解：**属性名与列名同构**（`appRelease` → `app_release`），**刻意不加 `@TableField`**，对外仍叫 `release`；守卫 `ClientLogEntityMappingTest` 对生成的 SQL 直接断言
- **达梦**：MP 原生内置 `DbType.DM`（`:dm\d*:` 匹配 URL）→ 分页 / 防全表更新零改动；枚举里还有 KINGBASE_ES / OPENGAUSS / GAUSS / OCEAN_BASE / XU_GU / SINODB，**换库先查这张表**。**Flyway 官方无达梦模块** → `flyway.enabled=false`，DDL 走 `scripts/init-dameng.sh`。方言 `IDENTITY(1,1)`、**`VARCHAR(n CHAR)`（默认按字节，中文会「数据超长」）**、`COMMENT ON COLUMN` 独立语句、标识符不加引号转大写。保留字**两库不可互推**（`RELEASE` 达梦不认、`LEVEL` 是达梦非保留关键字）。`DatasourceDialectTest` 钉 URL → DbType；**未用真实达梦实例验证过**

## 7 契约与前端请求（ADR-0006 / 0007）

- 链：代码 → springdoc → `scripts/export-openapi.sh`（:18080）→ `docs/api/openapi.json` → `openapi-typescript` → `common-api/src/generated/schema.d.ts`；生成物**入库**
- 路径常量 `satisfies keyof paths`；`startTime` 等字段用 `expectTypeOf` 钉住（springdoc 会把 query 位带 `@Schema` 的 `Long` 推平成 string）。坑：`Object` 字段渲染成 `Record<string, never>` → `common-api/types.ts` 的 `JsonValue` 只修正 `data`/`context`/`logs`
- `common-tools/http` = 传输内核（不认识业务接口）；`common-api` = 契约装配（只接线）
- 三件收口：解包 `R<T>`、`code!==0` 抛异常、超时+取消合并一个 AbortController；六类错误 `business/http/network/timeout/abort/parse` + `retryable`；**内核不做自动重试**（幂等边界属业务语义）；**超时必须先于取消分类**（都是 abort 实现）；`traceIdProvider` 注入 X-Trace-Id，不自动生成
- `LogReporter`：批量 20 / 3s / 缓冲 500 / 退避封顶 30s / 不抛异常不自我记录

## 8 日志与行为事件（ADR-0004 / 0011）

三条管道并列：**技术日志**（排查故障 · 可采样 · 短保留 · 前端驱动）· **审计日志**（合规 · 全量不可改 · **后端拦截器自动产生**，前端上报只作「业务意图」补充）· **行为事件**（分析用户 · **不可采样** · 契约极稳定）。**内容不同 ⇒ 独立模块独立表；通道相同 ⇒ 复用** `common-tools/json` + `common-tools/report`。

**logger**（`common-tools`）

- 7 级（trace/debug/info/success/perf/warn/error）+ 阈值过滤，**过滤前置于格式化**；映射 `console.debug/log/warn/error`；渲染三态 `css`（浏览器 `%c`）/ `ansi` / `plain`（管道、`NO_COLOR`）；`child()` / `withContext()` / 主题深合并；消息里 `%o`/`%c` 必须转义（零宽空格隔断）。参考实现共修复 16 项，见 `web/packages/common-tools/README.md`
- **两个钩子分工不可混**：`LogSink` 吃**渲染后的样子**（含 `%c`）→ 控制台 / 测试捕获 / 页面镜像；`onRecord` 吃**结构化事实** `LogRecord` → 上报 / 落库 / 聚合。**上报必须走 `onRecord`**（sink 级别有损，`info`/`success` 都压成 `'log'`）；`onRecord` 派发在**过滤之后、格式化之前**，内部异常**静默吞掉**
- `LogRecord` = `level/namespace/timestamp/message/data/context` + `traceId/sessionId/route/release`；服务端聚合与查询已实现（`sts-server-log`）；**采样与脱敏未做**

**行为事件**

- 形态 **语义化 `track()` + 内部总线**（不做通用全局事件总线）；事件名契约 `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`，前后端**同一条正则**，非法名丢弃且只报一次；中间件（补上下文 / 屏蔽 / 脱敏）与多订阅者是内核内部实现
- **登录先 updateContext 再 track；登出先 track 再 clearUser()**（反了不报错，只丢「谁登出了」）
- 边界：**双时间戳**（`clientTime` 不可信仅展示 / `serverTime` 是排序过滤唯一依据）· `event_id` **不建唯一索引**（一条重复不能让整批 INSERT 失败）· `propsVersion` **按次传** · 无 `traceId` · `deviceId` + `userId` **同时挂**（否则漏斗口底对不上）
- 卸载兜底用 `fetch(keepalive)` **不用** `sendBeacon`（发 JSON 触发预检 → 静默失败）；上限 64KB，按体积裁剪且优先保住最近批次
- `EventEnvelope.clientTime` ↔ 契约 `ClientEventItem.timestamp`，改名在接线层 `toReportItem`（**写错不报错，只有该列为空**）
- 聚合接口**刻意不接受 `eventName` 过滤**（它本身按事件名分组），返回 `eventNameCount`；表 `bw_client_event`（维度列 + `props_json`）
- **未做**：采样 · 服务端二次脱敏 · 事件注册表 · Kafka/ClickHouse · 生命周期 · 同意管理与按用户删除 · 服务端 propsVersion 校验 · 达梦事件表验证
- 两个 demo（`platform-log-console` :3200 / `platform-event-console` :3300）：左右分栏「客户端视角 / 服务端视角」（只看一边无法判断上报成没成）；`emit` 不能写 `logger[level](...)`（`perf` 签名是 `(label,value,unit)`，须显式 switch）

## 9 配置分域（ADR-0003）

- 三层：**密钥级**（Vault/KMS）· **部署级**（Nacos/Apollo）· **业务运行期**（自研，走 OpenAPI 契约）；**自研范围只有业务运行期配置**
- 客户端五条底线：首屏零阻塞、兜底默认值在客户端、版本化+缓存+变更通知、配置项有 schema 与类型、环境物理隔离
- 禁止：万能配置接口（长成上帝对象）、命名风格并存、无变更审计、两端各自解释语义

## 10 微前端（ADR-0012）

- 基座用 **`loadMicroApp`** 而非 `registerMicroApps` + `start`（挂载点由路由出口给，位置唯一、无竞态）；**`sandbox: false`**
- 子应用接入 **`vite-plugin-qiankun`**；根因是 `import-html-entry` 用 `eval` 执行脚本文本 → module script 跑不了
- **应用名是隐式契约**：基座 `micro/apps.ts` 的 `name` 与子应用 `qiankun(APP_NAME,…)` 逐字一致；不一致**不报错、只卡「加载中」**
- 子应用 `base` **分环境**：dev 必须 `/`（否则 `useDevMode` 定位不到 `/@vite/client`，热更新**静默失效**），build 才带 `/log-console/` 等；须 `strictPort: true` + `cors: true`
- **`initGlobalState` 不返回 `getGlobalState`** → 基座自留 `currentState` 副本。（参考项目把不存在的 `actions.getGlobalState` 传给子应用，是**真 bug**）
- 通信全走 **`common-tools/micro`**（`bindHost` / `unbindHost` / `getHostGlobalState`），**不 import qiankun**；子应用之间禁止互相引用 ⇒ 只能下沉 `packages/`
  - **订阅必须走桥的 `onHostGlobalStateChange()`**：qiankun 每个子应用**只保存一个回调**，第二次注册会静默覆盖第一个（实际踩过）；桥在 `bindHost` 注册一次原生回调再分发，`unbindHost` 清空订阅者
  - `unbindHost` **先取父节点再清 `hostProps`**，反了会退化成 body
- `common-config/vite` 的 `qiankunDevPreamble()` 修 Fast Refresh preamble（`@vitejs/plugin-react` 塞的内联 module 脚本被 `eval` 后抛 `Cannot use import statement outside a module`）
- 样式隔离**指望不上框架**（`experimentalStyleIsolation` 依赖 `<style>` 在入口 HTML 里，Vite dev 由 JS 插入 `document.head`）
- **已知限制（根因未定位）**：被基座加载时子应用 **React 组件级热更新不生效**（样式热更新正常、独立运行正常）→ 改组件时单独跑子应用
- 验证 `scripts/verify-micro-frontend.mjs`（CDP :9333）；**未做**：`platform-user` / `platform-admin` · 生产部署目录与网关规则 · 样式前缀去重 · 子应用错误边界

## 11 三维环境 platform-environment（ADR-0013 / 0014 / 0015）

（:3400，`/environment`）与任何业务域无关；three.js + `@types/three`（版本进 catalog）；产物 gzip 约 225 kB。`mount.ts` 的 `resolveMountPoint()` 支持「基座传入容器」与「独立自建容器」两种挂载。

- 分层：`scenes/*.ts` + `common.ts`（场景图 / 动画 / 工具，**不碰渲染器**）与 `renderer` / `stage` 分开（WebGLRenderer 构造即要上下文，jsdom 直接抛错）→ 场景逻辑全部可单测
- **画布与上下文同生共死（最关键）**：canvas 由 `createStage` 创建并随 `dispose()` 移除，**不接受外部传入**；`dispose()` 必须含 `forceContextLoss()`。`forceContextLoss()` 后同张 canvas 上下文永久 lost，`getShaderPrecisionFormat()` 返回 null → 再建 renderer 抛 `Cannot read properties of null (reading 'precision')`（StrictMode 双跑 effect 必踩，jsdom 测不出）；不加则上下文攒到上限（~16 个）后被浏览器回收最旧的，表现为「切几次后画面变白且无报错」
- GPU 资源不随 GC 释放：`disposeSubtree()` 递归释放并可断言；**PMREMGenerator 与 RoomEnvironment 不在场景图里**，traverse 不到，必须各自释放
- three r186：**`PCFSoftShadowMap` 已移除**（写上只警告并静默退回）、**`Clock` 弃用**改用核心 `Timer`（`connect(document)` 借 Page Visibility 兜住切标签页的巨大 delta；`dispose()` 必须断开）

**多场景（ADR-0014）**

- **一个场景 = 实现（`scenes/*.ts` three.js 代码，后端下发不了）+ 元数据（`SceneMeta` 纯数据）** ⇒「新增场景必发前端版本」，后端字典当下净收益为 0；当前用**前端注册表**（ADR-0003：场景目录属「业务运行期配置」层，且内置兜底须永久在客户端）
- **三条护栏**：调用方只认 `loadSceneCatalog(): Promise<SceneCatalog>`（首屏 `localSceneCatalog()` 同步兜底）· `mergeCatalogOverrides()` **只能覆盖不能发明**（认不出的记 `unknownIds`）· `SceneId` 字面量联合 + `Record<SceneId, SceneDefinition>` ⇒ 漏实现 / 拼错 id 是**编译期**报错
- **选择器用卡片不用下拉框**（场景是视觉选项）；`SceneMeta.icon` 只是**枚举**，图形归组件渲染
- **切换 = 只换场景图**：释放旧 → 建新 → 相机搬到新取景位；渲染器 / 画布 / 相机 / 控制器 / PMREM 环境贴图**全部复用**；**先释放旧、后建新**（反了峰值显存是两份之和）
- **取景随场景走**：`SceneMeta` 自带相机位姿、拉近拉远范围、IBL 强度、默认选项覆盖；**切场景要重置渲染选项**（网格辅助线对广场是参照、对丛林是干扰）
- **散布禁用 `Math.random()`** → `createPrng(seed)`（mulberry32），否则不可复现不可断言；**释放可断言**（`stage.diagnostics()` 暴露 GPU 几何体 / 纹理计数；`scripts/verify-scene-switching.mjs`，CDP :9334）
- 方盒构件共用一个 `BoxGeometry` + `scale`（古建筑 127 绘制调用只 6 份几何体）；新增场景：`scenes/<id>.ts` → `types.ts` 加 `SceneId` 字面量 → `catalog.ts` 注册一行

**可操控场景（ADR-0015）**——古建筑：殿前广场 + 主人公（WASD/方向键 + 滚轮缩放 + 右键设终点）+ 左上角小地图（正北朝上、人物朝向箭头、相机视野扇形）

- **交互是场景的能力不是舞台的能力**：`SceneBuild.createControls?(context)` 可选契约；舞台管控制器生命周期（装载造 / 每帧驱动 / 切场景销毁），场景不碰帧循环
- **相机跟随必须「整体平移」**：控制器只上报 `focus()`，`stage.ts` 的 `syncFocus` 把相机与轨道目标挪**同样的位移**（`delta = focus - orbit.target`）；只改 target 会变成原地转头。控制器**不得直接改相机位姿**
- **左键 / 滚轮仍归 OrbitControls**，控制器只借走键盘与右键；有控制器的场景 `orbit.enablePan=false`（OrbitControls 的 contextmenu 只 preventDefault、键盘监听需显式 `listenToKeyEvents()`，故不冲突）
- **移动算法只在 `player/motor.ts`（纯内核，不碰 three 不碰 DOM）**：相机相对位移、目标点行走（撞障碍即清终点）、最短转向、边界夹取、沿穿透最浅的一边推出障碍。改手感只改这里
- **角度约定全应用统一**：`heading`/`azimuth` 绕 Y 轴、`0` = 朝 +Z、方向向量 `(sin a, cos a)`；世界 **-Z 是北**；相机右向量 `right = forward × up ⇒ (-cos a, sin a)`。符号写错的症状：按上往后走、小地图东在西边
- 小地图**自绘 canvas 不走 React 状态**（60fps setState 不可接受）；数据经 `stage.interaction()` 逐帧读，`SceneView` 的 `onReady` 把舞台交给 App；投影 / 方向 / 扇形 / 箭头在 `scene/minimap.ts` 全是纯函数；**世界→地图零角度补偿**（+X→右、+Z→下、-Z→上），地物与碰撞矩形**同源**（挡人的必须也是地图上画出来的）
- 右键射线打**地面平面 y=0**（不打网格）；画布尺寸 0 必须返回 null（防 NaN 进内核）
- 调参：广场 halfExtent 40 + 压边石 + 雾 26/85（近了人走远被雾洗白）+ 太阳阴影范围 30（小了走出范围影子无声消失）；障碍 = 台基 + 台阶；验证 `scripts/verify-player-controls.mjs`（CDP :9335）
- **未做**：寻路（直线过不去即放弃）、移动动画、遮挡精度、小地图交互、移动端虚拟摇杆

## 12 沟通偏好

- 舰长偏好可视化方案与可运行演示，讲方案配图优于纯文字；给方案时先说结论和理由，再给细节
