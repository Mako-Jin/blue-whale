# BlueWhale — 长期备忘

> 三类：**约定 · 坑 · 索引**。决策的论证与细则在 `docs/decisions/`，`docs/README.md` 有逐条摘要 —— 本文件**不复述**（复述曾膨胀到注入被截断）。

## 1 铁律
`main` @ `github.com/Mako-Jin/blue-whale`。`web/`(pnpm) · `server/`(Maven `com.bluewhale.sts`) · `docs/ scripts/ infra/`。
六条：① 两端不共用构建 ② 跨端只认 OpenAPI（前端 SDK **禁手写**）③ 共享物只放 `docs/` ④ `packages/*` **不反向依赖** `platform/*` ⑤ `platform-*` 互不引用、复用下沉 `packages/` ⑥ 后端**禁反向依赖与跨层跳跃**。
**改代码必须同提交改文档** · Husky 靠 `core.hooksPath web/.husky` · LF · Conventional Commits · **ADR 不回改旧文**、只在新文档建立指向 · 凭据在 `~/.git-credentials`、**不在终端解析**；无人值守 git 加 `-c credential.helper= -c credential.helper=store`（**不带它连凭据文件都不试**，报 `could not read Username` —— **不是网络问题，别查代理**）。

## 2 坑表（勿再排查）
- **环境** 一律 Bash · Node `spawn git`/`spawnSync`→EBUSY、`turbo run`→231（逐包直跑正常） · 沙箱注入端口 → `--server.port=8080` · `curl --noproxy '*'` · 长驻服务**各起一个**后台任务（**别 `| tail -N`**，会缓冲全部输出） · `git push/fetch` 常被代理阻断（502/Empty reply）→ 先重试，仍不行**只本地提交记领先数**、下轮先补推 · **e2e 不与前端构建并发**（假"掉登录"） · MySQL 便携版 `D:\tools\installer\mysql` · 系统 python3.11 坏 → 用受管 `…/3.13.12/python.exe`
- **敏感门控** 命令**参数**含口令字面量 → `SENSITIVE_APPROVAL=TIMED_OUT`。三条走法：grep **标识符**（不 grep 值）／管道加 `sed -E 's/"[^"]*"/"<redacted>"/g'`（**输出**遮蔽即可）／编辑这类文件写"按行号定位、正则吃掉值"的一次性脚本 · Windows 上 Node 的 ESM import 须 `file:///`
- **后端** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn`（PATH 是 17） · **`-pl` 必带 `-am`**（否则旧 jar → `NoSuchMethod`）· **但 `spring-boot:run` 反过来不能带 `-am`**（CLI goal 落到父工程、没有 main class）⇒ 正解两步：`-am install -DskipTests` 再 `-pl sts-app/sts-app-server spring-boot:run`；**本地仓 `D:\repository\maven`（不是 `~/.m2`）** · `-Dsurefire.failIfNoSpecifiedTests=false` · `mvn clean` 占用 → `jps -l`+`taskkill` · **MP `eq(条件,列,值)` 的"值"无条件求值** → 先取局部变量再判空 · **三目统一类型会拆箱** → 写 `if` · **同一元素挂两注解 = 解析期中止** → Lombok 没跑完 → **同批大批文件报"找不到符号"**；见 `getXxx()` 找不到就 **clean 重编**
- **前端** stylelint `extends` 须 `require.resolve` 绝对路径 · TS6 起 `baseUrl` 弃用 · ESLint10 flat config **默认导出数组**、只从 cwd 向上找 · typescript-eslint 须显式 `tsconfigRootDir` · `gen:api` 挂的 `prettier --write` **别去掉** · **格式欠账**：`lint-staged` 只处理**本次 `git add` 的文件** ⇒ 老文件一改就顺带重排全文（见过 494 行 diff 里 450 行漂移）；但**它的 cwd 是 `web/`** ⇒ 仓库根 `docs/`/`server/`/`scripts/` **永不被格式化**、"改了也不炸"**不必还**
- **子应用接线（0034）** 认证一律用 `common-api` 的 `createConsoleAuth`，**四应用逐字同形**（旧副本已收 —— 曾同一条分支两种写法、两个控制台丢掉那次拒绝；**新子应用照抄 `platform-admin` 的 `src/auth/index.ts` + `src/api.ts`**）。端口**构造即就绪**（令牌每次请求时求值）⇒ **不要**再引入 `initializeAuth()` 那种两段式初始化与**延迟创建**的 api 客户端 · `bindHost` 必须早于 `connectPermissionSource()`（`connect` **读一次**身份，判据是"**有没有宿主桥**"不是"有没有会话"） · **子应用组件测试**：权限 store 只有 `main.tsx` 会 connect ⇒ 测试里**必须自己 connect**，否则界面全空（与"没权限"同形）· `vi.mock('common-api')` 必须 **`importOriginal`**（替身要给**完整** `StandaloneIdentitySource`；**顶层调用 + mock 少一个导出 = 整文件加载失败**，表现为 0 个用例）· `getStandaloneSession()` 可空，测试写局部辅助**显式断言**、别到处 `!`
- **e2e** CDP 求值须容忍"那刻正在导航"→ 重试 · 浏览器侧报错**要让脚本非零退出**，但要**分两类**：脚本层（未捕获异常/未处理的拒绝）**任何模式都判失败**；网络层（`source==='network'` 的非 2xx）在"依赖没起"的模式下**属于场景本身**、只计数 · **断言选择器必须是"权限产物"、不能是"布局外壳"**（`admin-tabs`/`audit-identity` 无条件渲染 ⇒ 造出"后端没起却出现鉴权内容"这类假问题） · **收尾两样都要收**：`ws?.close()` + 兜底 `process.exit`（只 `chrome.kill()` ⇒ "结论已打出、进程不结束"，流水线里表现为**超时**；三个老脚本曾漏，已补） · 基座有登录守卫 → **先真实登录** · **⚠️ dev 未设 `strictPort`** → 残留进程占端口时**静默跳号** ⇒ 打到旧实例 = 假回归 → **起 dev 前 `netstat` 清残留、起完逐端口 `curl` 验位** · CDP 改输入框用**原型原生 setter + 手动派发 `input`**
- **⚠️ dev server 冷热是两种环境**（0035）`qiankunDevPreamble()` 为让 qiankun `eval` 执行，把 React preamble 从**模块脚本**改成 **async IIFE** ⇒ 丢顺序保证，入口的 `import(main.tsx)` 与 `import('/@react-refresh')` **抢跑**，冷启动时抢输 ⇒ **单跑首帧白屏** + `can't detect preamble. Something is wrong.`（刷新就好）。现已让入口等 preamble 的 Promise；**改启动路径后 e2e 要先重启 dev 再跑** · **"看起来像回归"的也要先做冷热对照**：只重启一个 dev 服务、其余保持热，就能判定是不是自己的改动
- **nginx**（`D:\tools\installer\nginx\nginx-1.20.1`，**只读拷贝**到 `/tmp`）**`envsubst` 必须白名单形式**，否则吃掉 nginx 自己的 `$remote_addr` 等、配置看着合法但限流转发全失效 · `limit_req`/`limit_conn` 对**空 key** `NGX_DECLINED`（不计入）→ 靠 `map $request_method` 置空"只限 POST" · `limit_req_zone`/`map`/`upstream` 只能在 **http 上下文**（落 `conf.d/`）· **`limit_req_status` 默认 `503` 不是 `429`**（否则 `error_page 429` 永不触发）· **zone 名写错 `nginx -t` 当场报** `zero size shared memory zone` · `limit_req` **可同 location 写多次** · **全局桶 key 写成 `$binary_remote_addr` 语法合法** → 退化成第二个按 IP 桶、`nginx -t` 仍 successful · **⚠️ 压限流必须有活的后端**（全 502 时每次连接被拒约 2 秒 → 间隔被拉长到低于阈值，**一个 429 都不出**）
- **凭据进产物**（静默）**模块级常量**、或在**提前返回之后**才引用 ⇒ 看着"生产不会用"、实际**被保留进产物**（实测两个控制台各残留一份种子口令，另两个只是恰好被摇掉）。正解：`devAccount: import.meta.env.DEV ? {…} : undefined`；**唯一可靠验证是 `scripts/verify-bundle-secrets.mjs` 扫 `dist`**

## 3 ADR 索引
0001 基线 · 0002 公共包 · 0003 配置分域 · 0004 日志上报 · 0005 模块划分 · 0006 契约生成消费 · 0007 请求分层 · 0008 Boot4/JDK21 · 0009 多数据源与 Flyway · 0010 达梦 DM8 · 0011 行为事件 · 0012 微前端 · 0013 WebGL 生命周期 · 0014 多场景 · 0015 场景交互 · 0016 权限域服务 · 0017 大气与天气 · 0018 权限归属 · 0019 审计日志管道 · 0020 口令与登录安全 · 0021 资源目录与权限码约束 · 0022 角色与角色资源授权 · 0023 角色成员关系 · 0024 用户组与成员归属 · 0025 数据范围与审计可见性 · 0026 日志与事件的鉴权与租户隔离 · 0027 上报限流归网关层 · 0028 上报限流阈值标定与网关侧实测 · 0029 种子口令不得有默认值 · 0030 组织树与数据范围求值 · 0031 菜单与页面级权限过滤 · 0032 日志链路的条数上限与稳定排序 · 0033 开发凭据只在开发构建里构造 · 0034 子应用认证接线统一到 createConsoleAuth · 0035 独立运行时入口必须等 preamble 装好

## 4 结构 / 栈
React19+Vite8+TS**6.0.3**(7.x 不被 typescript-eslint 支持)+Router7；qiankun2；pnpm+Turborepo；Less；ESLint10/Prettier3/Stylelint17/Vitest5。后端 Java21+Boot4.1.1+MP3.5.17；OpenAPI 3.1。
端口：playground 3100 · portal 3000 · log 3200 · event 3300 · environment 3400 · admin 3500 · audit 3600。
规范只在 `common-config` · **各应用 LESS 令牌各自一份、禁跨应用引用**（引了只有 `vite build` 报） · `catalog:` 是版本唯一来源 · **按需再建，不空建空壳** · 配置三层(0003)。

## 5 后端 / 契约 · 跨切面不变量（**只记 ADR 里没有的**）
- 依赖方向 `sts-app→sts-server→sts-components→sts-common→sts-parent`、`sts-app-server` 唯一可启动、**按特性分包**；**业务失败返 HTTP 200**（判 `code===0`）。Boot4 升级 **starter 三处同动**（`web→webmvc`、`boot3-starter→boot4-starter`、springdoc `2.x→3.x`）；MockMvc 引 `spring-boot-starter-webmvc-test`；Jackson3 import `tools.jackson.*`
- **Flyway** 不写 `IF NOT EXISTS`、**已执行脚本不可改**、**版本号全仓唯一**（log V1·V6 / track V2·V7 / auth V3·V5 / audit V4）；切 mysql 须显式引 `flyway-mysql`；达梦 `flyway.enabled=false` 走 `scripts/init-dameng.sh`（**未用真实实例验证**）。MySQL 保留字正解**属性名与列名同构**且不加 `@TableField`；**两库保留字不可互推**；达梦 `VARCHAR(n CHAR)`
- **权限域** `bw_auth_*` 13 表；错误码 5 位 = HTTP 3 位 + 业务 2 位（`40001~40918`）；自研 HS512+PBKDF2、**不引 Spring Security**；`@RequiresPermission`+`AuthInterceptor`（**白名单反着写：无注解即公开**）；`AuthTokenFilter` 不可同时 `@Component` 又进 `FilterRegistrationBean`；`#{it.xxx}` OGNL **编译器管不到**；权限项 admin **45** / auditor **14**
- **三条静默缺陷**（写了但不生效、**且不报错**，要反复问）① **新增影响权限的开关 → 问"判定里读它了吗"**（已抓 5 处：DENY 跨角色 / 停用角色 / 时态 / **停用用户组** / 组链路时态；**判定越严、反查越宽**；**嵌套组判定只跳一层** → `bindMember` 拒绝 `GROUP` 兜住）② **软删+唯一键**（资源/角色/用户组同坑）：唯一键**不含 `deleted`** → 重建同码**撞键** → 正解**复活原记录**、并**一并清空成员与授权**（`40900` 由 `DuplicateKeyException` 处理器填；`spring-tx` 该模块必须 **compile**）③ **`updateById` 的 `NOT_NULL` 清不掉字段**（传 null 进不了 `SET`、0 行不报错）→ 用 `update(new Entity(), lambdaUpdate().set(...))` **逐列显式 set**、传**空实体**
- **权限项登记与两份声明**：`AuthPermissions.ALL` 是种子资源行的**全仓唯一来源**（漏登记 = 入口/权限**永不出现**）；`AuthMenus`(后端) 与 `common-api/menus.ts`(前端) 各一份由测试**逐字**钉住 ⇒ **改码必须同改两端**；`ui:` 码是**布局**、读权限是**安全边界**（撤销菜单码**不拦接口**，不是收窄工具）；**菜单不进 OpenAPI**；**排序四处对齐**（`AuthMenus`=`menus.ts`=`TABS`=`MICRO_APPS`）
- **前端跨端坑**：权限 store 住 `common-tools/micro`（**不引 React**，三行适配留各应用）；`visibleViews()` 未就绪返回**空列表**（"不确定"≠"都可以看"）；`ready` 看 `userId` 非空；独立运行身份经 `StandaloneIdentitySource` 暴露（`ConsoleAuth` 实现它）；**`OrganizationTreeNode` 与 `OrganizationVO` 不是子集关系** ⇒ 拿树节点填编辑表单会**以空值提交** ⇒ **树给结构、详情给表单初值**（`openEdit` 必须先 `detail(id)`）；「是否主岗」是**三态** `'' | '1' | '0'`；`visibleOrgIds` 的 **`null` 与 `[]` 必须说成两句不同的话**
- **契约生成（0006）** 代码 → springdoc → `scripts/export-openapi.sh`(:18080，**须 `JAVA_HOME=<jdk21>`**) → `docs/api/openapi.json`（**67 接口 / 43 路径 / 100 模型**）→ `openapi-typescript` → `common-api` 的 `schema.d.ts`（入库）。**`@Schema` 的 description 会进 `schema.d.ts`** ⇒ 改它必须重导 + `pnpm --filter common-api gen:api`；`@Operation` 只进 `openapi.json`

## 6 其余分域（**只记陷阱**）
- **三条管道（0004/0011/0019）** 审计**无写接口**、**匿名不记 / 查询不记**、`AUDIT(-100) < AUTHORIZATION(-50)`、失败仍 200 → `ResponseOutcome.markFailure/failureOf`（**新增 handler 必调**）、`AuditRecorder` 用 `REQUIRES_NEW`；**三套标识别混**：资源 `auth:user` / 权限码 `auth:user:create` / 动作 `auth.user.create`；**登录先 `updateContext` 再 `track`、登出先 `track` 再 `clearUser()`**
- **口令（0020）** **⚠️ 同事务写失败计数 = 永远不生效** → `LoginAttemptRecorder`(`REQUIRES_NEW`)；**守卫测试不能加 `@Transactional`**；指数退避**在 `double` 里比完再转 `long`**
- **权限归属（0018）** 会话**在 `packages/`、基座只装配** · **基座单点算 → `HostGlobalState` 下发**、子应用**不得自调 `/me`** · **权限不在令牌里** → `restore()` 走刷新、**401 刷新必须单飞** · 子应用**无登录页**、**按钮级只显隐不禁用**（写回 **40301**）
- **微前端（0012）** **应用名是隐式契约** · `sandbox:false` · 子应用 `base` dev `/`、build 带前缀 · **订阅必须 `onHostGlobalStateChange()`** · **被基座加载时组件级热更新不生效**（已知限制、根因未定位）
- **三维（0013~0015/0017）** **画布与上下文同生共死**（`dispose()` 含 `forceContextLoss()`）· **切场景先释放旧后建新** · **相机跟随整体平移** · **验证按「帧」等不按毫秒**（判据 `diagnostics().frames`）

## 7 待办池 / 刻意不做
**待办**：`platform-user` 未创建（本仓无业务数据表，承载什么还没答案 ⇒ **先讨论再动手**）→ **新账号默认没有任何菜单码**（表现为"没有可进入的模块"）→ 自助改密接口（0020 遗留）→ `bw_auth_rel_org_user` 补唯一索引（需新迁移；**堵不住"两个主岗"**）→ `CUSTOM` 的 `filter_json` 解析 → **React 组件级 HMR 在 qiankun 下不生效**（0012 的已知限制；0035 确认**不是** preamble 改写引起的，值得单独一轮）
**刻意不做（是决策不是待办，别"顺手接上"）**：把 `DEPT` 接进审计可见性（0030 第 10 条）；下沉四个子应用的 `usePermission` 适配层（重复的只是 2-4 行不变的代码；**基座的 `usePermissions()` 不是重复** —— 它读基座自己的会话，分层不同）；把登录页开发提示文案并入产物自检（文案是**会变的值**，写进检查会静默失效）
**未覆盖（记录，不催办）**：0028 慢速攻击 / 多实例单机内存 zone / 阈值标定需真实流量 · 0029 密钥级配置外部化 · 0031 新账号的菜单授权体验
