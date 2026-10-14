# BlueWhale — 长期备忘

> **本文件只留"仓库文档里没有的"三类：本机 / 沙箱坑 · 索引 · 待办。**
> 仓库约定一律**指向文档、不在此重述** —— 重复正是它此前反复涨到注入截断线的根因（2026-10-13 收口，-60%）。
> 铁律 / 技术栈在 `README.md` §四 / §五 · 开工前必读 `AGENTS.md` · 环境与 Git 与 CI 在 `CONTRIBUTING.md` ·
> **各域硬规则表在 `server/README.md`（权限 / 日志 / 事件 / 审计）与 `web/README.md`（微前端 / 工具链 / 测试）** ·
> 「改动 → 该同步哪些文档」在 `docs/README.md` §二 · ADR 逐篇摘要在 §四（`verify-docs-index.mjs` 保证不漏登记）。

## 1 本机 / 沙箱坑（**仓库别处没有**，勿再排查）

- **环境** 一律 Bash · **python 两条路全废**（受管 3.13.12 加载系统 3.11 的 `sitecustomize` ⇒ `SRE`）⇒ 校验脚本用 **Node** · `Node spawnSync` → EBUSY · 长驻服务**各起一个**后台任务 · `turbo run` **能跑** · **e2e 不与前端构建并发**（CPU 抢占 ⇒ 假掉登录）· 本机有 **MySQL 与 nginx**（`D:\tools\installer\`）· **无 docker**
- **看 CI** 读法已升进团队文档 ⇒ `CONTRIBUTING.md` §八（本机无 `gh` 也能看；**判据是逐 step 的 `steps[].conclusion`，`skipped` 是"没跑"**）。此处不再重述
- **要干净的 MySQL 做实验**（不碰舰长那个 11 库实例）：`mysqld --initialize-insecure --basedir=D:/tools/installer/mysql --datadir=D:/tools/tmp/mysql-ci/data` → `--port=3307` 起（**数据目录留着**）⇒ root 无口令、建 `bluewhale` 库；用完 `taskkill //PID` 收。**= CI `services:` 的形态**
- **git** 凭据同上、**不在终端解析**；无人值守加 `-c credential.helper= -c credential.helper=store`（**不带它连凭据文件都不试**，报 `could not read Username` —— 不是网络；`unable to write credential store…` **无害**）· **⚠️ push 只有代理一条路**：env 里 `http_proxy=https_proxy=http://127.0.0.1:55370`，**git 会读它** ⇒ `-c http.proxy=` **不等于直连**（要 `env -u HTTP_PROXY -u HTTPS_PROXY -u http_proxy -u https_proxy` 才是），而**真直连本机出不去**（`Failed to connect … after 21069 ms`）⇒ **别去试直连**。代理是间歇的（`10-14` 通了 9 笔，随后 502 / `schannel: server closed abruptly` / reset 交替）⇒ **反复试代理即可**，都不通才只本地提交并记领先数 · **提交可能多带文件**（实测 4 个、根因未定位）⇒ `git commit -F - -- <paths>` + `git show --name-only` 复核；**新文件先 `git add`**
- **敏感门控** 命令**参数**里出现口令字面量 → `SENSITIVE_APPROVAL=TIMED_OUT` ⇒ grep **标识符**（不 grep 值）／管道加 `sed -E 's/"[^"]*"/"<redacted>"/g'`／**临时配置文件传** · Node ESM import 须 `file:///` · **Node 里的 `/tmp` 是盘符相对**（cwd 在 D: ⇒ `D:\tmp`）⇒ 临时脚本用 `process.env.TEMP`
- **后端工具链** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn`（PATH 是 17）· `-pl` **必带** `-am`；`spring-boot:run` **不能**带（见 `scripts/README.md`）· 本地仓 `D:\repository\maven` · `mvn clean` 占用 → `jps -l` + `taskkill`
- **prettier 只覆盖 `web/`** ⇒ `docs/**`、根三份文档、`scripts/README.md`、`.github/**` 的表格**手工对齐**（CJK 算 2），**别跑 `--write`** · **`yaml` 不是直接依赖** ⇒ 从 `web/` 用 `require('./node_modules/.pnpm/yaml@*/node_modules/yaml')`
- **⚠️ dev 未设 `strictPort`** ⇒ 残留进程占端口时**静默跳号**、e2e 打到**旧实例** = 假回归 → 起 dev 前 `netstat` 清残留、起完逐端口 `curl` 验位 · **CDP 改输入框**用原型原生 setter + 手动派发 `input`（直接赋 `value` 会被 React 改回去）
- **bash 双引号里的反引号会被命令替换** ⇒ 往文档插含反引号的行**别用** `node -e "…"`（不报错、只是内容残缺），用 `Write` 落 `.mjs` 再 `node` 跑

## 2 索引（只记指针，内容都在文档里）

- **ADR** `docs/decisions/`：摘要 + 索引见 `docs/README.md` §四。跨篇只需记编号：`0010` 达梦未验 · `0018` 权限归属 · `0021`~`0024` RBAC 配置侧 · `0025`+`0030` 数据范围 · `0026`/`0031` 鉴权与菜单 · `0027`/`0028` 网关限流 · `0029`/`0033` 凭据 · `0034` 认证接线 · `0035` preamble · `0036`~`0042` CI 与文档纪律 · `0043`/`0044` 口令入口与引导 · `0045` 口令有效期（核出 `40111` 零写入方）· `0046` 凭据变更即撤销会话（核出"撤销"只写在两个调用方之一）· `0047` 登出两条语义（核出"全撤"是**省略的默认值**、且那条路径**零用例**）
- **CI** `.github/workflows/ci.yml` **四 job**：`web` / `server` / `server-mysql` / `guards`；硬点看 `CONTRIBUTING.md` §八 + `AGENTS.md` 要求 9 / 10 + `0036`~`0042`。**契约三段各有守卫**：代码→契约（`0042`）· 契约→文档（`0040`，**会逐条核对 `docs/api/README.md` 的 4 处计数**：总览句的接口数 / 路径数、汇总表分项之和、每模块行数）· 契约→生成物（`0041`）。**已真跑过**（`10-14` 四 job 全绿，含此前"只能靠 runner 验"的两步）—— 怎么看见 `CONTRIBUTING.md` §八。**不记基线数字**（`0039`：用例数会变，要数字就跑）

## 3 待办 / 刻意不做

**待办**：`platform-user` 未创建（无业务数据表 ⇒ **先讨论再动手**）→ **新账号一个菜单码都没有**（`0031`；**需先定产品决策**：默认角色自动赋码 还是 授权时显式勾选）→ **"忘记口令"**（要另一条不依赖登录态的信任通道；且那条通道**必须让待处理的找回凭证可撤销**，`0047`）→ **管理侧"踢掉某个人的会话"**（要连数据范围一起定，`0047`）→ **多标签页不同步**（`0047`：A 页全撤、B 页要等下一次请求才掉线；在"退出所有设备"语境下比登出更该做）→ 会话列表（"我在哪些设备上登着"，要展示 IP / UA）→ **审计记不到"改了哪个字段"**（`AuditLogEntity` 无此列 ⇒ 换绑邮箱与改昵称同形，`0047`）→ MFA 绑 / 解绑 · `revokeAll` 返回值无人消费 → 租户级 / 角色级口令有效期 · 过期前预警 → `bw_auth_rel_org_user` 补唯一索引（`0030`）→ `CUSTOM` 的 `filter_json` 解析 → **React 组件级 HMR 在 qiankun 下不生效**（`0012`）→ `elemental/*` 悬空 glob（`0037`/`0039`/`0040` 刻意未表态）→ **分支保护不是待办**（`10-14` 实测：私有库 + 免费计划 ⇒ 403；要它只有升 Pro 或转公开）

**刻意不做（是决策不是待办，别"顺手接上"）**：`DEPT` 接进审计可见性（`0030`）· 下沉子应用的 `auth/hooks.ts` 适配层（重复只是 2~4 行；**基座的 `usePermissions()` 不是重复**）· 登录页开发提示并入产物自检（`0039`）· 真机层进 CI 必过项（`0036` 决策 2）· `test` 加 `--coverage`（`0039`）· **`mustChangePassword = 1` 的服务端强拦**（死锁，`0043`）· 去"清" `password_expires_at`（`0045`：`updateById` 跳 null ⇒ 假动作）· 停用 / 锁定 / 删除时"顺手"清会话（`0046`：是**当前状态** ⇒ 靠校验；锁定触发**不需凭据** ⇒ 免费 DoS）· 给 `PasswordChanger#change` 的调用方补第二句撤销（`0046`：会把撤销时刻推后）· `logout` 补回"不传 = 全撤" · 让 `logoutAll` 吞掉失败（静默的安全失败）· 把"撤销他人会话"塞进 `logout-all` · 为一句"已退出 N 台设备"消费 `revokeAll` 的返回值（末四条见 `0047`）
