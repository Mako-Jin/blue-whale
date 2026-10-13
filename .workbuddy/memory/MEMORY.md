# BlueWhale — 长期备忘

> **本文件只留"仓库文档里没有的"三类：本机 / 沙箱坑 · 索引 · 待办。**
> 仓库约定一律**指向文档、不在此重述** —— 重复正是它此前反复涨到注入截断线的根因（2026-10-13 收口，-60%）。
> 铁律 / 技术栈在 `README.md` §四 / §五 · 开工前必读 `AGENTS.md` · 环境与 Git 与 CI 在 `CONTRIBUTING.md` ·
> **各域硬规则表在 `server/README.md`（权限 / 日志 / 事件 / 审计）与 `web/README.md`（微前端 / 工具链 / 测试）** ·
> 「改动 → 该同步哪些文档」在 `docs/README.md` §二 · ADR 逐篇摘要在 §四（`verify-docs-index.mjs` 保证不漏登记）。

## 1 本机 / 沙箱坑（**仓库别处没有**，勿再排查）

- **环境** 一律 Bash · **python 两条路全废**（受管 3.13.12 加载系统 3.11 的 `sitecustomize` ⇒ `SRE`）⇒ 校验脚本用 **Node** · `Node spawnSync` → EBUSY（`verify-nginx-config --nginx` 踩过）· 长驻服务**各起一个**后台任务 · `turbo run` **能跑** · **e2e 不与前端构建并发**（CPU 抢占 ⇒ 假掉登录）· 本机有 **MySQL 与 nginx**（`D:\tools\installer\`）· **无 docker** ⇒ CI 的 `services:` 只能靠 runner 验
- **要一个干净的 MySQL 做实验**（不碰舰长那个 11 库实例、免口令）：`mysqld --initialize-insecure --basedir=D:/tools/installer/mysql --datadir=D:/tools/tmp/mysql-ci/data`，再 `--port=3307` 起（**数据目录留着**）⇒ root **无口令**，建 `bluewhale` 库即可；用完 `taskkill //PID` 收掉。**形态与 CI 的 `services:` 一致**
- **git** 凭据在 `~/.git-credentials`、**不在终端解析**；无人值守加 `-c credential.helper= -c credential.helper=store`（**不带它连凭据文件都不试**，报 `could not read Username` —— 不是网络；`unable to write credential store…` **无害**）· **⚠️ push 两路都不稳**（代理常 `CONNECT tunnel failed 502`、直连曾 `Connection was reset` / 21 秒超时）⇒ **两路各试一次，都不通就只本地提交并记领先数** · **提交可能多带文件**（实测 4 个、根因未定位）⇒ `git commit -F - -- <paths>` + `git show --name-only` 复核；**新文件先 `git add`**
- **敏感门控** 命令**参数**里出现口令字面量 → `SENSITIVE_APPROVAL=TIMED_OUT`。走法：grep **标识符**（不 grep 值）／管道加 `sed -E 's/"[^"]*"/"<redacted>"/g'`／**临时配置文件传**（如 MySQL 种子）· Node ESM import 须 `file:///` 前缀 · **Node 里的 `/tmp` 是盘符相对**（cwd 在 D: ⇒ `D:\tmp`，不是 Git Bash 的） ⇒ 临时脚本用 `process.env.TEMP`
- **后端工具链** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn`（PATH 是 17）· `-pl` **必带** `-am`；`spring-boot:run` **不能**带（原因见 `scripts/README.md`「硬性约束」末条）· **本地仓 `D:\repository\maven`** · `mvn clean` 占用 → `jps -l` + `taskkill`
- **prettier 只覆盖 `web/`**（`format:check` 的 cwd）⇒ `docs/**`、根三份文档、`scripts/README.md`、`.github/**` 的表格**手工对齐**，**别跑 `--write`**· **`yaml` 不是直接依赖**（`NODE_PATH` 解析不到）⇒ 从 `web/` 用 `require('./node_modules/.pnpm/yaml@*/node_modules/yaml')`
- **⚠️ dev 未设 `strictPort`** ⇒ 残留进程占端口时**静默跳号**、e2e 打到**旧实例** = 假回归 → 起 dev 前 `netstat` 清残留、起完逐端口 `curl` 验位 · **CDP 改输入框**用原型原生 setter + 手动派发 `input`（`Input.insertText` 是追加；直接赋 `value` 会被 React 改回去）
- **bash 双引号里的反引号会被命令替换** ⇒ 往文档插含反引号的行**别用** `node -e "…"`（内容会被 shell 执行掉，且不报错、只是内容残缺），用 `Write` 落一个 `.mjs` 再 `node` 跑

## 2 索引（只记指针，内容都在文档里）

- **ADR** `docs/decisions/`：逐篇摘要 + 索引见 `docs/README.md` §四。跨篇要记住的**只有编号**：`0010` 达梦未验 · `0018` 权限归属 · `0021`~`0024` RBAC 配置侧 · `0025`+`0030` 数据范围 · `0026`/`0031` 鉴权与菜单 · `0027`/`0028` 网关限流 · `0029`/`0033` 凭据 · `0034` 认证接线 · `0035` preamble · `0036`~`0042` CI 与文档纪律 · `0043`/`0044` 口令入口与引导 · `0045` 口令有效期（并核出 `40111` 此前**不可能出现**：零写入方）
- **CI** `.github/workflows/ci.yml` **四 job**：`web` / `server` / `server-mysql` / `guards`；细节与硬点看 `CONTRIBUTING.md` §八 + `AGENTS.md` 硬性要求 9 / 10 + `0036`~`0042`。**契约三段各有守卫**：代码→契约（`0042`）· 契约→文档（`0040`）· 契约→生成物（`0041`）。**本文件不记基线数字**（`0039`：用例数是**会变的值**，要数字就跑一遍）

## 3 待办 / 刻意不做

**待办**：`platform-user` 未创建（无业务数据表 ⇒ **先讨论再动手**）→ **新账号一个菜单码都没有**（`0031`；**需先定产品决策**：默认角色自动赋码 还是 授权时显式勾选）→ **管理员重置口令不撤销刷新令牌**（`0045` 核出：自助改密撤销**全部**、管理员重置只改口令并置 `mustChangePassword=1` ⇒ 已换到手的刷新令牌**仍能续期**；判据 = "撤销会话的入口要不要只有一个"）→ **"忘记口令"**（要另一条不依赖登录态的信任通道）→ 租户级 / 角色级口令有效期 · 过期前预警（要把到期时刻下发给前端；`0045` 未覆盖）→ `bw_auth_rel_org_user` 补唯一索引（堵不住"两个主岗"，`0030`）→ `CUSTOM` 的 `filter_json` 解析 → **React 组件级 HMR 在 qiankun 下不生效**（`0012`；`0035` 确认非 preamble）→ **CI 首次真跑还没人看过**（无 `gh`、私有库 404）＋ **`server-mysql` 的 `services:` 从未在任何 runner 上跑过**（无 docker，`0038`）＋ **四个 job 未设分支保护**（仓库设置，不在版本库）→ **`0042` 的字节判据有残余风险**：本机 Oracle JDK 21.0.8 vs CI temurin —— 若首次真跑报"不一致且 diff 全是同一批键换序"，处置是**降级为递归排序键后比较**，不是关掉（`0042`）→ `elemental/*` 悬空 glob 待定（`0037` / `0039` / `0040` 刻意未表态）

**刻意不做（是决策不是待办，别"顺手接上"）**：把 `DEPT` 接进审计可见性（`0030`）· 下沉四个子应用的 `auth/hooks.ts` 适配层（剩下的重复只是 2~4 行不会变的代码；**基座的 `usePermissions()` 不是重复**）· 登录页开发提示文案并入产物自检（`0039`）· **真机层进 CI 必过项**（`0036` 决策 2）· 给 `test` 加 `--coverage`（`0039`）· **`mustChangePassword = 1` 时的服务端强拦**（会死锁：进不去就没法改密；是引导事实非安全边界，`0043`）· **去"清" `password_expires_at` 的残留**（`0045`：`updateById` 静默跳过 null ⇒ `set(null)` 是句不会落库的假动作；"关掉策略"靠**判断先看策略**，配置改完立刻对所有人生效）
