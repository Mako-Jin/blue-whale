# BlueWhale — 长期备忘

> **本文件只留"仓库文档里没有的"三类：本机 / 沙箱坑 · 索引 · 待办。**
> 仓库约定一律**指向文档、不在此重述** —— 重复正是它此前反复涨到注入截断线的根因（2026-10-13 收口，-60%）。
> 铁律 / 技术栈在 `README.md` §四 / §五 · 开工前必读 `AGENTS.md` · 环境与 Git 与 CI 在 `CONTRIBUTING.md` ·
> **各域硬规则表在 `server/README.md`（权限 / 日志 / 事件 / 审计）与 `web/README.md`（微前端 / 工具链 / 测试）** ·
> 「改动 → 该同步哪些文档」在 `docs/README.md` §二 · ADR 逐篇摘要在 §四（`verify-docs-index.mjs` 保证不漏登记）。

## 1 本机 / 沙箱坑（**仓库别处没有**，勿再排查）

- **环境** 一律 Bash · **python 两条路全废**（受管 3.13.12 会加载系统 3.11 的 `sitecustomize` → `SRE` 错）⇒ 校验脚本用 **Node** · `Node spawnSync` → EBUSY（`verify-nginx-config --nginx` 踩过）· 长驻服务**各起一个**后台任务 · `turbo run` **能跑** · **e2e 不与前端构建并发**（CPU 抢占 → 假"掉登录"）· 本机有 **MySQL 与 nginx**（`D:\tools\installer\`）· **无 docker** ⇒ CI 的 `services:` 只能靠 runner 验
- **要一个干净的 MySQL 做实验**（不碰舰长已有 11 个库的实例、无需口令）：`mysqld --initialize-insecure --basedir=D:/tools/installer/mysql --datadir=D:/tools/tmp/mysql-ci/data`，再 `--port=3307` 起（**数据目录留着，下次直接起**）⇒ root **无口令**，建 `bluewhale` 库即可；用完 `taskkill //PID` 收掉。**形态与 CI 的 `services:` 一致**
- **git** 凭据在 `~/.git-credentials`、**不在终端解析**；无人值守加 `-c credential.helper= -c credential.helper=store`（**不带它连凭据文件都不试**，报 `could not read Username` —— 不是网络；`unable to write credential store: Permission denied` **无害**）· **⚠️ push 两路都不稳**（代理常 `CONNECT tunnel failed 502`、直连曾 `Connection was reset` / 21 秒超时）⇒ **两路各试一次，都不通就只本地提交并记领先数** · **提交可能多带文件**（实测 4 个、根因未定位）⇒ `git commit -F - -- <paths>` + `git show --name-only` 复核；**新文件先 `git add`**
- **敏感门控** 命令**参数**里出现口令字面量 → `SENSITIVE_APPROVAL=TIMED_OUT`。走法：grep **标识符**（不 grep 值）／管道加 `sed -E 's/"[^"]*"/"<redacted>"/g'`／**用临时配置文件传**（MySQL 种子即如此）· Node 的 ESM import 须 `file:///` 前缀 · **Node 里的 `/tmp` 是盘符相对**（cwd 在 D: ⇒ `D:\tmp`），**不是** Git Bash 的 ⇒ 临时脚本用 `process.env.TEMP`
- **后端工具链** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn`（PATH 是 17）· `-pl` **必带** `-am`；`spring-boot:run` **不能**带（原因与替代命令见 `scripts/README.md`「硬性约束」末条）· **本地仓 `D:\repository\maven`** · `mvn clean` 占用 → `jps -l` + `taskkill`
- **prettier 只覆盖 `web/`**（`format:check` 的 cwd）⇒ `docs/**`、根三份文档、`scripts/README.md`、`.github/**` 的表格**手工对齐**，**别跑 `--write`**（18~260 行无色 diff）· **`yaml` 不是直接依赖**（`NODE_PATH` 解析不到）⇒ 从 `web/` 用 `require('./node_modules/.pnpm/yaml@*/node_modules/yaml')`
- **⚠️ dev 未设 `strictPort`** ⇒ 残留进程占端口时**静默跳号**、e2e 打到**旧实例** = 假回归 → 起 dev 前 `netstat` 清残留、起完逐端口 `curl` 验位 · **CDP 改输入框**用原型原生 setter + 手动派发 `input`（`Input.insertText` 是追加；直接赋 `value` 会被 React 改回去）· 真机脚本两类坑见 `scripts/README.md`

## 2 索引（只记指针，内容都在文档里）

- **ADR** `docs/decisions/`：逐篇摘要 + 索引见 `docs/README.md` §四。跨篇要记住的**只有编号**：`0010` 达梦未验 · `0018` 权限归属 · `0021`~`0024` RBAC 配置侧 · `0025`+`0030` 数据范围 · `0026`/`0031` 鉴权与菜单 · `0027`/`0028` 网关限流 · `0029`/`0033` 凭据 · `0034` 认证接线 · `0035` preamble · `0036`~`0041` CI 与文档纪律
- **CI** `.github/workflows/ci.yml` **四 job**：`web` / `server` / `server-mysql` / `guards`；细节与硬点看 `CONTRIBUTING.md` §八 + `AGENTS.md` 硬性要求 9 / 10 + `0036`~`0041`。**本文件不记基线数字**（`0039`：用例数是**会变的值**，要数字就跑一遍）

## 3 待办 / 刻意不做

**待办**：`platform-user` 未创建（无业务数据表 ⇒ **先讨论再动手**）→ **新账号一个菜单码都没有**（`0031`；**需先定产品决策**：默认角色自动赋码 还是 授权时显式勾选）→ 自助改密接口（`0020` 遗留）→ `bw_auth_rel_org_user` 补唯一索引（堵不住"两个主岗"，`0030`）→ `CUSTOM` 的 `filter_json` 解析 → **React 组件级 HMR 在 qiankun 下不生效**（`0012`，`0035` 确认不是 preamble 引起）→ **CI 首次真跑还没人看过**（无 `gh`、私有库未认证 404）＋ **`server-mysql` 的 `services:` 从未在任何 runner 上跑过**（无 docker，`0038` 未验证项）＋ **四个 job 未设分支保护**（仓库设置，不在版本库）→ **契约与代码是否一致仍没有守卫**（`0040` 未覆盖第 1 条；"生成物 ↔ 契约"那半已由 `0041` 收尾）→ `elemental/*` 悬空 glob 待定（`0037` / `0039` / `0040` 刻意未表态）

**刻意不做（是决策不是待办，别"顺手接上"）**：把 `DEPT` 接进审计可见性（`0030`）· 下沉四个子应用的 `auth/hooks.ts` 适配层（剩下的重复只是 2~4 行不会变的代码；**基座的 `usePermissions()` 不是重复**）· 登录页开发提示文案并入产物自检（文案是**会变的值**，`0039`）· **真机层进 CI 必过项**（`0036` 决策 2）· 给 `test` 加 `--coverage`（`0039`）
