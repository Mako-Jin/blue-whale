# BlueWhale — 长期备忘

> 三类：**约定** · **花过代价的坑** · **ADR 索引**。论证在 `docs/decisions/`，流水在 `.workbuddy/memory/YYYY-MM-DD.md`；细节去这两处查，本文件只留索引与结论。

## 1 铁律与工作空间

- 分支 `main`；远端 **`github.com/Mako-Jin/blue-whale`**（2026-10-10 首推，现已转私有）。**分机布局差异**：另一台机器在 `F:\workspace\blue-whale`（在那台机器上旧址 `D:\workspace\BlueWhale` 已迁走）；**本机没有 F 盘**，工作空间仍是 `D:\workspace\BlueWhale`，Maven 本地仓库 `D:\repository\maven`
- `web/`（pnpm）· `server/`（Maven，包名 `com.bluewhale.sts`）· `docs/ scripts/ infra/`（横向层）
- 结构范本（舰长指定）：`D:\workspace\platform-terminator`（前端）、`D:\workspace\SystemTerminator`（后端）
- **六条铁律**：① 两端不共用构建 ② 跨端只认 OpenAPI 契约（前端 SDK 禁手写）③ 跨端共享物只放 `docs/` ④ `packages/*` 不反向依赖 `platform/*` ⑤ `platform-user` / `admin` 互不引用，复用下沉 `packages/` ⑥ 后端禁反向依赖与跨层跳跃
- **每步完善文档**：对照表 `docs/README.md`；改代码同步改文档，同一次提交。Husky 脚本在 `web/.husky`（自动装报 `spawn git EBUSY`）；`.gitattributes` 强制 LF；Conventional Commits
- **当前工作空间 `core.hooksPath` 未设置** → 钩子**不生效**，`pnpm lint-staged` / `commitlint` 都不跑（只有 `.sample`）。提交信息全靠手工守规，别以为被拦过
- HTTPS 凭据在 `~/.git-credentials`（store 助手）；**绝不要用命令行解析该文件**（`cut` / `sed` / `grep` / `cat` 都会把明文令牌打进输出，已踩过）；`helper-selector` 助手在非交互环境会卡住 → 无人值守操作加 `-c credential.helper= -c credential.helper=store`

## 2 坑表（已查清，勿再排查）

**环境** —— 一律用 **Bash**（PowerShell 在本沙箱不可用）· Node `spawn git`→`EBUSY`、`turbo run`→`os error 231`（逐包直跑正常）· 后端报 Port 56491 → 沙箱注入 `SERVER__PORT`，用 `--server.port=8080` 覆盖 · `curl localhost` 报 upstream connect failed → 加 `--noproxy '*'` · dev server 用 `&` 会被杀 → 长驻服务必须后台任务起 · 废弃 Chrome 占 CDP 端口 → 换端口 / `taskkill`

**后端** —— 构建必须 `JAVA_HOME="D:/tools/installer/jdk/java21" mvn …`（PATH 上是 JDK 17；权限域另记 `D:/java`，两处都以 `mvn -v` 实测为准）· `mvn clean` 报文件占用 → `jps -l` 找 PID + `taskkill //F //PID` · Maven 本地仓库分机不同：一台在 **`F:\respository\maven`（拼写是 respository）**、Maven 装在 `D:\maven`；本机在 `D:\repository\maven`

**前端** —— stylelint `extends` 须 `require.resolve` 转绝对路径 · TS 6 起 `baseUrl` 弃用 · ESLint 10 flat config 须默认导出数组 · ESLint 只从 cwd 向上找配置 → 工作区根也要 `eslint.config.js` · typescript-eslint 须显式 `tsconfigRootDir`

## 3 ADR 索引（正文在 `docs/decisions/`）

0001 架构基线与选型 · 0002 公共包治理 · 0003 配置分域 · 0004 日志上报 · 0005 后端模块划分 · 0006 契约生成与消费 · 0007 请求分层 · 0008 Boot4 与 JDK21 门禁 · 0009 多数据源与 Flyway · 0010 达梦 DM8 · 0011 行为事件内核 · 0012 微前端 · 0013 WebGL 生命周期 · 0014 多场景 · 0015 场景交互 · 0016 权限域服务 · **0017 大气与天气**

## 4 技术栈与前端结构

React 19 + Vite 8 + TS **6.0.3**（7.x 不被 typescript-eslint 支持）+ Router 7；qiankun 2.x；pnpm + Turborepo；Less；ESLint 10 + Prettier 3 + Stylelint 17 + commitlint / Husky / lint-staged + Vitest 5。后端 Java 21（enforcer）+ Boot 4.1.1（Spring 7 / Tomcat 11 / Jackson 3）+ MyBatis-Plus 3.5.17。契约 OpenAPI 3.1（`docs/api/`，springdoc 3.1.1）。

包（端口）：`common-config` · `common-tools`:3100 · `common-api` · `portal`:3000 · `log-console`:3200 · `event-console`:3300 · `environment`:3400；`elemental/` 预留。

- `common-{theme,components}`、`platform-{user,admin}` **按需再建，不空建**
- 规范只在 `common-config` 一份（stylelint 一行 re-export；eslint 调 `createEslintConfig()`）
- **各应用 LESS 令牌各自一份，禁跨应用引用**（引了只有 `vite build` 报）
- `pnpm-workspace.yaml` 的 `catalog:` 是版本唯一来源，各包写 `"catalog:"`；**不建「存放依赖的包」**（生幽灵依赖）；公共码按能力建 `packages/common-<能力>`（ADR-0002）

## 5 后端（ADR-0005 / 0008 / 0009 / 0010）

依赖自上而下：`sts-app → sts-server → sts-components → sts-common → sts-parent`（零代码，`<version>` 只在这里）；`sts-app-server` 唯一可启动；`sts-common-{basic,web,db}`；`sts-server-{log,track,auth}`；groupId 按层分级。

- 模块内**按特性分包**（`clientlog/` 下 Controller/Service/Mapper/VO），不用 controller/service 顶层分包
- **业务失败也返 HTTP 200**，只由 `code` 表达（前端判 `code === 0`）；`R.isSuccess()` 须 `@JsonIgnore`
- Boot 4：starter 三处同动（`web→webmvc`、`boot3-starter→boot4-starter`、springdoc `2.x→3.x`）；MockMvc 另加 `spring-boot-starter-webmvc-test`
- Jackson 3：import 认 `tools.jackson.*`，注解仍在 `com.fasterxml.jackson.annotation`
- enforcer `requireJavaVersion [21,)`；message 禁写 `${java.version}`（被插值吃掉）

**数据源**：三 profile `h2`(默认) / `mysql`(8.0.26) / `dameng`；切换 `BW_DB_PASSWORD=… --spring.profiles.active=mysql`，密码不进 git；库名 `bluewhale`；本机别的项目 11 个库勿动。

- **建表 = Flyway 12**：`db/migration/{h2,mysql,dameng}/V<n>__x.sql`；**不写 `IF NOT EXISTS`**；**已执行脚本不可改**（校验和）；**版本号全仓唯一**（log=V1、track=V2、auth=V3）
- 三个必踩依赖坑：`flyway-mysql` 须显式引（Flyway 10 起 DB 支持模块化）· `mysql-connector-j` runtime 放 `sts-app-server` · 达梦 `com.dameng:DmJdbcDriver18`（**BOM 不纳管**，`sts-parent` 收口，驱动类 `dm.jdbc.driver.DmDriver`）
- **MySQL 保留字**：避开 `release` / `rank` / `groups` / `system` / `window`；只改列名没用 —— MP 见「列名 ≠ 属性名」会补 `列名 AS 属性名`，别名照样 1064 → 正解**属性名与列名同构**且刻意不加 `@TableField`（`appRelease` ↔ `app_release`，对外仍叫 `release`）；守卫 `ClientLogEntityMappingTest`
- **达梦**：MP 原生内置 `DbType.DM`（URL `jdbc:dm:`）· Flyway 官方无达梦模块 → `flyway.enabled=false`，DDL 走 `scripts/init-dameng.sh` · `IDENTITY(1,1)`、**`VARCHAR(n CHAR)`**（默认按字节，中文报「数据超长」）、标识符转大写 · 保留字两库不可互推 · `#{it.xxx}` OGNL 编译器管不到，改属性名要全局搜 · **未用真实达梦实例验证过**

**权限域 `sts-server-auth`（ADR-0016）** —— 参照 `F:\workspace\SystemTerminator\sts-server\sts-server-auth` 重写（参照物只读）。它的问题：只有模型没有入口（无注解/拦截器/AOP）、9 张表有 PO 无 DDL、`auth_tbl_tenant_info` 与 DDL 撞名、错误码撞码、两套上下文并存。

- 表 `bw_auth_*` 共 12 张，DDL 是 **V3**（h2/mysql/dameng 三份）；`deleted TINYINT DEFAULT 0`（0 正常）；避保留字用 `sort_no` / `tenant_level` / `org_level` / `role_level` / `resource_version`
- 错误码段 `40001~40913`（`40101~40111` 认证 · `40301` 无权限 · `409xx` 冲突）
- **自研零依赖**：HS512 JWT（`JwtCodec`）+ PBKDF2-SHA256（`PasswordHasher`），**不引 Spring Security**（本轮只要认证+鉴权入口）
- 入口：`@RequiresPermission` + `AuthInterceptor`（`findMergedAnnotation`）+ `PermissionChecker`（请求内缓存）；`AuthTokenFilter` **只解析不拒绝**；上下文只有 `AuthContext` 一套
- 刷新令牌**只存 SHA-256 摘要** + 轮换 + 重放检测（已作废 jti 复用 → 撤销该用户全部刷新令牌）；登录不区分「账号不存在/口令错」
- 已交付特性：认证（login/refresh/logout/me）、租户、用户；**组织/角色/用户组/资源的管理接口未做**（实体/Mapper 已就绪）
- 坑：Jackson 3 下 `encode(Map)` 接不了 `byte[]`（签名要单列 `encodeBytes(byte[])`）；`AuthTokenFilter` 不能既 `@Component` 又进 `FilterRegistrationBean`（会双注册）；`BaseEntity` 不给字段加 `@TableField` 列名；`RefreshTokenEntity` 不继承 `BaseEntity`
- **构建**：`JAVA_HOME="D:/java" mvn clean install`；Maven 本地仓库 `F:\respository\maven`（**拼写是 respository**）；Maven 在 `D:\maven`

## 6 契约与请求（ADR-0006 / 0007）

链：代码 → springdoc → `scripts/export-openapi.sh`（:18080）→ `docs/api/openapi.json` → `openapi-typescript` → `common-api/src/generated/schema.d.ts`（生成物入库）。`common-tools/http` = 传输内核（不认业务接口），`common-api` = 契约装配（只接线）。三件收口：解包 `R<T>`、`code!==0` 抛异常、超时+取消合并一个 AbortController；六类错误 `business/http/network/timeout/abort/parse` + `retryable`；**内核不自动重试**；**超时必须先于取消分类**；`traceIdProvider` 注入 X-Trace-Id。`LogReporter`：批量 20 / 3s / 缓冲 500 / 退避封顶 30s。

## 7 日志与行为事件（ADR-0004 / 0011）

三管道并列：**技术日志**（可采样 · 短保留 · 前端驱动）· **审计日志**（全量不可改 · 后端拦截器自动产生）· **行为事件**（不可采样 · 契约极稳定）；内容不同 ⇒ 独立模块独立表，通道相同 ⇒ 复用 `common-tools/json` + `report`。

- logger：7 级 + **过滤前置于格式化**；渲染三态 css（`%c`）/ ansi / plain；**上报必须走 `onRecord`**（`LogSink` 吃的是渲染后有损的样子）；`onRecord` 在过滤后、格式化前派发，内部异常静默吞
- 事件：语义化 `track()`，事件名正则 `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`（前后端同一条）· **登录先 updateContext 再 track；登出先 track 再 clearUser()** · 双时间戳（`serverTime` 是排序过滤唯一依据）· `event_id` 不建唯一索引 · `deviceId` + `userId` 同时挂 · 卸载用 `fetch(keepalive)` **不用** `sendBeacon`（64KB 上限，按体积裁剪保最近批次）
- **未做**：采样 · 服务端二次脱敏 · 事件注册表 · Kafka/ClickHouse · 生命周期 · 同意管理与按用户删除

## 8 配置分域（ADR-0003）

三层：密钥级（Vault/KMS）· 部署级（Nacos/Apollo）· 业务运行期（自研，走 OpenAPI 契约）；**自研只有第三层**。客户端五底线：首屏零阻塞 · 兜底默认在客户端 · 版本化+缓存+变更通知 · 有 schema 与类型 · 环境物理隔离。禁止：万能配置接口、命名风格并存、无变更审计。

## 9 微前端（ADR-0012）

基座用 **`loadMicroApp`**（非 `registerMicroApps`+`start`）；**`sandbox: false`**；子应用接入 `vite-plugin-qiankun`（根因：`import-html-entry` 用 `eval` 执行脚本文本，module script 跑不了）。

- **应用名是隐式契约**：基座 `micro/apps.ts` 与子应用 `qiankun(APP_NAME,…)` 逐字一致；不一致**只卡「加载中」不报错**
- 子应用 `base` 分环境：dev 必须 `/`（否则热更新**静默失效**），build 才带 `/log-console/`；须 `strictPort: true` + `cors: true`
- `initGlobalState` **不返回** `getGlobalState` → 基座自留 `currentState` 副本
- 通信全走 `common-tools/micro`，**不 import qiankun**；**订阅必须走 `onHostGlobalStateChange()`**（qiankun 每子应用只存一个回调，二次注册静默覆盖）；`unbindHost` **先取父节点再清 `hostProps`**
- 样式隔离指望不上框架；**已知限制**：被基座加载时子应用 React 组件级热更新不生效 → 改组件时单独跑子应用
- 未做：`platform-user` / `admin` · 生产部署与网关规则 · 样式前缀去重 · 子应用错误边界

## 10 三维环境（ADR-0013 / 0014 / 0015 / 0017，:3400）

- **画布与上下文同生共死（最关键）**：canvas 由 `createStage` 创建并随 `dispose()` 移除，**不接受外部传入**；`dispose()` 必须含 `forceContextLoss()` —— 否则 StrictMode 双跑 effect 必抛 `reading 'precision'` of null；不加则上下文攒到 ~16 个被回收 → 「画面变白且无报错」
- GPU 资源不随 GC 释放：`disposeSubtree()` 递归；**PMREMGenerator / RoomEnvironment 不在场景图里，须各自释放**
- three r186：`PCFSoftShadowMap` 已移除（静默退回）· `Clock` 弃用改核心 `Timer`（`connect(document)` 借 Page Visibility，`dispose()` 断开）
- 多场景：**一个场景 = 实现（`scenes/*.ts`，后端下发不了）+ 元数据（`SceneMeta`）** ⇒ 新增场景必发前端版本，当前用**前端注册表**；三条护栏 = 只认 `loadSceneCatalog()`（`localSceneCatalog()` 同步兜底）· `mergeCatalogOverrides()` **只覆盖不发明** · `SceneId` 联合 + `Record<SceneId, SceneDefinition>` ⇒ 编译期兜错；选择器用**卡片不用下拉框**
- **切换 = 只换场景图**（渲染器 / 画布 / 相机 / 控制器 / PMREM 全复用），**先释放旧、后建新**（反了峰值显存翻倍）；**切场景要重置渲染选项**；散布用 `createPrng(seed)` 不用 `Math.random()`；`stage.diagnostics()` 可断言释放
- 可操控场景：`SceneBuild.createControls?(context)`（**交互是场景的能力不是舞台的**）；**相机跟随必须整体平移**（`syncFocus` 里 `delta = focus - orbit.target`，控制器不得直改相机位姿）；左键 / 滚轮仍归 OrbitControls（有控制器时 `orbit.enablePan=false`）
- 移动算法只在 **`player/motor.ts`**（纯内核，不碰 three / DOM）；**角度约定全应用统一**：`0` = 朝 +Z、方向 `(sin a, cos a)`、世界 -Z 是北、`right = forward × up ⇒ (-cos a, sin a)`（符号错的症状：按上往后走、小地图东在西边）
- 小地图**自绘 canvas 不走 React 状态**（60fps setState 不可接受）；投影等纯函数在 `scene/minimap.ts`；地物与碰撞矩形**同源**；右键射线打 y=0 地面；画布尺寸 0 返回 null
- 验证脚本：`verify-micro-frontend.mjs` :9333 · `verify-scene-switching.mjs` :9334 · `verify-player-controls.mjs` :9335 · `verify-atmosphere.mjs` :9336（浏览器自动探测 Chrome/Edge）
- **大气（ADR-0017，一期）**：环境内核四层纯函数 `solar/palette/sample/rig`；**高度角是唯一自变量**；太阳方位用 `atan2` 不用 `acos`；夜间主光取反指向反日点（`MIN_KEY_ELEVATION` 防阴影视锥退化）；**雾距给系数不给绝对值**；天空盒用 three r186 `Sky`（**自带云层**，uniforms `cloudCoverage/cloudSpeed/time`）；动态 IBL = 纯天穹捕获（整棵场景图会"建筑照亮自己"）+ 双重节流 + **每帧补检查**（防节流窗口漏掉最后一次变化）；天穹两份实例（主 + 捕获）；`SceneClimate` 必填，**`outdoor` ↔ 实现 `createAtmosphere` 一一对应**（古建筑/丛林/写字楼三个都挂；测试双向钉住）。**写断言别把进度当契约** —— "只有 X 实现了能力"会在别人按契约补齐时变红，逼后人删掉护栏；要断言的是方向（"所有 outdoor 都得有"）；**切场景泄漏阴影贴图**：`disposeSubtree` 必须放 `light.shadow.dispose()`——贴图不在 geometry/material 之列，实测每切一次净增 2 张纹理；**IBL 烘焙关太阳圆盘**用 `showSunDisc=0`（官方开关；别用 sunPosition 藏——会把散射分布一起改成暮色）；镜面过曝的大头是**天空散射的 HDR 亮度**而非圆盘（PMREM 宽卷积核会稀释小立体角），`environmentIntensity` 换 IBL 来源后必须对真实天空重标（写字楼 0.85→0.45）
- **⚠️ 验证脚本必须按「帧」等，不按毫秒**：`setEnvironment()` 同步改光但画面下帧才变；无头 `--use-gl=swiftshader` **单帧 2~4 秒（≈0.5 fps）**，`sleep(650)` 会稳定截到上一帧 → 症状是"四个时刻天空颜色一样"，其实测量错了。判据用 `diagnostics().frames`（只数真正渲染的帧）；测速率要**先对齐帧边界**且窗口同时满足"≥3 帧 且 ≥3 秒"，`localHours` 跨午夜要取模回绕
- 未做：寻路 · 移动动画 · 遮挡精度 · 小地图交互 · 移动端虚拟摇杆 · **雨雪霜天气状态机 · 风向量 · 夜空星点与月亮圆盘**

## 11 沟通偏好

舰长偏好可视化与可运行演示，讲方案配图优于纯文字；先给结论和理由，再给细节。
