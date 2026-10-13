# BlueWhale — 长期备忘

> 三类：**约定 · 坑 · 索引**。决策论证在 `docs/decisions/`，逐篇摘要与索引在 `docs/README.md` §四（`verify-docs-index.mjs` 保证不漏登记），应用级坑表在 `AGENTS.md`，脚本细则在 `scripts/README.md` —— **本文件不复述它们**，只留"仓库别处没有的"：本机/沙箱、提交习惯、跨篇不变量、待办。（复述曾膨胀到注入被截断。）

## 1 铁律
`main` @ `github.com/Mako-Jin/blue-whale`。`web/`(pnpm) · `server/`(Maven `com.bluewhale.sts`) · `.github/ docs/ scripts/ infra/`。
六条：① 两端不共用构建 ② 跨端只认 OpenAPI（前端 SDK **禁手写**）③ 共享物只放 `docs/` ④ `packages/*` **不反向依赖** `platform/*` ⑤ `platform-*` 互不引用、复用下沉 `packages/` ⑥ 后端**禁反向依赖与跨层跳跃**。
**改代码必须同提交改文档** · Husky 靠 `core.hooksPath web/.husky` · LF · Conventional Commits · **ADR 不回改旧文**、只在新文档建立指向 · **CI 入口只能 `.github/workflows/`**（平台硬要求）。

## 2 本机 / 沙箱坑（**仓库别处没有**，勿再排查）
- **环境** 一律 Bash · **python 两个都不能用**（受管 3.13.12 会去加载系统 3.11 的 `sitecustomize` → `SRE module mismatch`）⇒ 要脚本就用 Node · `Node spawnSync` → EBUSY（`verify-nginx-config --nginx` 分支会踩）· 沙箱注入端口 → `--server.port=8080` · `curl --noproxy '*'` · 长驻服务**各起一个**后台任务（别 `| tail -N`，会缓冲全部输出）· `turbo run` 现在**能跑**（旧的"231"记录已过时）· **e2e 不与前端构建并发**（假"掉登录"）· MySQL 便携版 `D:\tools\installer\mysql` · nginx 只读包 `D:\tools\installer\nginx\nginx-1.20.1`
- **git** 凭据在 `~/.git-credentials`、**不在终端解析**；无人值守加 `-c credential.helper= -c credential.helper=store`（**不带它连凭据文件都不试**，报 `could not read Username` —— 不是网络问题，**别查代理**；`unable to write credential store: Permission denied` **无害**）· 推送**不是持续坏的**，每次先试一次再决定是否只本地提交 · **提交时索引可能被多带几个文件**（实测 4 个：两个 memory md、一个 `scripts/` 脚本、`web/README.md`），内容无损但**分组差一拍**；单独跑 `lint-staged` **复现不出**、根因**未定位** ⇒ 提交用 `git commit -F - -- <paths>`（绕过索引）、提交后 `git show --name-only` 复核、**别在工作区堆着无关改动时提交**
- **敏感门控** 命令**参数**含口令字面量 → `SENSITIVE_APPROVAL=TIMED_OUT`。三条走法：grep **标识符**（不 grep 值）／管道加 `sed -E 's/"[^"]*"/"<redacted>"/g'`（遮蔽**输出**即可）／一次性脚本"按行号定位、正则吃掉值" · Windows 上 Node 的 ESM import 须 `file:///`
- **后端工具链** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn`（PATH 是 17） · **`-pl` 必带 `-am`**（否则旧 jar → `NoSuchMethod`）· **但 `spring-boot:run` 反过来不能带 `-am`**（CLI goal 落到父工程、没有 main class）⇒ 先 `-am install -DskipTests` 再 `-pl sts-app/sts-app-server spring-boot:run` · **本地仓 `D:\repository\maven`（不是 `~/.m2`）** · `-Dsurefire.failIfNoSpecifiedTests=false` · `mvn clean` 占用 → `jps -l`+`taskkill`
- **编译期三个静默中止**（症状都是"一大批文件找不到符号"或"运行时才炸"）：**同一元素挂两注解** ⇒ 解析期中止、Lombok 没跑完 ⇒ **clean 重编**；**三目两侧统一类型会拆箱**（`null` → NPE）→ 写 `if`；**MP `eq(条件,列,值)` 的"值"无条件求值** → 先取局部变量再判空
- **前端工具链** stylelint `extends` 须 `require.resolve` 绝对路径 · TS6 起 `baseUrl` 弃用 · ESLint10 flat config **默认导出数组**、只从 cwd 向上找 · typescript-eslint 须显式 `tsconfigRootDir` · `gen:api` 挂的 `prettier --write` **别去掉** · **格式欠账**：`lint-staged` 只处理**本次 `git add` 的文件**（见过 494 行 diff 里 450 行漂移），且**它的 cwd 是 `web/`** ⇒ 仓库根 `docs/`/`server/`/`scripts/` **永不被格式化、"改了也不炸"不必还**
- **真机脚本**（`scripts/verify-*.mjs`，写法细则见 `scripts/README.md` 硬性约束）CDP 求值容忍"那刻在导航"→ 重试 · **收尾两样都要收**：`ws?.close()` + 兜底 `process.exit`（只 `chrome.kill()` ⇒ 结论已打出、进程不结束 = 流水线**超时**）· 浏览器侧报错**分两类**（脚本层任何模式都判失败；网络层在"依赖没起"时属场景本身）· **断言选择器要是"权限产物"、不能是"布局外壳"** · 基座有登录守卫 → **先真实登录** · **⚠️ dev 未设 `strictPort`** ⇒ 残留进程占端口时**静默跳号**、打到旧实例 = 假回归 → 起 dev 前 `netstat` 清残留、起完逐端口 `curl` 验位 · CDP 改输入框用**原型原生 setter + 手动派发 `input`**
- **⚠️ dev server 冷热是两种环境**（0035）`qiankunDevPreamble()` 把 preamble 改成 async IIFE ⇒ 入口与 `/@react-refresh` **抢跑**、冷启动抢输 ⇒ **单跑首帧白屏**（刷新就好）。已修；**改启动路径后 e2e 必须先重启 dev 再跑**，且**"看起来像回归"的先做冷热对照**（只重启一个 dev、其余保持热）

## 3 索引
**ADR** 全在 `docs/decisions/`，逐篇摘要见 `docs/README.md` §四。跨篇要记住的：**0010** 达梦（未用真实实例验证）· **0018** 权限归属 · **0021/0022/0023/0024** RBAC 配置侧 · **0025 + 0030** 数据范围（0030 推翻 0025 第 9 条）· **0026/0031** 鉴权与菜单（`ui:` 是布局不是边界）· **0027/0028** 网关限流 · **0029/0033** 凭据 · **0034** 认证接线 · **0035** preamble · **0036** CI。
**CI**（0036 · `.github/workflows/ci.yml`）三 job：`web`（静态 / 单测 / 构建 / 产物自检）· `server`（`mvn -B -ntp verify`；默认 profile 是 h2 ⇒ **不需要数据库服务**）· `guards`（`verify-nginx-config` + `verify-docs-index`）。**真机层八个浏览器脚本刻意不进必过项** —— 依赖真实浏览器 / GPU / 冷启动 dev server，进去只会得到**随机红**，而随机红比没有 CI 更坏。四个硬点：`pnpm/action-setup` **必须** `package_json_file: web/package.json`（仓库根**没有** `package.json`）且**排在 `setup-node` 之前**（`cache: pnpm` 要求 `pnpm` 已在 PATH）；Node **手写 `22`**（`engines.node` 是**下限**不是 pin）；入口只能 `.github/workflows/`（`infra/ci/` 空占位已删）；**改 workflow 里的命令先在本机跑一遍**（CI 配置本身没有测试）。基线：前端 **549 例** / 后端 **180 例**（174 是旧数）· 产物自检 18 文件零命中。

## 4 结构 / 栈
React19+Vite8+TS**6.0.3**(7.x 不被 typescript-eslint 支持)+Router7；qiankun2；pnpm+Turborepo；Less；ESLint10/Prettier3/Stylelint17/Vitest5。后端 Java21+Boot4.1.1+MP3.5.17；OpenAPI 3.1。
端口：playground 3100 · portal 3000 · log 3200 · event 3300 · environment 3400 · admin 3500 · audit 3600。
规范只在 `common-config` · **各应用 LESS 令牌各自一份、禁跨应用引用**（引了只有 `vite build` 报） · `catalog:` 是版本唯一来源 · **按需再建，不空建空壳** · 配置三层(0003)。

## 5 后端 / 契约 · 跨切面不变量（**只记 ADR 里没有的**）
- 依赖方向 `sts-app→sts-server→sts-components→sts-common→sts-parent`、`sts-app-server` 唯一可启动、**按特性分包**；**业务失败返 HTTP 200**（判 `code===0`）。Boot4 升级 **starter 三处同动**（`web→webmvc`、`boot3-starter→boot4-starter`、springdoc `2.x→3.x`）；MockMvc 引 `spring-boot-starter-webmvc-test`；Jackson3 import `tools.jackson.*`
- **Flyway** 不写 `IF NOT EXISTS`、**已执行脚本不可改**、**版本号全仓唯一**（log V1·V6 / track V2·V7 / auth V3·V5 / audit V4）；切 mysql 须显式引 `flyway-mysql`；达梦 `flyway.enabled=false` 走 `scripts/init-dameng.sh`。MySQL 保留字正解**属性名与列名同构**且不加 `@TableField`；**两库保留字不可互推**；达梦 `VARCHAR(n CHAR)`
- **权限域** `bw_auth_*` 13 表；错误码 5 位 = HTTP 3 位 + 业务 2 位；自研 HS512+PBKDF2、**不引 Spring Security**；`@RequiresPermission`+`AuthInterceptor`（**白名单反着写：无注解即公开**）；`AuthTokenFilter` 不可同时 `@Component` 又进 `FilterRegistrationBean`；`#{it.xxx}` OGNL **编译器管不到**；权限项 admin **45** / auditor **14**
- **三条静默缺陷**（写了但不生效、**且不报错**，要反复问）① **新增影响权限的开关 → 问"判定里读它了吗"**（已抓 5 处：DENY 跨角色 / 停用角色 / 时态 / **停用用户组** / 组链路时态；**判定越严、反查越宽**；**嵌套组判定只跳一层** → `bindMember` 拒绝 `GROUP` 兜住）② **软删+唯一键**（资源/角色/用户组同坑）：唯一键**不含 `deleted`** → 重建同码**撞键** → 正解**复活原记录**并**清空成员与授权**（`40900` 由 `DuplicateKeyException` 处理器填；`spring-tx` 该模块必须 **compile**）③ **`updateById` 的 `NOT_NULL` 清不掉字段** → 用 `update(new Entity(), lambdaUpdate().set(...))` **逐列显式 set**、传**空实体**
- **登记与两份声明**：`AuthPermissions.ALL` 是种子资源行的**全仓唯一来源**（漏登记 = 入口/权限**永不出现**）；`AuthMenus`(后端) 与 `common-api/menus.ts`(前端) 由测试**逐字**钉住 ⇒ **改码必须同改两端**；`ui:` 码是**布局**、读权限是**安全边界**（撤销菜单码**不拦接口**）；**菜单不进 OpenAPI**；**排序四处对齐**
- **前端跨端坑**：权限 store 住 `common-tools/micro`（**不引 React**，三行适配留各应用）；`visibleViews()` 未就绪返回**空列表**（"不确定"≠"都可以看"）；**`OrganizationTreeNode` 与 `OrganizationVO` 不是子集关系** ⇒ 拿树节点填编辑表单会**以空值提交** ⇒ **树给结构、详情给表单初值**（`openEdit` 必须先 `detail(id)`）；「是否主岗」是**三态** `'' | '1' | '0'`；`visibleOrgIds` 的 **`null` 与 `[]` 必须说成两句不同的话**
- **认证接线（0034）** 一律用 `common-api` 的 `createConsoleAuth`，**四个子应用逐字同形**（新子应用照抄 `platform-admin` 的 `src/auth/index.ts` + `src/api.ts`）；端口**构造即就绪** ⇒ **不要**再引入两段式初始化与**延迟创建**的 api 客户端；`bindHost` 必须早于 `connectPermissionSource()`（判据是"**有没有宿主桥**"不是"有没有会话"）；**子应用组件测试**里权限 store **必须自己 connect**（只有 `main.tsx` 会 connect），否则界面全空、与"没权限"同形；`vi.mock('common-api')` 必须 **`importOriginal`**
- **契约生成（0006）** 代码 → springdoc → `scripts/export-openapi.sh`(:18080，**须 `JAVA_HOME=<jdk21>`**) → `docs/api/openapi.json`（**67 接口 / 43 路径 / 100 模型**）→ `openapi-typescript` → `common-api` 的 `schema.d.ts`（入库）。**`@Schema` 的 description 会进 `schema.d.ts`** ⇒ 改它必须重导 + `pnpm --filter common-api gen:api`；`@Operation` 只进 `openapi.json`

## 6 其余分域（**只记陷阱**）
- **三条管道（0004/0011/0019）** 审计**无写接口**、**匿名不记 / 查询不记**、`AUDIT(-100) < AUTHORIZATION(-50)`、失败仍 200 → `ResponseOutcome.markFailure/failureOf`（**新增 handler 必调**）、`AuditRecorder` 用 `REQUIRES_NEW`；**三套标识别混**：资源 `auth:user` / 权限码 `auth:user:create` / 动作 `auth.user.create`；**登录先 `updateContext` 再 `track`、登出反之**
- **口令（0020）** **⚠️ 同事务写失败计数 = 永远不生效** → `LoginAttemptRecorder`(`REQUIRES_NEW`)；**守卫测试不能加 `@Transactional`**；指数退避**在 `double` 里比完再转 `long`**
- **权限归属（0018）** 会话**在 `packages/`、基座只装配** · **基座单点算 → `HostGlobalState` 下发**、子应用**不得自调 `/me`** · **权限不在令牌里** → `restore()` 走刷新、**401 刷新必须单飞** · 子应用**无登录页**、**按钮级只显隐不禁用**（写回 **40301**）
- **微前端（0012）** **应用名是隐式契约** · `sandbox:false` · 子应用 `base` dev `/`、build 带前缀 · **订阅必须用 `onHostGlobalStateChange()`** · **被基座加载时组件级热更新不生效**（已知限制、根因未定位）
- **三维（0013~0015/0017）** **画布与上下文同生共死**（`dispose()` 含 `forceContextLoss()`）· **切场景先释放旧后建新** · **相机跟随整体平移** · **验证按「帧」等不按毫秒**（判据 `diagnostics().frames`）
- **网关（0027/0028）** `envsubst` 必须**白名单形式**（否则吃掉 nginx 自己的 `$remote_addr`）· `limit_req_status`/`limit_conn_status` **默认是 `503` 不是 `429`** · `limit_req_zone`/`map`/`upstream` 只能在 **http 上下文**（落 `conf.d/`）· **全局桶 key 必须是常量**（写成 `$binary_remote_addr` 语法合法但退化成第二个按 IP 桶）· **⚠️ 压限流必须有活的后端**（全 502 时每次连接被拒约 2 秒 → 间隔被拉长到低于阈值、**一个 429 都不出**）· `verify-nginx-config.mjs` 补 `nginx -t` 查不到的那几类

## 7 待办池 / 刻意不做
**待办**：`platform-user` 未创建（本仓无业务数据表，承载什么还没答案 ⇒ **先讨论再动手**）→ **新账号默认没有任何菜单码**（表现为"没有可进入的模块"）→ 自助改密接口（0020 遗留）→ `bw_auth_rel_org_user` 补唯一索引（需新迁移；**堵不住"两个主岗"**）→ `CUSTOM` 的 `filter_json` 解析 → **React 组件级 HMR 在 qiankun 下不生效**（0012 的已知限制；0035 确认**不是** preamble 引起，值得单独一轮）→ **CI 首次真跑还没人看过**（本机跑不了 Actions：无 `gh`、私有仓库未认证 404；最可能红的是 runner toolchain 版本 / Maven 首次下载 / `--frozen-lockfile`）＋ 三个 job 还没设成**分支保护的必需检查**（仓库设置，不在版本库）
**刻意不做（是决策不是待办，别"顺手接上"）**：把 `DEPT` 接进审计可见性（0030 第 10 条）；下沉四个子应用的 `usePermission` 适配层（重复的只是 2-4 行不变的代码；**基座的 `usePermissions()` 不是重复** —— 它读基座自己的会话，分层不同）；把登录页开发提示文案并入产物自检（文案是**会变的值**，写进检查会静默失效）；**真机层进 CI 必过项**（0036 决策 2）
**未覆盖（记录，不催办）**：0028 慢速攻击 / 多实例单机内存 zone / 阈值标定需真实流量 · 0029 密钥级配置外部化 · 0031 新账号的菜单授权体验 · 0036 的 `common-api#build` turbo 输出告警（既有，不影响结果）
