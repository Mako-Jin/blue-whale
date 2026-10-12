# BlueWhale — 长期备忘

> 只留三类：**约定** · **坑** · **ADR 索引**。论证在 `docs/decisions/`，流水在 `.workbuddy/memory/*.md`，细节在各 README。

## 1 铁律
- 分支 `main`；远端 `github.com/Mako-Jin/blue-whale`。`web/`(pnpm) · `server/`(Maven `com.bluewhale.sts`) · `docs/ scripts/ infra/`。范本 `platform-terminator`(前端 monorepo/qiankun/React) / `SystemTerminator`(后端 Maven 多模块)
- 六条：① 两端不共用构建 ② 跨端只认 OpenAPI 契约（前端 SDK 禁手写）③ 跨端共享物只放 `docs/` ④ `packages/*` 不反向依赖 `platform/*` ⑤ `platform-{user,admin}` 互不引用、复用下沉 `packages/` ⑥ 后端禁反向依赖与跨层跳跃
- **改代码必须同步改文档，同一次提交** · Husky 靠 `core.hooksPath web/.husky`（沙箱自动安装会 `spawn git EBUSY` → 手动挂钩）· LF 强制 · Conventional Commits
- 凭据在 `~/.git-credentials`，**绝不在终端解析**（`cut/sed/grep` 会把明文令牌打进输出）；无人值守 git 加 `-c credential.helper= -c credential.helper=store`

## 2 坑表（勿再排查）
- **环境** 一律 Bash（PowerShell 沙箱不可用）· Node `spawn git`→EBUSY、`turbo run`→231（逐包直跑正常）· 沙箱注入端口 56491 → `--server.port=8080` · `curl` 加 `--noproxy '*'` · 长驻服务必须**每个单独一个**后台任务（`&` 被杀、`(cmd &)`+`wait` 也撑不住 → 端口 000 / 页面空载）· 废弃 Chrome 占 CDP → 换端口或 taskkill · git 连接重置 → `-c http.version=HTTP/1.1` · **`git push` 曾被沙箱代理阻断**（`github.com` → `CONNECT tunnel failed 502` / `Empty reply from server` / `schannel: server closed abruptly`，直连 `curl` 返 `000`，而 `api.github.com` 正常）→ **2026-10-12 下午已恢复可推**（积压 7 个提交一次推完），遇到时先重试一次、不行就只本地提交并记下领先数 · **`git fetch` 有时会挂住**（比 push 更容易被掐断）→ 给足超时或直接看 `git log origin/main`（push 自身会更新远端追踪引用，不必 fetch） · **e2e 脚本不与前端构建并发跑**（`vite build` 占满 CPU → 有时限的等待超时，表现成"掉登录"的假回归）· MySQL 8.0.26 便携版 `D:\tools\installer\mysql`，root 口令现场给不落文件 · 系统 python3.11 坏 → 用受管 `…/binaries/python/versions/3.13.12/python.exe`
- **后端** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn …`（PATH 是 JDK17）· **`-pl` 不带 `-am` 会从 `D:\repository\maven` 拿旧 jar**（症状 `NoSuchMethod` / 新错误码不生效）· 正确 flag **`-Dsurefire.failIfNoSpecifiedTests=false`** · `mvn clean` 文件占用 → `jps -l` + `taskkill //F //PID` · Maven 仓库 `D:\repository\maven` · **MP `eq(条件, 列, 值)` 的"值"无条件求值** → `x.getY().trim()` 在 `getY()==null` 时照样 NPE（`!= null` 判断救不了），**过滤值先取局部变量**
- **前端** stylelint `extends` 须 `require.resolve` 绝对路径 · TS6 起 `baseUrl` 弃用 · ESLint10 flat config **默认导出数组**、只从 cwd 向上找（工作区根也要有）· typescript-eslint 须显式 `tsconfigRootDir` · **`openapi-typescript` 输出 4 空格双引号，入库的是 Prettier 归一后样子** → `common-api` 的 `gen:api` 挂了 `prettier --write`，**别去掉**（去掉后改一个字段也整文件重排）
- **e2e** CDP 求值须容忍"求值那刻在导航"→ 重试 · **浏览器侧报错必须让脚本非零退出**（子应用没起时 qiankun 抛 `Failed to fetch`，每步仍能读出 `undefined` 往下走）· 基座有登录守卫，脚本**必须先真实登录** · **⚠️ 前端 dev 未设 `strictPort`** → 端口被上一会话的残留进程占用时**静默跳号**（3000→3001→3002），而 e2e 脚本打的是固定 3000 → 打到旧实例、表现为"改的东西不生效"这类诡异假回归。**起 dev 前先 `netstat -ano | grep :30xx` 清残留**（`taskkill //F //T //PID`），起完逐端口 `curl` 验位

## 3 ADR 索引
0001 架构基线 · 0002 公共包治理 · 0003 配置分域 · 0004 日志上报 · 0005 后端模块划分 · 0006 契约生成消费 · 0007 请求分层 · 0008 Boot4/JDK21 · 0009 多数据源与 Flyway · 0010 达梦 DM8 · 0011 行为事件 · 0012 微前端 · 0013 WebGL 生命周期 · 0014 多场景 · 0015 场景交互 · 0016 权限域服务 · 0017 大气与天气 · 0018 权限归属 · 0019 审计日志管道 · 0020 口令与登录安全加固 · 0021 资源目录接口与权限码登记约束 · 0022 角色管理与角色资源授权 · 0023 角色成员关系 · 0024 用户组管理与成员关系归属

## 4 技术栈 / 结构
React19+Vite8+TS**6.0.3**(7.x 不被 typescript-eslint 支持)+Router7；qiankun2；pnpm+Turborepo；Less；ESLint10/Prettier3/Stylelint17/Husky/lint-staged/Vitest5。后端 Java21+Boot4.1.1+MP3.5.17；OpenAPI 3.1。
端口：playground:3100 · portal:3000 · log-console:3200 · event-console:3300 · environment:3400 · admin:3500 · audit-console:3600。
规范只在 `common-config` 一份 · **各应用 LESS 令牌各自一份、禁跨应用引用**（引了只有 `vite build` 报） · `catalog:` 是版本唯一来源 · **按需再建，不空建空壳** · 配置三层(0003)：密钥级 / 部署级 / **业务运行期(自研只有这层)**

## 5 后端
- 依赖 `sts-app → sts-server → sts-components → sts-common → sts-parent`（零代码，`<version>` 只在此）；`sts-app-server` 唯一可启动；模块内**按特性分包**；**业务失败返 HTTP 200**（判 `code===0`，`R.isSuccess()` 须 `@JsonIgnore`）
- Boot4 starter 三处同动（`web→webmvc`、`boot3-starter→boot4-starter`、springdoc `2.x→3.x`，2.x 配 Boot4 启动即失败）；MockMvc 另引 `spring-boot-starter-webmvc-test`，注解换包到 `…boot.webmvc.test.autoconfigure`；Jackson3 import 认 `tools.jackson.*`（注解仍在 `com.fasterxml`）；enforcer `[21,)` 且 message 禁写 `${java.version}`
- 数据源 `h2`(默认)/`mysql`/`dameng`，切 `--spring.profiles.active=mysql`；库名 `bluewhale`（本机别的项目 11 个库勿动）。**Flyway** 脚本 `db/migration/{h2,mysql,dameng}/V<n>__x.sql`：不写 `IF NOT EXISTS`、**已执行脚本不可改**、**版本号全仓唯一**（log=V1 / track=V2 / auth=V3·V5 / audit=V4）
- **切库必踩** `flyway-mysql` 须显式引（Flyway 10 起 DB 支持模块化，core 只内置 H2/SQLite）· `mysql-connector-j` runtime 放 `sts-app-server` · 达梦 `DmJdbcDriver18` 由 `sts-parent` 收口 · 真实库**不自动种账号**（显式开 `seed-admin/seed-auditor.enabled=true`，幂等）
- **MySQL 保留字** 避 `release/rank/groups/system/window`；只改列名没用（MP 见"列名≠属性名"补 `列名 AS 属性名`，别名照样 1064）→ 正解**属性名与列名同构**（`appRelease`→`app_release`）且**刻意不加 `@TableField`**。**达梦** MP 内置 `DbType.DM`；无官方 Flyway 模块 → `flyway.enabled=false` 走 `scripts/init-dameng.sh`；方言 `IDENTITY(1,1)`、**`VARCHAR(n CHAR)`**；保留字**两库不可互推**；**未用真实达梦实例验证过**
- **权限域(0016/0020/0021)** 表 `bw_auth_*` 13 张；错误码 `40001~40918`；自研 HS512+PBKDF2、**不引 Spring Security**；入口 `@RequiresPermission`+`AuthInterceptor`+`PermissionChecker`；刷新令牌只存摘要+轮换+重放检测。坑：`AuthTokenFilter` 不可同时 `@Component` 又进 `FilterRegistrationBean`；`#{it.xxx}` 这类 OGNL 引用**编译器管不到**，改属性名后要全局搜
- **管理接口** users / tenants / resources / roles（含资源授权）/ **roles/{id}/members**（含成员关系）/ **user-groups**（含组成员）/ **`/users/{id}/roles` 反查** 均已落地 —— **配置侧彻底闭环**；**组织**待补（表+实体+Mapper 都在，只缺 Controller/Service）
- **`40900` 段已由 `DuplicateKeyException` 处理器填上**（此前唯一键冲突落兜底 → 对外 `50000`，责任方归错）。它是**并发下"先查后写"挡不住的那一次**的第二道防线，**不是**"软删复活原记录"的替代；`spring-tx` 在该模块必须 **compile**（处理器启动时被反射扫描，provided 不传递 → 服务模块启动 `NoClassDefFoundError`，与业务代码无关、极难定位）；`DuplicateKeyAdviceTest` 直接调 Mapper 钉住"抛的确实是 `DuplicateKeyException`"（依赖**方言错误码映射** H2 23505 / MySQL 1062）—— 因所有 HTTP 路径都已挡住撞键、这条分支**不可达**，不单独测就是没人跑过的代码
- **资源目录(0021)** `bw_auth_resource` 存**权限码**（三段 `auth:user:create`），非资源标识（两段 `auth:user`）。坑：①权限码**建后不可改**（旧授权静默失效）②`uk_bw_auth_resource_code` 建在 **`resource_code` 单列**不含 `deleted` → MP 查询加 `deleted=0` **看不见软删行** → 重建同码**撞键 → 兜底 50000** → 正解**复活**原记录 ③删除拒"有子资源 40913 / 被角色引用 40915" ④树对**孤儿与成环**降级平铺 ⑤**资源平台级**（`tenant_id` 留空）；`AuthorizationMapper` 只按 `ro.tenant_id` 过滤，**注释与 SQL 不一致**（记录未改）
- **角色与授权(0022)** 表 `bw_auth_role` + `bw_auth_rel_role_resource`；授权挂在角色下（`/roles/{id}/resources`）。坑：①`uk_bw_auth_role_tenant_code` 建在 **(tenant_id, role_code)**、不含 `deleted` → 与资源目录同一个坑，正解**复活**（复活出来一定是**空角色**，否则旧授权静默回来）②**授权是 upsert 并清同键其余行**（表上无 (role_id, resource_id) 唯一索引）③删除三道拒绝：SYSTEM 角色 40917 / 有子角色 40913 / 有成员 40916；**自身授权一并撤销**（与"删资源被引用时拒绝"不是一回事）④SYSTEM 角色不可停用、**不可改分类**（改分类 = 先降级再删的绕过）⑤**判定侧三修**：DENY 跨角色优先（原 SQL 只滤"行"→ A 允许 + B 拒绝仍放行）、停用角色不再产生权限（漏了 `ro.status`）、时态 `effective_from/to` 进判定且**时间由调用方传而非 `NOW()`**
- **⚠️ `updateById` 的 `NOT_NULL` 策略清不掉字段**（传 null 进不了 `SET`，影响 0 行且不报错）→ `parentId` 摘不回顶级、描述清不掉、**授权生效区间/成员时间窗清不掉（重授权仍无权限）**。正解 `update(new Entity(), lambdaUpdate().eq(...).set(...))` 逐列显式 set；传**空实体**而非 null 才能让 `updateFill` 跑
- **角色成员关系(0023)** 表 `bw_auth_rel_role_member`（多态 `member_type`=USER/GROUP）；`/roles/{id}/members` 三条。坑：①**撤销路径必须带 memberType**（`/members/{memberType}/{memberId}`）—— `member_id` 只在所指那张表有意义，两个自增序列**会撞号**，只用 id 会撤错对象而表现为"权限凭空消失" ②`memberType` **必填不给默认值**（默认 USER 会把漏填变成"悄悄授给同号用户"）③多态表**无外键** ⇒ 应用层保证存在性（40002）④**成员必须与角色同租户** —— 判定两侧都限 `tenant_id`，跨租户关系**永远匹配不上 = 静默不可达**，文案须点名租户 ⑤表上**无 (role_id, member_type, member_id) 唯一索引** → 同 upsert + 清同键其余行 ⑥成员列表**分页**但**不做按名搜索**（名字在另一张表，先解析再分页会让 total 失真）⑦`effectiveNow` **只表达时态**，不压入角色/成员状态（三个开关必须分别可见）⑧复用 `auth:role:update`（含**直接提权**路径：把自己加到 SUPER_ADMIN，与 `user:update` 含重置任意口令同性质），但**审计动作必须分开** `auth.role.grant_member`/`revoke_member` ⑨复活角色**一并清空成员关系**（与清授权同理，让"复活=没人持有也没权限"无条件成立）
- **用户组与反查(0024)** 表 `bw_auth_user_group` + `bw_auth_rel_user_group_member`；`/user-groups` 8 条 + `GET /users/{id}/roles` 1 条。坑：①**路径 `user-groups`（连字符）与权限码 `auth:user_group:*`（下划线）是全仓唯一一处两处不同形**（标识字符集被 `AuthTaxonomyTest` 钉成 `[a-z][a-z0-9_]*`，放宽会让"用标识当键"处处处转义）②`uk_bw_auth_user_group_tenant_code` 建在 **(tenant_id, group_code)** 不含 `deleted` —— **第三处同一个坑**，正解**复活**，且复活出来一定是**空组**（清成员 `clearMembersOf` + 清该组持有的角色 `clearRoleGrantsOf`，后者的 `member_type='GROUP'` 条件**不能省**：用户与组两个自增序列会撞号）③**用户组只支持静态组**：`DYNAMIC` 报 `40001`，`filterJson`/`orgScopeJson` **连入参都不接受**（接受没人读的字段最坏后果是调用方**以为它生效了**）④**`bindMember` 显式拒绝 `GROUP` 成员**（判定只跳一层，接受会产生"列表可见、判定不参与"的假开关）⑤删除两道拒绝：有成员 `40918` / 有子组 `40913`，**组自身角色授权一并撤销** ⑥**反查与判定 SQL 分开实现不合并**：判定越来越**严**（聚合/过滤/去重）、反查越来越**宽**（不过滤/逐条/带来源）；**反查刻意不过滤停用与过期**（那正是"为什么没权限"的答案），唯一过滤"用户根本不在组里"（`gm.id IS NOT NULL`）⑦反查带**两组时态**（经组授予是两条边），`source=DIRECT` 时第二组为 `null`（那条边**不存在**，非"长期有效"）；`LEFT JOIN` 的 `ON` 里带 `rm.member_type='GROUP'`（写 `WHERE` 会整行滤掉超时的直授行 = 内连接语义）⑧**反查权限归 `auth:user:read`**（读的是"某个用户"，与列表/详情同类）→ **审计员可用反查、但对用户组一律 `40301`**（分开做的决定）⑨权限码 17 → **21**、错误码 +2（`40409`/`40918`）、审计动作 +5（`auth.user_group.{create,update,delete,grant_member,revoke_member}`，其中 **`grant_member` 是本域第二条间接提权路径**）⑩组成员关系与角色成员关系形状对齐但**不共用 VO**（取值域与名字解析来源不同；触发条件 = **第三个**成员关系出现，组织-用户是下一个候选）
- **判定侧第五处静默缺陷已修**：`bw_auth_user_group.is_enabled` 列从建库起就在、实体也带着，但 `AuthorizationMapper` 两条子查询**从不连 `bw_auth_user_group`** → "停用组"是个**假按钮**。已接进判定（连表检查 `ug.deleted=0` 与 `ug.is_enabled`）。与 0022「停用角色」同类 → **提炼规则：凡新增会影响权限的开关（状态位 / 时态列 / 归属字段），都要同时问一句"判定里读它了吗"**。累计五处：DENY 跨角色 / 停用角色 / 时态过滤（0022 三处）· 停用组（0024）· 组链路时态过滤（0023 首次验证）
- **嵌套用户组在判定里只跳一层**（`AuthorizationMapper` 把 `gm.member_type` 钉成 `'USER'`），而 `MemberType`/实体注释**声称支持嵌套**（误导性注释，已在 `AuthorizationMapper` 写明）。补它要 `WITH RECURSIVE`（三套方言不一致、达梦未验证）→ **已由 `UserGroupService.bindMember` 显式拒绝 `GROUP` 成员兜住**（`decisions/0024` 第 3 条）
- **组链路与成员时态已在 0023 首次端到端验证**（用户 → 用户组 → 角色 → 资源；组-用户关系过期即失效）
- **菜单权限还差两步**：资源表**没有 `MENU`/`PAGE` 行**（种子把 `ALL` 全建成 `API`）+ 基座导航没按权限过滤（`BasicLayout` 直接遍历 `MICRO_APPS`）
- **append-only 事实表不继承 `BaseEntity`**：`RefreshTokenEntity`、`PasswordHistoryEntity` 是唯二例子

## 6 契约与请求（0006/0007）
代码 → springdoc → `scripts/export-openapi.sh`(:18080，**须 `JAVA_HOME=<jdk21>`** 否则脚本直接拒绝) → `docs/api/openapi.json`（**54 接口 / 34 路径 / 83 模型**）→ `openapi-typescript` → `common-api/src/generated/schema.d.ts`（入库，提交前 Prettier）。`http` = 传输内核（不认业务接口）、`common-api` = 契约装配；收口解 `R<T>`、抛非 0 码、超时+取消合并一个 AbortController；六类错误 + `retryable`；**内核不自动重试**（幂等边界属业务语义）、**超时必须先于取消分类**（都是 abort 实现）
- **强度类校验只能有一个来源**：DTO `@Size(min=)` 与后端策略并存 ⇒ 改配置冒两个错误码。`UserSaveRequest`/`PasswordResetRequest` 已去掉 `min`
- 坑：`Object` 字段渲染成 `Record<string, never>` → `JsonValue` 只修正 `data`/`context`/`logs`

## 7 三条上报管道（0004/0011/0019）
技术日志(可采样/前端驱动) · 行为事件(不可采样/契约极稳) · **审计(全量/后端拦截器自动产生，唯一不由前端产生)**。内容不同 ⇒ 独立模块独立表；通道相同 ⇒ 复用。三者均已落地。
- **logger** 7 级、**过滤前置于格式化**；**上报必须走 `onRecord`**（`LogSink` 吃渲染后有损的样子，`info`/`success` 都压成 `'log'`），`onRecord` 在过滤后、格式化前派发且异常静默吞
- **事件** 名正则 `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`（前后端同一条）· **登录先 updateContext 再 track、登出先 track 再 clearUser()**（反了不报错，只丢"谁登出了"）· `serverTime` 是排序过滤唯一依据 · `event_id` 不建唯一索引 · 卸载用 `fetch(keepalive)` 不用 sendBeacon
- **审计** `@Audited(action, resource)` 沉在 `sts-components-audit`（破 auth↔audit 循环依赖，artifactId **刻意不带 -starter**）；表 `bw_audit_log`(V4)；**无写入接口**（不可篡改靠"接口不存在"，POST → 40500）；只 3 个 GET 须 `AUDIT_READ`；**匿名不记、查询不记**；顺序 `AUDIT(-100) < AUTHORIZATION(-50)`（Spring 只对 preHandle 成功过的拦截器回调 afterCompletion，排后就**静默丢**"越权尝试"）；`afterCompletion` 异常参数**恒为 null** 且业务失败返 200 → 用 `ResponseOutcome.markFailure/failureOf` 取业务码（**新增 handler 必须调**）；`AuditRecorder` 用 `REQUIRES_NEW`，异常须留**调用方**（self-invocation 会让 `@Transactional` 静默失效）；**三套标识别混**：资源 `auth:user` / 权限码 `auth:user:create` / 动作 `auth.user.create`，填错**静默查空**
- **一处已记录的同层依赖** `sts-server-audit → sts-server-auth`（只读 `AuthContext` 与权限码），与架构文档「同层 ✗」不符；触发条件：**第二个非权限域的服务需要 `@RequiresPermission` 时**下沉词汇到 `sts-components`

## 8 口令与登录安全（0020）
- `PasswordPolicy`（纯函数）返回**未满足项列表**，**不做"复杂度等级"枚举**；`LoginLockPolicy` 指数退避 `base × growth^(n-1)` 封顶 `max`，**在 `double` 里比完再转 `long`**（先转可能溢出成负 = "锁到过去"）
- **两个计数分工**：`failed_attempts` = 本轮失败数（触发锁定后清零）· `lock_level` = 历史锁定次数（只在登录成功清零）
- **⚠️ 同事务写失败计数 = 永远不生效**：`@Transactional login()` 抛异常 ⇒ 同事务刚写的计数被回滚。正解 `LoginAttemptRecorder`（**独立 Bean** + `REQUIRES_NEW`，**按主键重读用户**）
- **守卫测试不能加 `@Transactional`**（未提交的写自己可见，正好掩盖"根本没写进库"）；**"读库断言"不证明"接口可见"** —— 带运营含义的状态必须同时进 VO，且读库与读接口两次断言成对写
- 历史按**条数**裁剪（`historyCount`）不按时间；建号 / 重置口令都必须走策略校验并写历史

## 9 微前端（0012）
基座 `loadMicroApp`（非 registerMicroApps+start，挂载点由路由出口给、位置唯一无竞态）；`sandbox:false`；`vite-plugin-qiankun`。**应用名是隐式契约**（不一致只卡"加载中"）。子应用 `base`：dev 必须 `/`（否则热更新**静默失效**）、build 才带前缀；须 `strictPort`+`cors`。通信走 `common-tools/micro`，**订阅必须 `onHostGlobalStateChange()`**，须在 `mount` 建立、`unmount` 断开。**被基座加载时组件级热更新不生效**

## 10 权限归属（0018）
会话**实现在 `packages/`、基座只装配** · 判定**基座单点算 → `HostGlobalState` 下发 `roles`/`permissions`**，子应用**不得自调 `/api/auth/me`** · **不开认证子应用**
- 权限码 = 后端 `AuthPermissions` 的 `域:资源:动作`，前端禁拼接自造；**权限不在令牌里** → `restore()` 走**刷新接口**、`/me` 留给 `reload()`；**401 刷新必须单飞**（轮换+重放检测），内核按客户端合并、**跨客户端须 `channel.ts` 再合并**；access 放内存、refresh 持久化（`bw.session`，**不含 accessToken**）
- 基座：**契约路径自带 `/api`** → `baseUrl` 留空 · **下发"认证能力"不下发令牌值**（`HostAuthBridge={getAccessToken, reauthenticate}`）· **跳转只留一处**（守卫 `<Navigate>`）、守卫须**等恢复结束**再判未登录
- `platform-admin`/`platform-audit-console`：**`AuthPort` 端口归一**（基座加载用桥 / 独立运行自建 channel）· **不含登录页**，独立运行自动登录**仅 `import.meta.env.DEV`** · **按钮级用显隐不用禁用** · 审计台**演练区按钮刻意不按权限显隐**。两者 `auth/` 逐字重复，触发条件：第三个管理型子应用时下沉
- 种子：admin **21 项**（user 4 + tenant 4 + resource 4 + role 4 + user_group 4 + audit 1）；`auditor` 3 项（含 **audit:read**），写操作应得 **40301**（仅 h2 开）

## 11 三维环境（0013~0015/0017，:3400）
**画布与上下文同生共死**（`dispose()` 必含 `forceContextLoss()`，否则 StrictMode 双跑抛 `reading 'precision' of null`）· **切场景只换场景图、先释放旧后建新**，`disposeSubtree()` 递归（含 `light.shadow.dispose()`）· **相机跟随必须整体平移**，移动算法只在纯内核 `player/motor.ts`，角度 `0`=朝 +Z；小地图自绘 canvas · 大气四层纯函数，**高度角唯一自变量** · **⚠️ 验证脚本按「帧」等不按毫秒**（无头 swiftshader 单帧 2~4 秒），判据 `diagnostics().frames` · 未做：寻路 / 移动动画 / 雨雪霜 / 风向量 / 星空月亮
