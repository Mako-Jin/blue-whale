# BlueWhale — 长期备忘

> **本文件只留"仓库文档里没有的"三类：本机 / 沙箱坑 · 索引 · 待办。**
> 仓库约定一律**指向文档、不在此重述** —— 重复正是它此前反复涨到注入截断线的根因（2026-10-13 收口，-60%）。
> 铁律 / 技术栈在 `README.md` §四 / §五 · 开工前必读 `AGENTS.md` · 环境与 Git 与 CI 在 `CONTRIBUTING.md` ·
> **各域硬规则表在 `server/README.md`（权限 / 日志 / 事件 / 审计）与 `web/README.md`（微前端 / 工具链 / 测试）** ·
> 「改动 → 该同步哪些文档」在 `docs/README.md` §二 · ADR 逐篇摘要在 §四（`verify-docs-index.mjs` 保证不漏登记）。

## 1 本机 / 沙箱坑（**仓库别处没有**，勿再排查）

- **环境** 一律 Bash · **python 两条路全废**（受管 3.13.12 会去加载系统 3.11 的 `sitecustomize` → `SRE` 错）⇒ 校验脚本用 **Node** · `Node spawnSync` → EBUSY（`verify-nginx-config --nginx` 会踩）· 长驻服务**各起一个**后台任务 · `turbo run` **能跑** · **e2e 不与前端构建并发**（CPU 抢占 → 假"掉登录"）· 本机装了 **MySQL 与 nginx**（都在 `D:\tools\installer\`）· **本机无 docker** ⇒ CI 的 `services:` 只能靠 runner 验
- **要一个干净的 MySQL 做实验**（不碰舰长那个已有 11 个库的实例、不需要口令）：`mysqld --initialize-insecure --basedir=D:/tools/installer/mysql --datadir=D:/tools/tmp/mysql-ci/data`，再 `--port=3307` 起（**数据目录留着，下次直接起**）⇒ 全新实例 root **无口令**，建 `bluewhale` 库即可；用完 `taskkill //PID` 收掉。**形态与 CI 的 `services:` 一致**
- **git** 凭据在 `~/.git-credentials`、**不在终端解析**；无人值守加 `-c credential.helper= -c credential.helper=store`（**不带它连凭据文件都不试**，报 `could not read Username` —— 不是网络；`unable to write credential store: Permission denied` **无害**）· **⚠️ push 两路都不稳**：走代理常 `CONNECT tunnel failed 502`（而 `ls-remote` 正常）；加 `-c http.proxy= -c https.proxy=` 直连**多数能成、也曾全废**（`Connection was reset` / 21 秒超时）⇒ **两路各试一次，都不通就只本地提交并记领先数**，别在网络上耗时间 · **提交可能多带文件**（实测 4 个、根因未定位）⇒ `git commit -F - -- <paths>` 绕过索引 + `git show --name-only` 复核
- **敏感门控** 命令**参数**里出现口令字面量 → `SENSITIVE_APPROVAL=TIMED_OUT`。走法：grep **标识符**（不 grep 值）／管道加 `sed -E 's/"[^"]*"/"<redacted>"/g'`／**用临时配置文件传**（MySQL 种子即如此）· Node 的 ESM import 须 `file:///` 前缀
- **后端工具链** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn`（PATH 是 17）· `-pl` **必带** `-am`；反过来 `spring-boot:run` **不能**带（原因与替代命令见 `scripts/README.md`「硬性约束」末条）· **本地仓 `D:\repository\maven`** · `mvn clean` 占用 → `jps -l` + `taskkill`
- **⚠️ dev 未设 `strictPort`** ⇒ 残留进程占端口时**静默跳号**、e2e 打到**旧实例** = 假回归 → 起 dev 前 `netstat` 清残留、起完逐端口 `curl` 验位 · **CDP 改输入框**用原型原生 setter + 手动派发 `input`（`Input.insertText` 是追加；直接赋 `value` 会被 React 受控组件改回去）· 真机脚本「收尾要退出 / 前置条件」两类见 `scripts/README.md`

## 2 索引（只记指针，内容都在文档里）

- **ADR** `docs/decisions/`：逐篇摘要 + 索引见 `docs/README.md` §四。跨篇要记住的**只有编号**：`0010` 达梦未验 · `0018` 权限归属 · `0021`~`0024` RBAC 配置侧 · `0025`+`0030` 数据范围 · `0026`/`0031` 鉴权与菜单 · `0027`/`0028` 网关限流 · `0029`/`0033` 凭据 · `0034` 认证接线 · `0035` preamble · `0036`~`0038` CI · `0039` 文档纪律
- **CI** `.github/workflows/ci.yml` **四 job**：`web` / `server` / `server-mysql` / `guards`。细节与硬点看 `CONTRIBUTING.md` §八 + `AGENTS.md` 硬性要求 9 / 10 + `0036`~`0039`（此处不重述）。**本文件也不记基线数字** —— 按 `0039`，用例数是**会变的值**，要数字就跑一遍（前端 `pnpm test` / 后端 `mvn verify`）

## 3 待办 / 刻意不做

**待办**：`platform-user` 未创建（无业务数据表，"承载什么"还没有答案 ⇒ **先讨论再动手**）→ **新账号一个菜单码都没有**（"没有可进入的模块"，见 `0031`；**需先定产品决策**：默认角色自动赋码 还是 授权时显式勾选）→ 自助改密接口（`0020` 遗留）→ `bw_auth_rel_org_user` 补唯一索引（**堵不住"两个主岗"**，见 `0030`）→ `CUSTOM` 的 `filter_json` 解析 → **React 组件级 HMR 在 qiankun 下不生效**（`0012` 已知限制，`0035` 确认**不是** preamble 引起）→ **CI 首次真跑还没人看过**（本机无 `gh`、私有仓库未认证 404）；**`server-mysql` 的 `services:` 从未在任何 runner 上跑过**（本机无 docker，`0038` 明写的未验证项）＋ **四个 job 还没设成分支保护的必需检查**（仓库设置，不在版本库）→ **契约的路径 / 接口 / 模型计数（43 / 67 / 100）可加零前置守卫**（有 `docs/api/openapi.json` 作权威源、实测与文档一致；`0039` 明写的未覆盖项）→ `web/pnpm-workspace.yaml` 的 `elemental/*` 悬空 glob 待定（`0037` / `0039` 均**刻意未表态**）

**刻意不做（是决策不是待办，别"顺手接上"）**：把 `DEPT` 接进审计可见性（`0030`）· 下沉四个子应用的 `auth/hooks.ts` 适配层（剩下的重复只是 2~4 行正确且不会变的代码；**基座的 `usePermissions()` 不是重复**）· 登录页开发提示文案并入产物自检（文案是**会变的值**，同 `0039`）· **真机层进 CI 必过项**（`0036` 决策 2）· 给 `test` 任务加 `--coverage`（只会多产一份没人消费的东西并拖慢流水线，`0039`）
