# BlueWhale — 长期备忘

> 只留三类：**约定 · 坑 · ADR 索引**。论证在 `docs/decisions/`，流水在 `.workbuddy/memory/*.md`，细节在各 README。

## 1 铁律
`main` / `github.com/Mako-Jin/blue-whale`。`web/`(pnpm) · `server/`(Maven `com.bluewhale.sts`) · `docs/ scripts/ infra/`。六条：① 两端不共用构建 ② 跨端只认 OpenAPI（前端 SDK **禁手写**）③ 共享物只放 `docs/` ④ `packages/*` **不反向依赖** `platform/*` ⑤ `platform-{user,admin}` 互不引用、复用下沉 `packages/` ⑥ 后端**禁反向依赖与跨层跳跃**。
**改代码必须同提交改文档** · Husky 靠 `core.hooksPath web/.husky` · LF · Conventional Commits · 凭据在 `~/.git-credentials`、**绝不在终端解析**；无人值守 git 加 `-c credential.helper= -c credential.helper=store`。

## 2 坑表（勿再排查）
- **环境** 一律 Bash · Node `spawn git`→EBUSY、`turbo run`→231（逐包直跑正常） · 沙箱注入端口 → `--server.port=8080` · `curl --noproxy '*'` · 长驻服务**每个单独一个**后台任务 · `git push/fetch` 曾被代理阻断（502；直连 `curl` 000 而 `api.github.com` 正常）→ 先重试、不行只本地提交记领先数 · git 连接重置 → `-c http.version=HTTP/1.1` · **e2e 不与前端构建并发**（CPU 抢占 → 假"掉登录"） · MySQL 便携版 `D:\tools\installer\mysql` · 系统 python3.11 坏 → 用受管 `…/3.13.12/python.exe`
- **后端** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn`（PATH 是 17） · **`-pl` 必带 `-am`**（否则拿旧 jar → `NoSuchMethod`） · `-Dsurefire.failIfNoSpecifiedTests=false` · `mvn clean` 占用 → `jps -l`+`taskkill` · **MP `eq(条件,列,值)` 的"值"无条件求值** → `x.getY().trim()` 在 `getY()==null` 照样 NPE → **先取局部变量** · **三目统一类型会拆箱**（`long`/`Long`，`null` 时 NPE）→ 写 `if` · **同一元素挂两注解 = 解析期即中止** → Lombok 没跑完 → **同批一大批文件报"找不到符号"**；增量编译也丢 Lombok 方法 → 见 `getXxx()` 找不到就 **clean 重编**
- **前端** stylelint `extends` 须 `require.resolve` 绝对路径 · TS6 起 `baseUrl` 弃用 · ESLint10 flat config **默认导出数组**、只从 cwd 向上找 · typescript-eslint 须显式 `tsconfigRootDir` · **`openapi-typescript` 输出 4 空格双引号，入库是 Prettier 归一后样子** → `gen:api` 挂的 `prettier --write` **别去掉**；prettier 在工作区根 → `web/node_modules/.bin/prettier`
- **e2e** CDP 求值须容忍"求值那刻在导航"→ 重试 · **浏览器侧报错必须让脚本非零退出** · 基座有登录守卫 → **先真实登录** · **⚠️ dev 未设 `strictPort`** → 残留进程占端口时**静默跳号**、e2e 打固定 3000 → 打到旧实例 = 假回归 → **起 dev 前 `netstat` 清残留、起完逐端口 `curl` 验位**
- **网关 / nginx** 本机**有 nginx 可跑**：`D:\tools\installer\nginx\nginx-1.20.1`（**只读拷贝**到 `/tmp` 用，别原地跑）· **`envsubst` 必须白名单形式**（`envsubst '${A} ${B}'`），否则 nginx 自身的 `$remote_addr`/`$request_method`/`$proxy_add_x_forwarded_for` 会被一起吃掉、配置看着合法但限流转发全失效 · `limit_req`/`limit_conn` 对**空 key** 直接 `NGX_DECLINED`（不计入）→ 靠 `map $request_method` 置空来"只限 POST" · `limit_req_zone`/`map`/`upstream` 只能在 **http 上下文**（故落 `conf.d/`）· **`limit_req_status`/`limit_conn_status` 默认是 `503` 不是 `429`**（必须显式设，否则 `error_page 429` 永不触发、超限伪装成"服务不可用"）· **zone 名写错 `nginx -t` 当场就报** `[emerg] zero size shared memory zone`（**不是**"只在启动时" —— 曾把工具想得比实际弱）· `limit_req` **允许同一 location 写多次**（两层限流才能共存）· **全局桶的 key 写成 `$binary_remote_addr` 时语法完全合法** → 退化成第二个按 IP 桶，`nginx -t` 仍 `test is successful`（只能靠自检脚本挡）
- **⚠️ 压限流必须有活的后端** 后端没起时请求全 502，而**每次连接被拒耗时约 2 秒** → 把 15 个请求的间隔拉成 0.45 req/s，低于阈值**自然一个 429 都不出**（会误判成"脚本/配置没生效"）。压之前起个常驻极简上游、`netstat` 逐个确认 `LISTENING`
- **沙箱** 命令里出现**口令字面量** → 敏感内容门控（`SENSITIVE_APPROVAL=TIMED_OUT … Do not retry`）→ 改用"只输出长度/布尔值"的探针 · Windows 上 Node 的 **ESM import 必须 `file:///` 前缀**（否则 `ERR_UNSUPPORTED_ESM_URL_SCHEME: Received protocol 'd:'`） · `Node spawnSync` 同样 EBUSY（禁创建进程）→ 校验脚本要留"渲染出目录树 + 由 shell 手跑"的形态

## 3 ADR 索引
0001 基线 · 0002 公共包 · 0003 配置分域 · 0004 日志上报 · 0005 模块划分 · 0006 契约生成消费 · 0007 请求分层 · 0008 Boot4/JDK21 · 0009 多数据源与 Flyway · 0010 达梦 DM8 · 0011 行为事件 · 0012 微前端 · 0013 WebGL 生命周期 · 0014 多场景 · 0015 场景交互 · 0016 权限域服务 · 0017 大气与天气 · 0018 权限归属 · 0019 审计日志管道 · 0020 口令与登录安全 · 0021 资源目录与权限码约束 · 0022 角色与角色资源授权 · 0023 角色成员关系 · 0024 用户组与成员归属 · 0025 数据范围与审计可见性 · 0026 日志与事件的鉴权与租户隔离 · 0027 上报限流归网关层 · 0028 上报限流的阈值标定与网关侧实测 · 0029 种子口令不得有默认值 · 0030 组织树与数据范围求值

## 4 结构 / 栈
React19+Vite8+TS**6.0.3**(7.x 不被 typescript-eslint 支持)+Router7；qiankun2；pnpm+Turborepo；Less；ESLint10/Prettier3/Stylelint17/Vitest5。后端 Java21+Boot4.1.1+MP3.5.17；OpenAPI 3.1。
端口：playground 3100 · portal 3000 · log 3200 · event 3300 · environment 3400 · admin 3500 · audit 3600。
规范只在 `common-config` · **各应用 LESS 令牌各自一份、禁跨应用引用**（引了只有 `vite build` 报） · `catalog:` 是版本唯一来源 · **按需再建，不空建空壳** · 配置三层(0003)。

## 5 后端
- 依赖 `sts-app→sts-server→sts-components→sts-common→sts-parent`（`<version>` 只在 parent）；`sts-app-server` 唯一可启动；**按特性分包**；**业务失败返 HTTP 200**（判 `code===0`）
- Boot4 starter 三处同动（`web→webmvc`、`boot3-starter→boot4-starter`、springdoc `2.x→3.x`）；MockMvc 引 `spring-boot-starter-webmvc-test`；Jackson3 import `tools.jackson.*`；enforcer `[21,)`
- **Flyway** 不写 `IF NOT EXISTS`、**已执行脚本不可改**、**版本号全仓唯一**（log V1·V6 / track V2·V7 / auth V3·V5 / audit V4）；切 mysql 须显式引 `flyway-mysql`；达梦 `flyway.enabled=false` 走 `scripts/init-dameng.sh`、**未用真实实例验证**。MySQL 保留字正解**属性名与列名同构**且不加 `@TableField`；两库保留字**不可互推**；达梦 `VARCHAR(n CHAR)`
- **权限域** `bw_auth_*` 13 表、错误码 `40001~40918`；自研 HS512+PBKDF2、**不引 Spring Security**；`@RequiresPermission` + `AuthInterceptor`（**白名单反着写：无注解即公开**）；`AuthTokenFilter` 不可同时 `@Component` 又进 `FilterRegistrationBean`；`#{it.xxx}` OGNL **编译器管不到**
- **配置侧已闭环**、前端管理面已接通（20 条路径 / 五标签页）；**组织域后端已全量（0030，8 路径/12 接口）**，前端**刻意未接的三项**：数据范围反查、**组织树**、成员/主岗
- **软删+唯一键三处同坑**（资源/角色/用户组）：唯一键**不含 `deleted`** → 重建同码**撞键** → 正解**复活原记录**并**一并清空成员与授权**。`40900` 由 `DuplicateKeyException` 处理器填上；`spring-tx` 该模块必须 **compile**
- **`updateById` 的 `NOT_NULL` 清不掉字段**（传 null 进不了 `SET`，0 行不报错）→ `update(new Entity(), lambdaUpdate().set(...))` 逐列显式 set、传**空实体**
- **判定侧静默缺陷五处**（DENY 跨角色/停用角色/时态/**停用用户组**/组链路时态）→ **凡新增影响权限的开关都要问"判定里读它了吗"**；判定越**严**、反查越**宽**；**嵌套组判定只跳一层** → `bindMember` 拒绝 `GROUP` 兜住
- **数据范围(0025)** 只做**求值+下发**：**取最宽**（ALL5>DEPT_AND_CHILD4>DEPT3>CUSTOM2>SELF1）；`precise` 区分"已配置/已生效"；**算不出往窄兜底**（只 ALL 放行全租户）；无档位 `scope` 为 null。**本仓无业务数据表** ⇒ 过滤业务数据**无落点**
- **组织域(0030)** `DEPT` 系列从"**永远算不出**"变成**可精确求值**（此前 `bw_auth_organization`/`bw_auth_rel_org_user` 表+实体+Mapper 全在却**零引用** ⇒ 表恒空 ⇒ **"只读的组织接口"没价值**，故写入路径必须与读取同批交付）。① 层级=**物化路径**（`org_level`/`code_path` 形如 `/1/5/`/`full_name`），三者是 `parentId` 与节点名的**确定函数** ⇒ **不接受入参**；② `code_path` 前缀**同时是求值口径与重写边界**（共用同一事实是有意的）；③ **移动是独立接口+独立动作**（`auth.org.move`）——重写整棵子树 ⇒ **子树上每个人的范围同时变**；修改路径带不同 `parentId` **显式 40001 不静默忽略**（与用户组 `parentId` **判断相反**）；④ **移动要重算五样**，其中「**`parent_id` 本身要落库**」是**抓出的真缺陷**：`updateColumns` 原本不写它 ⇒ 移动后 `code_path` 已指新父（**范围已搬家**）而 `parent_id` 还在旧父（**树显示在原地**）、**两处都不报错**；抓它的断言 =「移动后**原父级 `hasChild` 应归 0**」（`hasChild` 按 `parent_id` 数）；同坑影响"同码复活"分支 ⇒ 也因此必须**显式 `set`**（复活可能要写 `null`）；⑤ **主岗三规则**：每人每租户至多一个（**该表连 `(org_id,user_id)` 唯一索引都没有** ⇒ 全靠服务层"先降级别处再设自己"）、**不静默搬主岗**（不传时：没主岗→自动设、已有→保持）、**时态参与求值**；设主岗要求先有归属否则 `40410`；⑥ 成员挂组织下、**路径无 `memberType`**（该表只装用户）；⑦ `DataScope.precise` 改名 **`selfSufficient`**（旧名混淆"自足"与"能精确求值"，后者是**合并阶段**的问题）+ 新增 `needsOrgContext()`；⑧ 有有效主岗才 `precise=true` 并下发 `visibleOrgIds`（`DEPT` 只主岗那一个 / `DEPT_AND_CHILD` 取**启用的**子树），否则**往窄兜底**（结论与 0025 一字未改）；⑨ **推翻 0025 第 9 条**（返回"可见组织集合"，消费方=反查接口本身）但**它不是过滤条件的输入**；⑩ **审计可见性"理由变了、结论没变"**：仍是"只有 ALL 放行全租户"——把 `DEPT` 接进审计是**静默放宽**（`bw_audit_log` 无组织维度、只能退化成 `operator_id IN (该部门的用户)`、保留期长）⇒ **刻意的"不做"**；⑪ 种子种「总部>研发部>前端组」并挂 admin/auditor，**复用服务而非重抄派生规则**（`ensureMembership` **不传 `isPrimaryPosition`** ⇒ 重启**不把运维改过的主岗改回来**）；⑫ 删除两码分开（有下级 `40913` / 有成员 `40919`）、**无级联**；⑬ `org_name` **不校验唯一**，`40909` 仍预留。权限 23→**27**、动作 +7（移动与设主岗**各自独立**）、错误码 +2（`40410`/`40919`）；契约 **43 路径/67 接口/100 模型**；后端 **161→174 例**
- **审计可见性(0025)** 租户**无条件**强制、`operatorId` 只在 ALL 档生效、跨租户详情 **404 非 403**、`countByAction` 必传 `tenantId`
- **日志/事件准入(0026)** 两域查询接口此前**完全公开**且**表无 `tenant_id`** → 持查询权者读到**全部租户**。修法：**上报公开 / 查询鉴权** · 新域前缀 `log:client`/`event:client`（登记在 `AuthPermissions.ALL`，**全仓唯一登记处**）· 三个读码**刻意不合并** · **归属只取服务端会话**（事件里的 `user_id` 是上报方填的、**不是归属依据**）· 租户条件**恒定加上、不做成可关形式** · 聚合手写 SQL **参数必传** · 详情跨租户 **404 且"不存在/不是你的"同码** · `tenant_id` 可空但 **NULL 对任何租户都不可见** · **历史数据不回填**。admin **27** 项 / auditor 3 项
- **菜单权限差两步且必须同做**：① 补 `MENU`/`PAGE` 资源行 ② 基座按权限过滤导航；**先定三件事**：菜单码粒度 / 审计员可见哪些应用 / `概览`是否纳管
- **上报限流归网关层(0027)** `infra/nginx/`，**应用层与前端零改动**（无新依赖/接口/权限码，契约不变）。理由：三条既有约定（`infra/README.md` 约定 4「限流不要散落在各应用里」· `0003` 限流阈值属部署级 · 后端 5 处「`X-Forwarded-For` 不可作风控依据」）—— 而**第三条被误读**：它成立是因为"应用无法判断前面有几跳"，nginx 知道。**真实 IP 分三态**（前方有代理必须开 `set_real_ip_from` / 本机是边缘绝不能开 / 开了只写自己那台代理），配错两个方向都不报错。**`42900` 由网关产生、后端不产生**。测：`scripts/verify-nginx-config.mjs`（不需要 nginx，补 `nginx -t` 查不到的几类错，已用两个负例验证不空转）
- **上报限流是两层且标定规则不同(0028)** 病根是**把"隔离"与"容量"压在同一阈值上**。① 按 IP（`$bluewhale_report_key`）= **隔离**，按**最大的 NAT 出口**算 `N/3 r/s`（0027 初版 20r/s 把"1 客户端=1 IP"当前提，只够约 60 人）；② 全局（常量 `"bluewhale-report"`）= **容量上限**，按后端能吸收的写入速率、**与人数无关**（300r/s × 20 条/批 ≈ 6000 条/秒）。**429 ≠ 丢弃**：`BatchChannel` 的 `flush()` 失败时把整批 `buffer.unshift()` **退回队首**、退避 `min(interval × 2^n, 30s)`、只有装不下才 `dropped`，`push()` 溢出丢**最旧** ⇒ 短暂超限=延迟、持续超限=丢最旧一批。实测：`2r/s burst2` → `200×3/429×12`；同 URI 的 15 个 GET **全 200**（空 key 不计入）；等 2.2s 后桶**按 rate 回填**。脚本 `--emit`（渲染目录树、shell 手跑 `nginx -t`）/ `--nginx`（探路径可用性，把"启动不了"与"配置错"分开报）
- **种子口令不得有默认值(0029)** `AuthProperties` 的两个 `password` **清空**（有默认值 ⇒ 任何环境只要"开种子 + 不覆盖口令"就得到一个**口令公开可查的管理员**，而 `application-mysql.yml` 的说明**恰恰教人这么开**）。修法选**拒绝启动**（`DefaultAdminSeeder#requirePassword`，两个校验**提到所有落库之前**）—— "跳过种子"会安静地不种而运维以为有、**"给默认口令"就是要消灭的那件事本身**。开发口令真值来源**收拢到 `application-h2.yml`（全仓唯一一处）**。`DefaultAdminSeederTest` 用**依赖全传 null** 钉住"先校验、再落库"的顺序。**0027 记错了两环**：登录页初值是空串（非 `@Schema`）、`@Schema(example)` 只落 JSDoc 注释**不进产物**，**真正把口令带进包的是登录页把口令印在页面上** → 门控用 `import.meta.env.DEV`，**验证靠构建后 grep 产物且查三条**（口令/提示正文/提示首句）。凭据副本收敛 `scripts/lib/seed-credentials.mjs`（**不引 YAML 库**）
- **append-only 事实表不继承 `BaseEntity`**：`RefreshTokenEntity`、`PasswordHistoryEntity`

## 6 契约 / 请求 / 其余分域
- **契约(0006/0007)** 代码 → springdoc → `scripts/export-openapi.sh`(:18080，**须 `JAVA_HOME=<jdk21>`**) → `docs/api/openapi.json`（**67 接口 / 43 路径 / 100 模型**）→ `openapi-typescript` → `common-api` 的 `schema.d.ts`（入库，提交前 Prettier）。`http`=传输内核、`common-api`=装配；`R<T>` 收口、非 0 码抛错、超时+取消合并一个 AbortController；**内核不自动重试**、**超时先于取消分类**。子应用认证走 `createConsoleAuth`（宿主桥优先；`allowDevAutoLogin` 由调用方传 `import.meta.env.DEV`）
- **三条管道(0004/0011/0019)** 日志(可采样)·事件(不可采样)·**审计(全量/后端产生)**。审计**无写接口**、**匿名不记/查询不记**、`AUDIT(-100) < AUTHORIZATION(-50)`、失败仍 200 → `ResponseOutcome.markFailure/failureOf`（**新增 handler 必调**）、`AuditRecorder` 用 `REQUIRES_NEW`；**三套标识别混**：资源 `auth:user`/权限码 `auth:user:create`/动作 `auth.user.create`；事件名正则 `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`；**登录先 updateContext 再 track、登出先 track 再 clearUser()**
- **口令(0020)** **⚠️ 同事务写失败计数 = 永远不生效** → `LoginAttemptRecorder`(`REQUIRES_NEW`)；**守卫测试不能加 `@Transactional`**；指数退避**在 `double` 里比完再转 `long`**；`failed_attempts`=本轮失败数（锁定后清零）·`lock_level`=历史锁定次数（仅成功清零）
- **权限归属(0018)** 会话**在 `packages/`、基座只装配** · **基座单点算 → `HostGlobalState` 下发**、子应用**不得自调 `/me`** · **权限不在令牌里** → `restore()` 走刷新、**401 刷新必须单飞** · `platform-{admin,audit-console}` **无登录页**、**按钮级显隐不禁用**（写回 **40301**）
- **微前端(0012)** **应用名是隐式契约** · `sandbox:false` · 子应用 `base` dev `/`、build 带前缀 · **订阅必须 `onHostGlobalStateChange()`** · **被基座加载时组件级热更新不生效**
- **三维(0013~0015/0017，:3400)** **画布与上下文同生共死**（`dispose()` 含 `forceContextLoss()`）· **切场景先释放旧后建新** · **相机跟随整体平移**（`player/motor.ts`）· **验证按「帧」等不按毫秒**（判据 `diagnostics().frames`）

## 8 ⚠️ 已知风险 / 待办池

**做 0029 时记下的（原始那条已闭环）**：种子口令"公开"链路**已修**（见 0029）—— `AuthProperties` 无默认值、开种子不给口令**拒绝启动**、登录页提示 DEV 门控、凭据收敛到 `scripts/lib/seed-credentials.mjs`。但**两条刻意留着**：

- **4 个子应用 dev 账号的惰性字面量**：`password: import.meta.env.VITE_DEV_PASSWORD ?? "Admin@12345"` 会留在生产包里（字面量替换），但 `allowDevAutoLogin` 为 `false` ⇒ **从不被使用**，比登录页那条轻。干净修法要动 `common-api`（被所有子应用依赖的共享包）→ **修法已定**：`devAccount?: …` + 各调用点 `import.meta.env.DEV ? {...} : undefined`
- **4 个后端测试仍写字面口令**：应改用 `properties.getSeedAdmin().getPassword()` —— 是**文档一致性**（跟着配置走，口令再改不会以"登录失败"的形式炸），不是安全问题

**0029 未覆盖**：自助改密接口（`0020` 遗留，防重用目前只在管理员重置路径上生效） · 密钥级配置的外部化（`must_change_password` 一类）

**0028 未覆盖**：慢速攻击 / 多实例单机内存 zone / 阈值定得合不合适（需真实流量）/ 采样

**0030 未覆盖**：前端管理页（组织树/成员/主岗） · `CUSTOM` 的 `filter_json` 解析 · **把 `DEPT` 接进审计可见性（刻意不做）** · 给 `bw_auth_rel_org_user` 补 `(org_id,user_id)` 唯一索引（需新迁移脚本，`0009` 禁改旧脚本）

**待办池（按上轮建议顺序）**：~~组织管理接口~~（**已完成，见 0030**）→ 前端组织管理页（顺带接上数据范围反查的显示）→ 菜单权限两步（**须同做**）→ `listByTraceId` 加条数上限
