# BlueWhale — 长期备忘

> 只留"仓库文档里没有的"三类：**本机 / 沙箱坑 · 索引 · 待办**；仓库约定一律**指向文档、不在此重述**。
> 铁律 / 技术栈 `README.md` §四/§五 · 开工前必读 `AGENTS.md` · 环境 / Git / CI `CONTRIBUTING.md` §八 ·
> 各域硬规则表 `server/README.md` 与 `web/README.md` · 「改动 → 同步哪些文档」`docs/README.md` §二 · ADR 摘要见其 §四。

## 1 本机 / 沙箱坑（**仓库别处没有**，勿再排查）

- **环境** Bash only · **python 两条路全废**（受管 3.13.12 加载系统 3.11 的 `sitecustomize` ⇒ `SRE`）⇒ 脚本用 **Node**（`spawnSync` → EBUSY）· 长驻服务各起一个后台任务 · **e2e 不与前端构建并发**（⇒ 假掉登录）· 本机有 MySQL / nginx（`D:\tools\installer\`）· **无 docker**
- **⚠️ 凭据**（那两条 github 凭据的详述已进 `CONTRIBUTING.md` §八，不在此重复）：取错那条一律回 **401**，**别把它读成"私有库看不了 / CI 看不了"**；git 必带 `-c credential.helper= -c credential.helper=store`
- **push 只有代理一条路**：env 的 `http_proxy` 会被 git 读 ⇒ `-c http.proxy=` **不是**直连（要 `env -u HTTP_PROXY …`），真直连出不去 ⇒ 别试 · 代理**间歇**（502 / reset）⇒ 反复试，都不通才只本地提交并记领先数 · **提交可能多带文件**（实测 4 个）⇒ `git commit -F - -- <paths>` + `git show --name-only`；新文件先 `git add`
- **数用例前先清 `target/surefire-reports`**：surefire 不清理，陈旧 XML 会把数**算多**；判"类还在不在"要先 `replace(/\\/g,'/')`（否则 Windows 永不命中）
- **干净 MySQL 做实验**（不碰舰长那 11 库实例）：`mysqld --initialize-insecure --basedir=D:/tools/installer/mysql --datadir=D:/tools/tmp/mysql-ci/data` → `--port=3307`（数据目录留着）⇒ root 无口令、建 `bluewhale`；用完 `taskkill //PID`。**= CI `services:` 的形态**
- **敏感门控** 命令**参数**含口令字面量 → `SENSITIVE_APPROVAL=TIMED_OUT`；**grep `XXX_PASSWORD` 这类标识符也触发** ⇒ 查夹具避开该符号名 · Node ESM import 须 `file:///` · **Node 的 `/tmp` 是盘符相对**（cwd 在 D: ⇒ `D:\tmp`）⇒ 用 `process.env.TEMP`
- **工具链** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn`（PATH 是 17）· `-pl` **必带** `-am` · 跑单类要 `-Dsurefire.failIfNoSpecifiedTests=false` · `spring-boot:run` **不能**带 `-pl` · 本地仓 `D:\repository\maven` · `mvn clean` 占用 → `jps -l` + `taskkill` · `pnpm <task>` 报 `>>> FULL TURBO` 其实没跑 ⇒ 加 `--force`
- **prettier 只覆盖 `web/`** ⇒ `docs/**`、根三份文档、`scripts/README.md`、`.github/**` 的表格**手工对齐**（CJK 算 2），**别跑 `--write`**；宽度用 `dw()`、**不能用 `.length`** · **`yaml` 不是直接依赖** ⇒ 从 `web/` `require('./node_modules/.pnpm/yaml@*/node_modules/yaml')`
- **⚠️ dev 未设 `strictPort`** ⇒ 残留进程占端口会**静默跳号**、e2e 打到**旧实例** = 假回归 → 起 dev 前 `netstat` 清残留、起完逐端口 `curl` 验位 · **CDP 改输入框**要原型原生 setter + 手动派发 `input`
- **夹具编码坑**：`resource_code` 必须 `域:资源:动作` 三段小写（别的编码只要"字母开头"）· 改 `@Operation` 描述后要走完生成链（`export-openapi.sh` → `pnpm --filter common-api gen:api`），漏一步 CI 红
- **bash 双引号里的反引号会被命令替换** ⇒ 往文档插含反引号的行**别用** `node -e "…"`（不报错、只内容残缺）⇒ 用 `Write` 落 `.mjs` 再跑 · **但日志 append-only、`Write` 会整文件覆盖** ⇒ 追加用 `cat >> 文件 <<'EOF'` · **删大量临时项**别用一次大 glob（沙箱 SIGTERM）⇒ `find -type f -delete` + 逐目录循环

## 2 索引

- **ADR 编号速查**（**逐篇一句话标签在 `docs/README.md` §四**，`verify-docs-index.mjs` 保证不漏登记）：
  `0009`/`0010` 数据源与达梦未验 · `0012`~`0017` 微前端 / 三维 · `0018` 权限归属 · `0021`~`0034` RBAC 配置侧 / 数据范围 /
  鉴权与菜单 / 网关限流 / 凭据 / 认证接线 · `0035` preamble · `0036`~`0042` CI 与文档纪律 · `0043`/`0044` 口令入口与引导 ·
  `0045` 口令有效期 · `0046` 凭据变更即撤销会话 · `0047` 登出两条语义 · `0048` 授权生效路径 · `0049` 建号默认角色 ·
  `0050` 租户存在性 · `0051` 存在 ≠ 可用 · `0052` 免鉴权清单 · `0053` 派生值被记成开关 · `0054` 答案表半数行要修 ·
  `0055` 守卫三支只钉一支 · `0056` 唯一编码列六份答案 · `0057` `40002` 十六处文案 · `0058` 停用租户的效力与自锁边界 ·
  `0059` 一个码就是一个对象（404 家族）· `0060` 登记了却没人抛的码（`401xx`/`403xx` 的落点）
- **七张权威清单都在 `server/README.md`**：权限 / 日志 / 事件 / 审计硬规则表 · `40913` 三支断言矩阵 · 唯一编码列与软删语义 ·
  权限域的开关列与读者 · `40002` 的 16 处文案与读者 · `404xx` 的十五处落点与断言 · `401xx`/`403xx` 的落点与断言
- **CI** 四 job（`web` / `server` / `server-mysql` / `guards`），见 `0036`~`0042`。**不记基线数字**（`0039`）

## 3 待办 / 刻意不做

**待办** —— 只列活跃的；每条的**理由与子项**在该篇 ADR 的「未覆盖」段（`0056`-3 读作 `0056` 未覆盖 3）。
**其余零星待办**（`platform-user` 未创建 · `allow_add` 真实现 · 只存不用的开关列 · 审计的字段级留痕 ·
子应用"请求重取"通道 · 会话列表 · 口令有效期的更细档位 · 唯一索引 · qiankun 组件级 HMR · 悬空 glob）——
**一律看各篇「未覆盖」段，此处不复述**。

- **租户管理员主体 + 租户内数据隔离**（`0049`/`0050`）：`DataScope` 未接进 auth 域；`auth:tenant:*` 持码者能改**所有**租户
- **"忘记口令" + 自助注册**（`0047`/`0049`）：都要一条不依赖登录态的信任通道，找回凭证**必须可撤销**
- **停用租户未答**：`update` 不过 `resolveStatus` · "只读冻结" · 不留"失效几个会话"的痕迹 · 停用父租户下建子租户（`0058`-1/2 · `0051`）
- **多标签页不同步 + 管理侧"踢掉某人的会话"**（`0047`/`0048`，应一起解决）
- **产品 / 机制决定类**：名称 / 邮箱 / 手机该不该唯一（`0056`-4；`0060` 已给出它的**具体形状** —— `40907`/`40909`/`40911` 三个码零落点、租户侧是已做的对照）· 加索引的"顺手"陷阱（`0056`-3）· 两处默认文案零使用（`0057`-4 · `0059`-1）· `40002` 分两句 / `40401` 合一句、理由没写下（`0059`-2）· **"未认证"要不要统一成一个码**（`0060`-1：入口 `40100`、内部 `40104`，且前端三处判据不一致 —— 先要定"按业务码还是传输层判 401"）· "读者名单少了一个"靠偶然撞见（`0058`-7）
- **机械守卫值不值**（`0054`-1 · `0056`-2 · `0057`-1/2 · `0059`-6）—— `0059`-5 的一半已由 `0060` 关闭：`scripts/verify-result-codes.mjs` 守"枚举里的码有没有落点"（语法级、不误报；**断言仍靠人工**，那是它刻意不查的）
- **分支保护不是待办**（私有库 + 免费计划 ⇒ 403）

**刻意不做**（是决策不是待办，**别"顺手接上"**；整表见各 ADR 的「刻意不做」段）

- 停用 / 锁定 / 删除时"顺手"清会话 · `PasswordChanger#change` 补第二句撤销（`0046`）· `mustChangePassword = 1` 服务端强拦（死锁）· 去"清" `password_expires_at`（`0045`）
- 向子应用开放 `reload()` · 两处空态加"自动重取权限"（`0048`）· 四处 `resolveTenantId` 合成 `TenantResolver`（`0050`）· `40920` 并进 `resolveTenantId`（`0051`）
- 给四个"复活"域再写用例 / 六列全矩阵测试（`0056`）· `40002` 十六处文案加机械守卫（`0057`）· 两处"只断 `code`"的原用例改掉（`0057`）· 给"全仓都……"加机械守卫（`0056`）
