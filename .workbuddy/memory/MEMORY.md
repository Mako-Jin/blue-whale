# BlueWhale — 长期备忘

> 三类：**约定** · **坑** · **ADR 索引**。论证在 `docs/decisions/`，流水在 `.workbuddy/memory/*.md`，细节在各 README。舰长偏好可视化演示，先结论后细节。

## 1 铁律
- 分支 `main`；远端 `github.com/Mako-Jin/blue-whale`。`web/`(pnpm) · `server/`(Maven `com.bluewhale.sts`) · `docs/ scripts/ infra/`。范本 `platform-terminator` / `SystemTerminator`
- 六条：① 两端不共用构建 ② 跨端只认 OpenAPI 契约（前端 SDK 禁手写）③ 跨端共享物只放 `docs/` ④ `packages/*` 不反向依赖 `platform/*` ⑤ `platform-{user,admin}` 互不引用、复用下沉 `packages/` ⑥ 后端禁反向依赖与跨层跳跃
- **改代码必须同步改文档，同一次提交** · Husky 靠 `core.hooksPath web/.husky` · LF 强制 · Conventional Commits · 凭据在 `~/.git-credentials`，**绝不用命令行解析**；无人值守 git 加 `-c credential.helper= -c credential.helper=store`

## 2 坑表（勿再排查）
- **环境** 一律 Bash（PowerShell 沙箱不可用）· Node `spawn git`→EBUSY、`turbo run`→231（逐包直跑正常）· 端口 56491 → `--server.port=8080` · `curl` 加 `--noproxy '*'` · 长驻服务必须后台任务起（`&` 会被杀）· 废弃 Chrome 占 CDP → 换端口/taskkill · git 连接重置 → `-c http.version=HTTP/1.1` · MySQL 8.0.26 便携版 `D:\tools\installer\mysql`，**root 口令现场给、不落文件** · 系统 python3.11 坏 → 用受管 `…/binaries/python/versions/3.13.12/python.exe`
- **后端** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn …`（PATH 是 JDK17）· `mvn clean` 文件占用 → `jps -l` + `taskkill //F //PID` · Maven 仓库 `D:\repository\maven`
- **前端** stylelint `extends` 须 `require.resolve` 绝对路径 · TS6 起 `baseUrl` 弃用 · ESLint10 flat config **默认导出数组**、只从 cwd 向上找（工作区根也要有）· typescript-eslint 须显式 `tsconfigRootDir`
- **e2e 脚本** CDP 求值须容忍"求值那刻页面在导航"（回协议层错误）→ 重试；**浏览器侧报错必须让脚本非零退出**（子应用没起时 qiankun 抛 `Failed to fetch`，而每步仍能"读出" `undefined` 往下走）；基座有登录守卫，脚本**必须先真实登录**

## 3 ADR 索引
0001 架构基线 · 0002 公共包治理 · 0003 配置分域 · 0004 日志上报 · 0005 后端模块划分 · 0006 契约生成消费 · 0007 请求分层 · 0008 Boot4/JDK21 · 0009 多数据源与 Flyway · 0010 达梦 DM8 · 0011 行为事件 · 0012 微前端 · 0013 WebGL 生命周期 · 0014 多场景 · 0015 场景交互 · 0016 权限域服务 · 0017 大气与天气 · 0018 权限归属 · **0019 审计日志管道**

## 4 技术栈 / 结构
React19+Vite8+TS**6.0.3**(7.x 不被 typescript-eslint 支持)+Router7；qiankun2；pnpm+Turborepo；Less；ESLint10/Prettier3/Stylelint17/Husky/lint-staged/Vitest5。后端 Java21+Boot4.1.1+MP3.5.17；契约 OpenAPI 3.1。
端口：`common-tools` playground:3100 · `portal`:3000 · `log-console`:3200 · `event-console`:3300 · `environment`:3400 · `admin`:3500 · `audit-console`:3600。
规范只在 `common-config` 一份 · **各应用 LESS 令牌各自一份、禁跨应用引用** · `catalog:` 是版本唯一来源 · **按需再建，不空建空壳** · 配置三层(0003)：密钥级 / 部署级 / **业务运行期(自研只有这层)**

## 5 后端
- 依赖 `sts-app → sts-server → sts-components → sts-common → sts-parent`（零代码，`<version>` 只在此）；`sts-app-server` 唯一可启动；模块内**按特性分包**；**业务失败返 HTTP 200**（判 `code===0`，`R.isSuccess()` 须 `@JsonIgnore`）
- Boot4 starter 三处同动（`web→webmvc`、`boot3-starter→boot4-starter`、springdoc `2.x→3.x`）；Jackson3 import 认 `tools.jackson.*`（注解仍在 `com.fasterxml`）；enforcer `[21,)` 且 message 禁写 `${java.version}`
- 数据源 `h2`(默认)/`mysql`/`dameng`，切 `--spring.profiles.active=mysql`；库名 `bluewhale`（本机别的项目 11 个库勿动）。**Flyway** 脚本在 `db/migration/{h2,mysql,dameng}/V<n>__x.sql`：不写 `IF NOT EXISTS`、**已执行脚本不可改**、**版本号全仓唯一**(log=V1/track=V2/auth=V3/audit=V4)
- **切库必踩** `flyway-mysql` 须显式引 · `mysql-connector-j` runtime 放 `sts-app-server` · 达梦 `DmJdbcDriver18` 由 `sts-parent` 收口 · 真实库**不自动种账号**（显式开 `seed-admin/seed-auditor.enabled=true`，幂等）
- **MySQL 保留字** 避 `release/rank/groups/system/window`；只改列名没用（MP 会补 `列名 AS 属性名`）→ 正解**属性名与列名同构**且不加 `@TableField`。**达梦** MP 内置 `DbType.DM`，无官方 Flyway 模块 → `flyway.enabled=false` 走 `scripts/init-dameng.sh`；`VARCHAR(n CHAR)`；保留字两库不可互推；**未真实验证过**
- **权限域(0016)** 表 `bw_auth_*` 12 张；错误码 `40001~40913`；自研 HS512+PBKDF2、**不引 Spring Security**；入口 `@RequiresPermission`+`AuthInterceptor`+`PermissionChecker`；刷新令牌只存摘要+轮换+重放检测。坑：`AuthTokenFilter` 不可同时 `@Component` 又进 `FilterRegistrationBean`；`RefreshTokenEntity` 不继承 `BaseEntity`

## 6 契约与请求（0006/0007）
代码 → springdoc → `scripts/export-openapi.sh`(:18080) → `docs/api/openapi.json`（28 接口）→ `openapi-typescript` → `common-api/src/generated/schema.d.ts`（入库）。`http` = 传输内核（不认业务接口）、`common-api` = 契约装配；收口解 `R<T>`、抛非 0 码、超时+取消合并一个 AbortController；六类错误 + `retryable`；**内核不自动重试**、**超时必须先于取消分类**

## 7 三条上报管道（0004/0011/0019）
技术日志(可采样/前端驱动) · 行为事件(不可采样/契约极稳) · **审计(全量/后端拦截器自动产生，唯一不由前端产生)**。内容不同 ⇒ 独立模块独立表；通道相同 ⇒ 复用。三者均已落地。
- **logger** 7 级、**过滤前置于格式化**；**上报必须走 `onRecord`**（`LogSink` 吃渲染后有损的样子），`onRecord` 在过滤后、格式化前派发且异常静默吞
- **事件** 名正则 `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`（前后端同一条）· **登录先 updateContext 再 track、登出先 track 再 clearUser()** · `serverTime` 是排序过滤唯一依据 · `event_id` 不建唯一索引 · 卸载用 `fetch(keepalive)` 不用 sendBeacon
- **审计** `@Audited(action, resource)` 沉在 `sts-components-audit`（破 auth↔audit 循环依赖，artifactId **刻意不带 -starter**）；表 `bw_audit_log`(V4)；**无写入接口**（不可篡改靠"接口不存在"，POST → 40500）；只 3 个 GET 须 `AUDIT_READ`；**匿名不记、查询不记**；顺序 `WebInterceptorOrder.AUDIT(-100) < AUTHORIZATION(-50)`（Spring 只对 preHandle 成功过的拦截器回调 afterCompletion，排后就**静默丢**"越权尝试"）；`afterCompletion` 异常参数**恒为 null** 且业务失败返 200 → 用 `ResponseOutcome.markFailure/failureOf` 取业务码（**新增 handler 必须调**，漏了不报错）；`AuditRecorder` 用 `REQUIRES_NEW`（业务回滚审计不能跟着回滚），异常须留**调用方**（self-invocation 会让 `@Transactional` 静默失效）；**三套标识别混**：资源 `auth:user` / 权限码 `auth:user:create` / 动作 `auth.user.create`，填错**静默查空**
- **一处已记录的同层依赖** `sts-server-audit → sts-server-auth`（只读 `AuthContext` 与权限码，不调服务），与架构文档「同层 ✗」不符；触发条件：**第二个非权限域的服务需要 `@RequiresPermission` 时**下沉词汇到 `sts-components`

## 8 微前端（0012）
基座 `loadMicroApp`（非 registerMicroApps+start）；`sandbox:false`；`vite-plugin-qiankun`。**应用名是隐式契约**（不一致只卡"加载中"）。子应用 `base`：dev 必须 `/`（否则热更新**静默失效**）、build 才带前缀；须 `strictPort`+`cors`。通信走 `common-tools/micro`，**订阅必须 `onHostGlobalStateChange()`**，须在 `mount` 建立、`unmount` 断开。**被基座加载时组件级热更新不生效**

## 9 权限归属（0018）
会话**实现在 `packages/`、基座只装配** · 判定**基座单点算 → `HostGlobalState` 下发 `roles`/`permissions`**，子应用**不得自调 `/api/auth/me`** · **不开认证子应用**
- 权限码 = 后端 `AuthPermissions` 的 `域:资源:动作`，前端禁拼接自造；**权限不在令牌里** → `restore()` 走**刷新接口**、`/me` 留给 `reload()`；**401 刷新必须单飞**（轮换+重放检测），内核按客户端合并、**跨客户端须 `channel.ts` 再合并**；access 放内存、refresh 持久化（`bw.session`，**不含 accessToken**）
- 基座：**契约路径自带 `/api`** → `baseUrl` 留空 · **下发"认证能力"不下发令牌值**（`HostAuthBridge={getAccessToken, reauthenticate}` 经 `HostProps.auth`）· **跳转只留一处**（守卫 `<Navigate>`）、守卫须**等恢复结束**再判未登录
- `platform-admin`(:3500)/`platform-audit-console`(:3600)：**`AuthPort` 端口归一**（基座加载用桥 / 独立运行自建 channel）· **不含登录页**，独立运行自动登录**仅 `import.meta.env.DEV`** · **按钮级用显隐不用禁用** · 审计台**演练区按钮刻意不按权限显隐**。两者 `auth/` 逐字重复，触发条件：第三个管理型子应用时下沉
- 种子：admin 9 项；`auditor` 3 项（含 **audit:read**），写操作应得 **40301**（仅 h2 开）

## 10 三维环境（0013~0015/0017，:3400）
**画布与上下文同生共死**（`dispose()` 必含 `forceContextLoss()`，否则 StrictMode 双跑抛 `reading 'precision' of null`）· **切场景只换场景图、先释放旧后建新**，`disposeSubtree()` 递归（含 `light.shadow.dispose()`）· **相机跟随必须整体平移**，移动算法只在纯内核 `player/motor.ts`，角度 `0`=朝 +Z；小地图自绘 canvas · 大气四层纯函数，**高度角唯一自变量** · **⚠️ 验证脚本按「帧」等不按毫秒**（无头 swiftshader 单帧 2~4 秒），判据 `diagnostics().frames` · 未做：寻路 / 移动动画 / 雨雪霜 / 风向量 / 星空月亮
