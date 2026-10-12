# BlueWhale — 长期备忘

> 只留三类：**必须遵守的约定** · **花过代价的坑** · **ADR 索引**。论证在 `docs/decisions/`，流水在 `.workbuddy/memory/YYYY-MM-DD.md`。舰长偏好可视化演示，先结论后细节。

## 1 铁律
- 分支 `main`；远端 `github.com/Mako-Jin/blue-whale`。`web/`(pnpm) · `server/`(Maven, `com.bluewhale.sts`) · `docs/ scripts/ infra/`。范本 `platform-terminator`(前端) / `SystemTerminator`(后端)
- 六条：① 两端不共用构建 ② 跨端只认 OpenAPI 契约（前端 SDK 禁手写）③ 跨端共享物只放 `docs/` ④ `packages/*` 不反向依赖 `platform/*` ⑤ `platform-{user,admin}` 互不引用，复用下沉 `packages/` ⑥ 后端禁反向依赖与跨层跳跃
- **每步完善文档**：改代码同步改文档、同一次提交 · Husky 靠 `core.hooksPath web/.husky`（本地 config 不入库，重克隆要重设）· `.gitattributes` 强制 LF · Conventional Commits
- 凭据在 `~/.git-credentials`，**绝不用命令行解析它**；无人值守 git 加 `-c credential.helper= -c credential.helper=store`

## 2 坑表（勿再排查）
**环境** 一律 Bash（PowerShell 不可用，`sc`/`reg` 还被沙箱拉黑）· Node `spawn git`→EBUSY、`turbo run`→231（逐包直跑正常）· 后端端口 56491 → `--server.port=8080` · `curl` 加 `--noproxy '*'` · 长驻服务必须后台任务起（`&` 会被杀）· 废弃 Chrome 占 CDP → 换端口/taskkill · git 连接重置 → `-c http.version=HTTP/1.1` · 本机 MySQL 8.0.26 便携版在 `D:\tools\installer\mysql`（`bin/mysqld.exe --datadir=…/data`，无 `my.ini`），**root 口令由舰长现场给、不落任何文件**
**后端** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn …`（PATH 是 JDK17）· `mvn clean` 占用 → `jps -l` + `taskkill //F //PID` · Maven 仓库 `D:\repository\maven`
**前端** stylelint `extends` 须 `require.resolve` 绝对路径 · TS6 起 `baseUrl` 弃用 · ESLint10 flat config **默认导出数组**、只从 cwd 向上找配置（工作区根也要有）· typescript-eslint 须显式 `tsconfigRootDir`

## 3 ADR 索引
0001 架构基线 · 0002 公共包治理 · 0003 配置分域 · 0004 日志上报 · 0005 后端模块划分 · 0006 契约生成消费 · 0007 请求分层 · 0008 Boot4/JDK21 · 0009 多数据源与 Flyway · 0010 达梦 DM8 · 0011 行为事件 · 0012 微前端 · 0013 WebGL 生命周期 · 0014 多场景 · 0015 场景交互 · 0016 权限域服务 · 0017 大气与天气 · 0018 权限归属

## 4 技术栈 / 前端结构
React19+Vite8+TS**6.0.3**(7.x 不被 typescript-eslint 支持)+Router7；qiankun2；pnpm+Turborepo；Less；ESLint10/Prettier3/Stylelint17/Husky/lint-staged/Vitest5。后端 Java21+Boot4.1.1(Spring7/Tomcat11/Jackson3)+MP3.5.17；契约 OpenAPI 3.1。
包(端口)：`common-config` · `common-tools`:3100 · `common-api` · `portal`:3000 · `log-console`:3200 · `event-console`:3300 · `environment`:3400 · `admin`:3500 · `elemental/` 预留。
规范只在 `common-config` 一份 · **各应用 LESS 令牌各自一份、禁跨应用引用** · `catalog:` 是版本唯一来源 · `common-{theme,components}` / `platform-user` **按需再建，不空建**

## 5 后端
依赖 `sts-app → sts-server → sts-components → sts-common → sts-parent`（零代码，`<version>` 只在此）；`sts-app-server` 唯一可启动；模块内**按特性分包**；**业务失败返 HTTP 200**（判 `code===0`，`R.isSuccess()` 须 `@JsonIgnore`）；Boot4 starter 三处同动（`web→webmvc`、`boot3-starter→boot4-starter`、springdoc `2.x→3.x`）；Jackson3 import 认 `tools.jackson.*`（注解仍在 `com.fasterxml`）；enforcer `[21,)` 且 message 禁写 `${java.version}`。
**数据源** `h2`(默认)/`mysql`/`dameng`，切 `BW_DB_PASSWORD=… --spring.profiles.active=mysql`；库名 `bluewhale`（本机别的项目 11 个库勿动）。**Flyway** 脚本 `db/migration/{h2,mysql,dameng}/V<n>__x.sql`，不写 `IF NOT EXISTS`，**已执行脚本不可改**，**版本号全仓唯一**（log=V1/track=V2/auth=V3）。必踩：`flyway-mysql` 须显式引 · `mysql-connector-j` runtime 放 `sts-app-server` · 达梦 `DmJdbcDriver18` 由 `sts-parent` 收口。**MySQL 保留字** 避 `release/rank/groups/system/window`；只改列名没用（MP 补 `列名 AS 属性名`）→ 正解**属性名与列名同构**且不加 `@TableField`。**达梦** MP 内置 `DbType.DM`；无官方 Flyway 模块 → `flyway.enabled=false` 走 `scripts/init-dameng.sh`；`VARCHAR(n CHAR)`；保留字两库不可互推；**未用真实达梦验证过**。**mysql 已打通**（V1~V3 全落地、跨重启留存已验证）：**真实库不自动种账号**（种子只在 h2 profile 开）→ 显式开一次 `--bluewhale.auth.seed-admin.enabled=true --bluewhale.auth.seed-auditor.enabled=true`（幂等；详见 `application-mysql.yml`「前置准备（二）」）。
**权限域 `sts-server-auth`(ADR-0016)** 表 `bw_auth_*` 12 张(V3)；错误码 `40001~40913`；自研 HS512+PBKDF2，**不引 Spring Security**；入口 `@RequiresPermission`+`AuthInterceptor`+`PermissionChecker`，上下文只有 `AuthContext`；刷新令牌只存 SHA-256 摘要+轮换+重放检测；认证·租户·用户已交付，**组织·角色·用户组·资源管理接口未做**。坑：Jackson3 `encode(Map)` 接不了 `byte[]`；`AuthTokenFilter` 不可同时 `@Component` 又进 `FilterRegistrationBean`；`RefreshTokenEntity` 不继承 `BaseEntity`。

## 6 契约与请求（ADR-0006/0007）
链：代码 → springdoc → `scripts/export-openapi.sh`(:18080) → `docs/api/openapi.json` → `openapi-typescript` → `common-api/src/generated/schema.d.ts`（入库）。`common-tools/http` = 传输内核（不认业务接口），`common-api` = 契约装配。收口：解 `R<T>`、`code!==0` 抛、超时+取消合并一个 AbortController；六类错误 `business/http/network/timeout/abort/parse`+`retryable`；**内核不自动重试**；**超时必须先于取消分类**。

## 7 日志与事件（ADR-0004/0011）
三管道：技术日志(可采样/前端驱动) · 审计日志(全量/后端拦截器产生) · 行为事件(不可采样/契约极稳)；内容不同 ⇒ 独立模块独立表，通道相同 ⇒ 复用。**审计日志至今 0 实现**（无表/无模块/无拦截器/无接口，只有 `ClientLog*` 注释里写下的边界），ADR-0011 明确标着"已设计未实现"。logger 7 级，**过滤前置于格式化**；**上报必须走 `onRecord`**（`LogSink` 吃渲染后有损的样子），`onRecord` 在过滤后、格式化前派发且异常静默吞。
事件名正则 `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`（前后端同一条）· **登录先 updateContext 再 track；登出先 track 再 clearUser()** · 双时间戳（`serverTime` 是排序过滤唯一依据）· `event_id` 不建唯一索引 · `deviceId`+`userId` 同挂 · 卸载用 `fetch(keepalive)` **不用** sendBeacon。

## 8 配置分域（ADR-0003）
三层：密钥级(Vault/KMS) · 部署级(Nacos/Apollo) · 业务运行期(自研走契约)；**自研只有第三层**；禁用万能配置接口。

## 9 微前端（ADR-0012）
基座 `loadMicroApp`（非 registerMicroApps+start）；`sandbox:false`；子应用用 `vite-plugin-qiankun`。**应用名是隐式契约**（不一致只卡「加载中」）。子应用 `base`：dev 必须 `/`（否则热更新**静默失效**）、build 才带前缀；须 `strictPort`+`cors`。通信走 `common-tools/micro`，**订阅必须 `onHostGlobalStateChange()`**（每子应用只存一个回调，二次注册静默覆盖）。**被基座加载时组件级热更新不生效** → 改组件单独跑子应用。

## 10 权限归属（ADR-0018，三段已落地）
会话**实现在 `packages/`、基座只装配** · 判定**基座单点算 → `HostGlobalState` 下发 `roles`/`permissions`**，子应用**不得自调 `/api/auth/me`** · 管理独立成 `platform-admin`；**不开认证子应用**。
- 权限码 = 后端 `AuthPermissions` 的 `域:资源:动作`，前端禁拼接自造；**权限不在令牌里**，但 `LoginVO`/`MeVO` 自带 roles/permissions → `restore()` 走**刷新接口**，`/me` 留给 `reload()`
- **401 刷新必须单飞**（轮换+重放检测，并发会被判重放 → 撤销全部刷新令牌）；内核 `pendingReauth` 按客户端合并，**跨客户端须 `common-api/channel.ts` 在会话层再合并一次**（`finally` 清位）
- access 放内存、refresh 持久化；新增 `common-tools/session` 子路径须在 `exports` 注册
- 基座：权限下发是**模块级订阅** `portal/src/micro/session-sync.ts` · **跳转只留一处**（守卫 `<Navigate>`；`onAuthLost` 刻意不传）· 守卫须**等恢复阶段结束**再判未登录
- **契约路径自带 `/api`** → `baseUrl` 留空
- **下发"认证能力"不下发令牌值**：`HostAuthBridge={getAccessToken(), reauthenticate()}` 经 `HostProps.auth`（令牌值会陈旧；刷新须全应用一次）；**不提供"清空会话"**
- `common-api/access.ts`：users 6 + tenants 5 操作，`AccessPermissions` 八常量与后端逐字一致
- `platform-admin`(:3500)：**`AuthPort` 端口归一**（基座加载用桥 / 独立运行自建 channel）· **不含登录页**，独立运行"开发账号自动登录"**仅 `import.meta.env.DEV`** · 权限快照 `ready` 区分"没权限"与"还不知道"，`usePermission` 未就绪返 `false` · **按钮级用显隐不用禁用**，带 `data-permission`
- 只读种子 `auditor / Auditor@12345`（`bluewhale.auth.seed-auditor`，**默认 false**，h2 开）——写操作应得 **40301**；e2e `verify-portal-auth.mjs`(:9337) · `verify-admin-permissions.mjs`(:9338)

## 11 三维环境（ADR-0013~0015/0017，:3400；细节见该应用 README）
- **画布与上下文同生共死（最关键）**：canvas 由 `createStage` 创建并随 `dispose()` 移除（不接受外部传入）；`dispose()` 必含 `forceContextLoss()`，否则 StrictMode 双跑抛 `reading 'precision'` of null
- GPU 资源不随 GC 释放：`disposeSubtree()` 递归（含 `light.shadow.dispose()`，**切场景会泄漏阴影贴图**）；**切换 = 只换场景图，先释放旧后建新**；多场景只认 `loadSceneCatalog()`（`mergeCatalogOverrides()` 只覆盖不发明）
- **相机跟随必须整体平移**（`syncFocus` 里 `delta = focus - orbit.target`，有控制器时 `orbit.enablePan=false`）；移动算法只在纯内核 `player/motor.ts`；**角度约定统一** `0`=朝 +Z、方向 `(sin a, cos a)`、世界 -Z 是北；小地图自绘 canvas 不走 React 状态
- 大气：四层纯函数 `solar/palette/sample/rig`，**高度角唯一自变量**，**雾距给系数**；`SceneClimate` 必填
- **⚠️ 验证脚本必须按「帧」等，不按毫秒**（无头 swiftshader 单帧 2~4 秒），判据 `diagnostics().frames`；脚本 `verify-{micro-frontend:9333,scene-switching:9334,player-controls:9335,atmosphere:9336}.mjs`
- 未做：寻路 · 移动动画 · 雨雪霜 · 风向量 · 星空月亮
