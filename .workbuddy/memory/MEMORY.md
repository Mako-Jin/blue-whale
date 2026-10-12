# BlueWhale — 长期备忘

> 只留三类：**约定** · **坑** · **ADR 索引**。论证在 `docs/decisions/`，流水在 `.workbuddy/memory/*.md`，细节在各 README —— 本文件只留"跨会话会失效"的部分。

## 1 铁律
`main` / `github.com/Mako-Jin/blue-whale`。`web/`(pnpm) · `server/`(Maven `com.bluewhale.sts`) · `docs/ scripts/ infra/`。
六条：① 两端不共用构建 ② 跨端只认 OpenAPI（前端 SDK 禁手写）③ 跨端共享物只放 `docs/` ④ `packages/*` 不反向依赖 `platform/*` ⑤ `platform-{user,admin}` 互不引用、复用下沉 `packages/` ⑥ 后端禁反向依赖与跨层跳跃。
**改代码必须同提交改文档** · Husky `core.hooksPath web/.husky`（沙箱自动装 `spawn git EBUSY` → 手动挂钩）· LF · Conventional Commits · 凭据在 `~/.git-credentials`、**绝不在终端解析**；无人值守 git 加 `-c credential.helper= -c credential.helper=store`。

## 2 坑表（勿再排查）
**环境** 一律 Bash · Node `spawn git`→EBUSY、`turbo run`→231（逐包直跑正常）· 沙箱注入端口 → `--server.port=8080` · `curl --noproxy '*'` · 长驻服务**每个单独一个**后台任务 · git 重置 → `-c http.version=HTTP/1.1` · **e2e 不与前端构建并发**（CPU 抢占 → 限时等待超时 = 假"掉登录"）· MySQL 便携版 `D:\tools\installer\mysql`
**后端** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn …`（PATH 是 17）· **`-pl` 必带 `-am`**（否则拿 `D:\repository\maven` 旧 jar → `NoSuchMethod`）· `-Dsurefire.failIfNoSpecifiedTests=false` · `mvn clean` 占用 → `jps -l`+`taskkill` · **MP `eq(条件,列,值)` 的"值"无条件求值** → `x.getY().trim()` 在 `getY()==null` 也 NPE · **三目运算符的类型统一会拆箱**：`selfOnly ? currentUserId : requested`（`long` vs `Long`）在 `requested==null` 时 NPE → 写 `if` · **同一元素挂两个注解 = 解析期即中止**（如误删方法签名留下两行 `@GetMapping`）→ Lombok 没跑完，同批文件报**一大片**"找不到符号"，看着像 Lombok 配置坏了、根因在别处；`mvn install` 的增量编译同样会丢 Lombok 生成方法 → 报 `getXxx()` 找不到就 **clean 重编**
**前端** stylelint `extends` 须 `require.resolve` 绝对路径 · TS6 `baseUrl` 弃用 · ESLint10 flat config 默认导出数组、只从 cwd 向上找 · typescript-eslint 须显式 `tsconfigRootDir` · `openapi-typescript` 输出 4 空格双引号 → `common-api` 的 `gen:api` 挂 `prettier --write`，**别去掉**
**e2e** CDP 求值容忍"求值那刻在导航"→重试 · 浏览器侧报错必须让脚本非零退出 · 基座有登录守卫 → 脚本**必须先真实登录** · **⚠️ 前端 dev 未设 `strictPort`** → 残留进程占端口时**静默跳号**（3000→3002），e2e 打固定 3000 → 打到旧实例 = 假回归。**起 dev 前 `netstat` 清残留，起完逐端口 `curl` 验位**

## 3 ADR 索引
0001 架构基线 · 0002 公共包治理 · 0003 配置分域 · 0004 日志上报 · 0005 后端模块划分 · 0006 契约生成消费 · 0007 请求分层 · 0008 Boot4/JDK21 · 0009 多数据源与 Flyway · 0010 达梦 DM8 · 0011 行为事件 · 0012 微前端 · 0013 WebGL 生命周期 · 0014 多场景 · 0015 场景交互 · 0016 权限域服务 · 0017 大气与天气 · 0018 权限归属 · 0019 审计日志管道 · 0020 口令与登录安全加固 · 0021 资源目录接口与权限码登记约束 · 0022 角色管理与角色资源授权 · 0023 角色成员关系 · 0024 用户组管理与成员关系归属 · 0025 数据范围求值与审计可见性

## 4 栈 / 结构
React19+Vite8+TS**6.0.3**(7.x 不被 typescript-eslint 支持)+Router7；qiankun2；pnpm+Turborepo；Less；ESLint10/Prettier3/Stylelint17/Vitest5。后端 Java21+Boot4.1.1+MP3.5.17；OpenAPI 3.1。
端口 playground:3100 · portal:3000 · log-console:3200 · event-console:3300 · environment:3400 · admin:3500 · audit-console:3600。
规范只在 `common-config` 一份 · **各应用 LESS 令牌各自一份、禁跨应用引用** · `catalog:` 是版本唯一来源 · **按需再建，不空建空壳**

## 5 后端要点（细节见 `server/README.md` 与各 ADR）
- 依赖 `sts-app → sts-server → sts-components → sts-common → sts-parent`；`sts-app-server` 唯一可启动；**按特性分包**；**业务失败返 HTTP 200**（判 `code===0`）
- Boot4 starter 三处同动（`web→webmvc`、`boot3-starter→boot4-starter`、springdoc `2.x→3.x`）；Jackson3 import `tools.jackson.*`（注解仍在 `com.fasterxml`）；enforcer `[21,)`
- 数据源 `h2`/`mysql`/`dameng`；**Flyway** `db/migration/{h2,mysql,dameng}/V<n>__x.sql`：不写 `IF NOT EXISTS`、**已执行不可改**、**版本号全仓唯一**（log=V1/track=V2/auth=V3·V5/audit=V4）。切 mysql 须显式引 `flyway-mysql` + `mysql-connector-j`；达梦无 Flyway 模块 → `flyway.enabled=false` 走 `scripts/init-dameng.sh`；**未用真实达梦验证过**
- **MySQL 保留字** 正解**属性名与列名同构**且**刻意不加 `@TableField`**（`appRelease`→`app_release`）；保留字两库不可互推
- **权限域** 表 `bw_auth_*` 13 张；错误码 `40001~40918`；自研 HS512+PBKDF2、**不引 Spring Security**；入口 `@RequiresPermission`+`AuthInterceptor`+`PermissionChecker`；刷新令牌只存摘要+轮换+重放检测
- **管理接口** users/tenants/resources/roles(含授权)/`roles/{id}/members`(含成员)/`user-groups`(含组成员)/`/users/{id}/roles` 反查/`/users/{id}/data-scope` 数据范围反查 **均已落地 —— 配置侧闭环**；**前端管理面也已接通**（`common-api/access.ts` 20 条路径；`platform-admin` 五个标签页：用户+反查 / 租户 / 资源(树·列表) / 角色(授权+成员) / 用户组(成员)）；两项**后端有、前端刻意未接**：数据范围反查（要展示"配了但未生效"，是独立产品决定）、**组织**（表+实体+Mapper 都在，缺 Controller/Service）
- **软删+唯一索引坑（三处，同一个）**：唯一键**不含 `deleted`** → MP 查询看不见软删行 → 重建同码**撞键** → 正解**复活原记录**（角色/组复活须**一并清空成员与授权**）
- **`40900` 由 `DuplicateKeyException` 处理器填上** —— 是**并发下"先查后写"挡不住那一次**的第二道防线，**不是**"复活"的替代；`spring-tx` 必须 **compile**（处理器启动反射扫描，provided → `NoClassDefFoundError`）
- **`updateById` 的 `NOT_NULL` 策略清不掉字段**（传 null 进不了 `SET`，0 行且不报错）→ 正解 `update(new Entity(), lambdaUpdate().eq(...).set(...))` 逐列显式 set；传**空实体**才能让 `updateFill` 跑。`TenantService`/`UserService` 仍未修
- **判定侧静默缺陷累计五处**（DENY 跨角色 / 停用角色 / 时态过滤 / 停用用户组 / 组链路时态）→ **规则：凡新增会影响权限的开关，都要问"判定里读它了吗"**
- **嵌套用户组只跳一层** → 由 `UserGroupService.bindMember` 显式拒绝 `GROUP` 成员兜住（补它要 `WITH RECURSIVE`，三方言不一致）
- **数据范围求值（0025）只做了"求值 + 下发"**：`GET /users/{id}/data-scope` 合并全部生效角色的 `data_scope`（**取最宽**，rank 序 ALL > DEPT_AND_CHILD > DEPT > CUSTOM > SELF，**CUSTOM 位置刻意任意**）并用 `precise` 把"已配置"与"已生效"分开（ALL·SELF 自足；DEPT 系列需组织归属、CUSTOM 需解析 `filter_json`）；**算不出的档位往窄兜底**（只有 ALL 放行全租户，否则收窄到本人；反过来是静默越权）；无档位时 `scope` 为 null 而非 ALL。`selectRoleCodes` 已**升级**为 `selectEffectiveRoles`（返回角色+范围，共用一套生效条件）。**本仓无业务数据表**（16 张 = 14 `bw_auth_*` + 审计/日志/事件）⇒「用 `data_scope` 过滤业务数据」**没有落点**
- **审计可见性（0025）**：三条路径（分页/详情/聚合）统一走 `AuditLogService.visibility()` —— 租户**无条件**强制为当前登录者、`operatorId` **只在 ALL 档生效**（否则整体忽略，不是报错）、跨租户/超范围详情返 **404 非 403**（403 等于承认"记录存在"）；`countByAction` 加**必传** `tenantId`。修之前**持 `audit:read` 者可读别的租户的记录**（两条查询连租户条件都没有）
- **菜单权限还差两步且必须一起做**：①资源表补 `MENU`/`PAGE` 行（种子把 `ALL` 全建成 `API`）②基座 `BasicLayout` 按权限过滤导航（现在直接遍历 `MICRO_APPS`）。只做②=一屏空白导航，只做①=界面无变化。会把 admin 权限 21→26，级联 e2e + 四份 README + 两份记忆。**先定三件事**：菜单码粒度与命名 / 审计员可见哪些应用（它 3 项权限里没有菜单码，过滤后导航会空）/ `概览` 是否纳管
- **append-only 事实表不继承 `BaseEntity`**：`RefreshTokenEntity`、`PasswordHistoryEntity`

## 6 契约与请求（0006/0007）
代码 → springdoc → `scripts/export-openapi.sh`(:18080，**须 `JAVA_HOME=<jdk21>`) → `docs/api/openapi.json`（**55 接口/35 路径/85 模型**）→ `openapi-typescript` → `common-api/src/generated/schema.d.ts`（入库，提交前 Prettier）。`http`=传输内核、`common-api`=契约装配；收口解 `R<T>`、抛非 0 码、超时+取消合并一个 AbortController；**内核不自动重试**、**超时先于取消分类**。强度类校验**只能一个来源**（DTO `@Size(min=)` 与后端策略并存 ⇒ 冒两个错误码）

## 7 三条上报管道（0004/0011/0019）
技术日志(可采样) · 行为事件(不可采样) · **审计(全量/后端拦截器产生，唯一不由前端产生)**。均已落地。
- **logger** 7 级、**过滤前置于格式化**、上报必须走 `onRecord`
- **事件** 名正则 `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`；**登录先 updateContext 再 track、登出先 track 再 clearUser()**；`serverTime` 是排序过滤唯一依据；卸载用 `fetch(keepalive)`
- **审计** `@Audited`；表 `bw_audit_log`(V4)；**无写入接口**；**匿名不记、查询不记**；顺序 `AUDIT(-100) < AUTHORIZATION(-50)`；业务失败返 200 → 用 `ResponseOutcome.markFailure/failureOf` 取码（**新增 handler 必须调**）；`AuditRecorder` 用 `REQUIRES_NEW` + 独立 Bean；**三套标识别混**：资源 `auth:user` / 权限码 `auth:user:create` / 动作 `auth.user.create`

## 8 口令与登录安全（0020）
`PasswordPolicy` 返回**未满足项列表**；`LoginLockPolicy` 指数退避，**在 `double` 里比完再转 `long`**（先转可能溢出成负 = "锁到过去"）。两个计数：`failed_attempts`=本轮失败数（触发锁定后清零）· `lock_level`=历史锁定次数（只在登录成功清零）。**⚠️ 同事务写失败计数 = 永远不生效** → `LoginAttemptRecorder`（独立 Bean + `REQUIRES_NEW`）。**守卫测试不能加 `@Transactional`**；带运营含义的状态必须同时进 VO。

## 9 微前端（0012）
基座 `loadMicroApp`；`sandbox:false`；`vite-plugin-qiankun`。**应用名是隐式契约**。子应用 `base`：dev 必须 `/`、build 才带前缀；须 `strictPort`+`cors`。**订阅必须 `onHostGlobalStateChange()`**，`mount` 建立 / `unmount` 断开。**被基座加载时组件级热更新不生效**

## 10 权限归属（0018）
会话**实现在 `packages/`、基座只装配** · 判定**基座单点算 → `HostGlobalState` 下发 `roles`/`permissions`**，子应用**不得自调 `/me`** · **不开认证子应用**。权限码 = 后端 `AuthPermissions` 的 `域:资源:动作`，前端禁拼接自造；**权限不在令牌里** → `restore()` 走刷新接口；**401 刷新必须单飞**（轮换+重放检测），**跨客户端须 `channel.ts` 再合并**。基座：**契约路径自带 `/api`** → `baseUrl` 留空；**下发"认证能力"不下发令牌值**；**跳转只留一处**（守卫 `<Navigate>`）。`platform-admin`/`platform-audit-console`：`AuthPort` 端口归一 · **不含登录页**（独立运行自动登录**仅 `import.meta.env.DEV`**）· **按钮级用显隐不用禁用**。种子 admin **21 项** / `auditor` 3 项（写操作 **40301**，仅 h2 开）

## 11 三维环境（0013~0015/0017，:3400）
**画布与上下文同生共死**（`dispose()` 必含 `forceContextLoss()`）· **切场景先释放旧后建新**（`disposeSubtree()` 递归含 `light.shadow.dispose()`）· **相机跟随必须整体平移**（算法只在 `player/motor.ts`，角度 `0`=朝 +Z）· 大气四层纯函数、**高度角唯一自变量** · **⚠️ 验证脚本按「帧」等不按毫秒**（无头 swiftshader 单帧 2~4 秒），判据 `diagnostics().frames`
