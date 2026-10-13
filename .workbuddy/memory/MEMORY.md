# BlueWhale — 长期备忘

> 只留三类：**约定 · 坑 · 索引**。ADR 论证在 `docs/decisions/`、摘要在 `docs/README.md` §四（`verify-docs-index.mjs` 防空漏）、**领域细则在 `AGENTS.md`**（必读，本文件与它重复的部分一律只留指针）、脚本细则在 `scripts/README.md`。

## 1 铁律
`main` @ `github.com/Mako-Jin/blue-whale`。`web/`(pnpm) · `server/`(Maven `com.bluewhale.sts`) · `.github/ docs/ scripts/ infra/`。
六条：① 两端不共用构建 ② 跨端只认 OpenAPI（前端 SDK **禁手写**）③ 共享物只放 `docs/` ④ `packages/*` **不反向依赖** `platform/*` ⑤ `platform-*` 互不引用、复用下沉 `packages/` ⑥ 后端**禁反向依赖与跨层跳跃**。
**改代码必须同提交改文档** · Husky 靠 `core.hooksPath web/.husky` · LF · Conventional Commits · **ADR 不回改旧文**、只在新文档建立指向 · **CI 入口只能 `.github/workflows/`**（平台硬要求）。

## 2 本机 / 沙箱坑（**仓库别处没有**，勿再排查）
- **环境** 一律 Bash · **python 两个都不能用**（受管 3.13.12 会加载系统 3.11 的 `sitecustomize` → `SRE` 错）⇒ 脚本用 Node · `Node spawnSync` → EBUSY（`verify-nginx-config --nginx` 会踩）· 沙箱注入端口 → `--server.port=8080` · `curl --noproxy '*'` · 长驻服务**各起一个**后台任务 · `turbo run` **能跑** · **e2e 不与前端构建并发**（假"掉登录"）· 本机装了 MySQL 与 nginx（都在 `D:\tools\installer\`）
- **git** 凭据在 `~/.git-credentials`、**不在终端解析**；无人值守加 `-c credential.helper= -c credential.helper=store`（**不带它连凭据文件都不试**，报 `could not read Username` —— 不是网络，**别查代理**；`unable to write credential store: Permission denied` **无害**）· **⚠️ push 撞沙箱代理：`CONNECT tunnel failed, 502`** —— 而 `ls-remote` 走同一代理**正常**、`curl --noproxy` 直连 **200** ⇒ **加 `-c http.proxy= -c https.proxy=` 直推即成功** → **但这不是稳定路径**：同日实测直连先成功推过 3 笔、随后**两条全废**（代理 502 / 直连 `Connection was reset`、连 21 秒超时）⇒ **两条各试一次，都不通就只本地提交并记领先数**，别在网络上耗时间· **提交可能多带文件**（实测 4 个，无损但**分组差一拍**；根因未定位）⇒ 用 `git commit -F - -- <paths>` 绕过索引、提交后 `git show --name-only` 复核、**别在工作区堆无关改动时提交**
- **敏感门控** 命令**参数**含口令字面量 → `SENSITIVE_APPROVAL=TIMED_OUT`。走法：grep **标识符**（不 grep 值）／管道加 `sed -E 's/"[^"]*"/"<redacted>"/g'`／脚本"按行号定位、正则吃掉值" · Node 的 ESM import 须 `file:///`
- **后端工具链** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn`（PATH 是 17） · **`-pl` 必带 `-am`**（否则旧 jar → `NoSuchMethod`）· **但 `spring-boot:run` 反过来不能带 `-am`**（CLI goal 落父工程、无 main class）⇒ 先 `-am install -DskipTests` 再 `-pl sts-app/sts-app-server spring-boot:run` · **本地仓 `D:\repository\maven`** · `mvn clean` 占用 → `jps -l`+`taskkill`
- **编译期三个静默中止**（症状 = "一大批文件找不到符号"或"运行时才炸"）：**同一元素挂两注解** ⇒ 解析期中止、Lombok 没跑完 ⇒ **clean 重编**；**三目统一类型会拆箱**（`null` → NPE）→ 写 `if`；**MP `eq(条件,列,值)` 的"值"无条件求值** → 先取局部变量再判空
- **前端工具链** stylelint `extends` 须 `require.resolve` 绝对路径 · TS6 起 `baseUrl` 弃用 · ESLint10 flat config **默认导出数组**、只从 cwd 向上找 · typescript-eslint 须显式 `tsconfigRootDir` · `gen:api` 挂的 `prettier --write` **别去掉** · **格式欠账**：`lint-staged` 只处理**本次 `git add` 的文件**（494 行 diff 里 450 行漂移过），且**cwd 是 `web/`** ⇒ 仓库根 `docs/`/`server/`/`scripts/` **永不被格式化、不必还**
- **真机脚本**（`scripts/verify-*.mjs`）CDP 求值容忍"那刻在导航"→ 重试 · **收尾两样都要收**：`ws?.close()` + 兜底 `process.exit`（只 `chrome.kill()` ⇒ 结论已打出、进程不结束 = 流水线**超时**）· **断言选择器要是"权限产物"、不能是"布局外壳"** · 基座有登录守卫 → **先真实登录** · **⚠️ dev 未设 `strictPort`** ⇒ 残留进程占端口时**静默跳号**、打到旧实例 = 假回归 → 起 dev 前 `netstat` 清残留、起完逐端口 `curl` 验位 · CDP 改输入框用**原型原生 setter + 手动派发 `input`**
- **⚠️ dev 冷热是两种环境**（0035）`qiankunDevPreamble()` 把 preamble 改 async IIFE ⇒ 入口与 `/@react-refresh` **抢跑**、冷启动抢输 ⇒ **单跑首帧白屏**（刷新就好），已修；**改启动路径后 e2e 必须先重启 dev**、**"像回归"的先做冷热对照**

## 3 索引
**ADR** 在 `docs/decisions/`，逐篇摘要见 `docs/README.md` §四。跨篇要记住的：**0003** 配置三层 · **0010** 达梦未验 · **0018** 权限归属 · **0021~0024** RBAC 配置侧 · **0025+0030** 数据范围 · **0026/0031** 鉴权与菜单 · **0027/0028** 网关限流 · **0029/0033** 凭据 · **0034** 认证接线 · **0035** preamble · **0036** CI · **0037** 格式检查。
**CI**（0036/0037 · `.github/workflows/ci.yml`）三 job：`web`（静态 / **格式** / 单测 / 构建 / 产物自检）· `server`（`mvn -B -ntp verify`；默认 profile 是 h2 ⇒ **不需要数据库服务**）· `guards`（nginx + 文档索引两个零前置自检）。**真机层九个浏览器脚本刻意不进必过项**（权威清单 = `scripts/README.md` 的前置条件列；进去只有**随机红**，比没有 CI 更坏）。硬点：`pnpm/action-setup` **必须**写 `package_json_file: web/package.json`（仓库根**没有** `package.json`）且**先于 `setup-node`**；Node **手写 `22`**（`engines.node` 是**下限**）；**改 workflow 里的命令先在本机跑一遍**；**`format:check`（全树）与 `lint-staged`（只看本次暂存、glob 原本没有 `*.html`）必须同步**。基线：前端 **549 例** / 后端 **180 例**。

## 4 结构 / 栈
React19+Vite8+TS**6.0.3**(7.x 不被 typescript-eslint 支持)+Router7；qiankun2；pnpm+Turborepo；Less；ESLint10/Prettier3/Stylelint17/Vitest5。后端 Java21+Boot4.1.1+MP3.5.17；OpenAPI 3.1。端口 **3000 / 3100 / 3200 / 3300 / 3400 / 3500 / 3600**（分别对应 portal / playground / log / event / environment / admin / audit）。
规范只在 `common-config` · **各应用 LESS 令牌各自一份、禁跨应用引用**（引了只有 `vite build` 报） · `catalog:` 是版本唯一来源 · **按需再建，不空建空壳**。

## 5 后端 / 契约 · 只记 `AGENTS.md` 里没有的
- `sts-app-server` 唯一可启动、**按特性分包**、**业务失败返 HTTP 200**（判 `code===0`）；Boot4 升级 **starter 三处同动**（`web→webmvc`、`boot3-starter→boot4-starter`、springdoc `2.x→3.x`）；MockMvc 引 `spring-boot-starter-webmvc-test`。**Flyway / 保留字那五件事见 `AGENTS.md`**
- **权限域** `bw_auth_*` 13 表；错误码 5 位 = HTTP 3 位 + 业务 2 位；自研 HS512+PBKDF2、**不引 Spring Security**；`@RequiresPermission`+`AuthInterceptor`（**白名单反着写：无注解即公开**）；`AuthTokenFilter` 不可同时 `@Component` 又进 `FilterRegistrationBean`；`#{it.xxx}` OGNL **编译器管不到**；权限项 admin **45** / auditor **14**
- **三条静默缺陷**（写了但不生效、**且不报错**，要反复问）① **新增影响权限的开关 → 问"判定里读它了吗"**（已抓 5 处：DENY 跨角色 / 停用角色 / 时态 / **停用用户组** / 组链路时态；**判定越严、反查越宽**；**嵌套组判定只跳一层** → `bindMember` 拒绝 `GROUP` 兜住）② **软删+唯一键**（资源/角色/用户组同坑）：唯一键**不含 `deleted`** → 重建同码**撞键** → 正解**复活原记录**并**清空成员与授权**（`40900` 由 `DuplicateKeyException` 处理器填；`spring-tx` 该模块必须 **compile**）③ **`updateById` 的 `NOT_NULL` 清不掉字段** → 用 `update(new Entity(), lambdaUpdate().set(...))` **逐列显式 set**、传**空实体**
- **登记与两份声明**：`AuthPermissions.ALL` 是种子资源行的**全仓唯一来源**（漏登记 = 入口/权限**永不出现**）；`AuthMenus`(后端) 与 `common-api/menus.ts`(前端) 由测试**逐字**钉住 ⇒ **改码必须同改两端**；`ui:` 码是**布局**、读权限是**安全边界**（撤销菜单码**不拦接口**）；**菜单不进 OpenAPI**；**排序四处对齐**
- **前端跨端坑**：权限 store 住 `common-tools/micro`（**不引 React**）；`visibleViews()` 未就绪返回**空列表**（"不确定"≠"都可以看"）；**`OrganizationTreeNode` 与 `OrganizationVO` 不是子集关系** ⇒ 拿树节点填编辑表单会**以空值提交** ⇒ **树给结构、详情给表单初值**（`openEdit` 必须先 `detail(id)`）；「是否主岗」是**三态** `'' | '1' | '0'`；`visibleOrgIds` 的 **`null` 与 `[]` 必须说成两句不同的话**
- **认证接线（0034）** 一律用 `common-api` 的 `createConsoleAuth`，**四个子应用逐字同形**（新子应用照抄 `platform-admin` 的 `src/auth/index.ts` + `src/api.ts`）；端口**构造即就绪** ⇒ **不要**再引入两段式初始化与**延迟创建**的 api 客户端；`bindHost` 必须早于 `connectPermissionSource()`（判据是"**有没有宿主桥**"）；**子应用组件测试**里权限 store **必须自己 connect**（只有 `main.tsx` 会 connect），否则界面全空、与"没权限"同形；`vi.mock('common-api')` 必须 **`importOriginal`**
- **契约生成（0006）** 导出流程见 `AGENTS.md` 硬性要求 5，此处只记两条：**`@Schema` 的 description 会进 `schema.d.ts`** ⇒ 改它必须重导 + `pnpm --filter common-api gen:api`；`@Operation` 只进 `openapi.json`。当前 **67 接口 / 43 路径 / 100 模型**

## 6 其余分域 · 只记 `AGENTS.md` 里没有的
- **三条管道（0004/0011/0019）**（其余见 `AGENTS.md`「后端写接口与审计」）：`AuditRecorder` 用 `REQUIRES_NEW`；**登录先 `updateContext` 再 `track`、登出反之**
- **口令（0020）** **⚠️ 同事务写失败计数 = 永远不生效** → `LoginAttemptRecorder`(`REQUIRES_NEW`)；**守卫测试不能加 `@Transactional`**；指数退避**在 `double` 里比完再转 `long`**
- **权限归属（0018）** 会话**在 `packages/`、基座只装配** · **基座单点算 → `HostGlobalState` 下发**、子应用**不得自调 `/me`** · **权限不在令牌里** → `restore()` 走刷新、**401 刷新必须单飞** · 子应用**无登录页**、**按钮级只显隐不禁用**（写回 **40301**）
- **三维（0013~0015/0017）**（其余见 `AGENTS.md`）：**验证按「帧」等不按毫秒**（判据 `diagnostics().frames`，无头软渲染单帧约 2 秒）
- **网关（0027/0028）** `envsubst` 必须**白名单形式**（否则吃掉 nginx 自己的 `$remote_addr`）· `limit_req_status`/`limit_conn_status` **默认是 `503` 不是 `429`** · `limit_req_zone`/`map`/`upstream` 只能在 **http 上下文**（落 `conf.d/`）· **全局桶 key 必须是常量**（写成 `$binary_remote_addr` 语法合法但退化成第二个按 IP 桶）· **⚠️ 压限流必须有活的后端**（全 502 时每次连接被拒约 2 秒 → 间隔被拉长到低于阈值、**一个 429 都不出**）

## 7 待办池 / 刻意不做
**待办**：`platform-user` 未创建（无业务数据表，承载什么还没答案 ⇒ **先讨论再动手**）→ **新账号没有任何菜单码**（"没有可进入的模块"）→ 自助改密接口（0020 遗留）→ `bw_auth_rel_org_user` 补唯一索引（**堵不住"两个主岗"**）→ `CUSTOM` 的 `filter_json` 解析 → **React 组件级 HMR 在 qiankun 下不生效**（0035 确认**不是** preamble 引起）→ **CI 首次真跑还没人看过**（本机无 `gh`、私有仓库未认证 404）＋ 三个 job 还没设成**分支保护的必需检查**（仓库设置，不在版本库）→ **本文件与 `AGENTS.md` 重叠仍偏多**（越写越长是它的老毛病，正确的长期归位是把领域不变量搬进 `AGENTS.md`、本文件只留机器/沙箱坑与待办）
**刻意不做（是决策不是待办，别"顺手接上"）**：把 `DEPT` 接进审计可见性（0030）· 下沉四个子应用的 `usePermission` 适配层（重复的只是 2-4 行不变代码；**基座的 `usePermissions()` 不是重复**）· 登录页开发提示文案并入产物自检（文案是**会变的值**）· **真机层进 CI 必过项**（0036 决策 2）
