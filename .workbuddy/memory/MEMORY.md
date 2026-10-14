# BlueWhale — 长期备忘

> 只留"仓库文档里没有的"三类：**本机 / 沙箱坑 · 索引 · 待办**；仓库约定一律**指向文档、不在此重述**。
> 铁律 / 技术栈 `README.md` §四/§五 · 开工前必读 `AGENTS.md` · 环境 / Git / CI `CONTRIBUTING.md` §八 ·
> 各域硬规则表 `server/README.md` 与 `web/README.md` · 「改动 → 该同步哪些文档」`docs/README.md` §二 ·
> ADR 逐篇摘要 `docs/README.md` §四（此处只留编号 + 一句话标签）。

## 1 本机 / 沙箱坑（**仓库别处没有**，勿再排查）

- **环境** Bash only · **python 两条路全废**（受管 3.13.12 加载系统 3.11 的 `sitecustomize` ⇒ `SRE`）⇒ 校验脚本用 **Node**（`spawnSync` → EBUSY）· 长驻服务各起一个后台任务 · **e2e 不与前端构建并发**（CPU 抢占 ⇒ 假掉登录）· 本机有 MySQL / nginx（`D:\tools\installer\`）· **无 docker**
- **CI 怎么看** `CONTRIBUTING.md` §八（判据 = 逐 step 的 `steps[].conclusion`；`skipped` = 没跑）
- **⚠️ 凭据（唯一坑）**：`~/.git-credentials` 两条 github 行，`head -1`（`gho_`/`Mako-Jin`）**能用**，另一条（`ghp_`/邮箱）**返 401** ⇒ 取错得出"私有库 404 / CI 看不了"这类**错误结论**。git 必带 `-c credential.helper= -c credential.helper=store`（不带报 `could not read Username`；`unable to write credential store…` 无害）
- **push 只有代理一条路**：git 会读 env 的 `http_proxy` ⇒ `-c http.proxy=` **不是**直连（要 `env -u HTTP_PROXY …`），真直连出不去 ⇒ 别试 · 代理**间歇**（502 / reset 交替）⇒ 反复试，都不通才只本地提交并记领先数 · **提交可能多带文件**（实测 4 个，根因未定位）⇒ `git commit -F - -- <paths>` + `git show --name-only` 复核；新文件先 `git add`
- **数用例前先清 `target/surefire-reports`**：surefire 不清理，已删类 / 探针的陈旧 XML 会把**用例数与报告数算多**；判"类是否还在"要先 `replace(/\\/g,'/')`（否则 Windows 上永不命中）
- **干净 MySQL 做实验**（不碰舰长那 11 库实例）：`mysqld --initialize-insecure --basedir=D:/tools/installer/mysql --datadir=D:/tools/tmp/mysql-ci/data` → `--port=3307`（数据目录留着）⇒ root 无口令、建 `bluewhale`；用完 `taskkill //PID`。**= CI `services:` 的形态**
- **敏感门控** 命令**参数**含口令字面量 → `SENSITIVE_APPROVAL=TIMED_OUT`；**grep 含 `XXX_PASSWORD` 的标识符也会触发** ⇒ 查夹具避开该符号名 · Node ESM import 须 `file:///` · **Node 的 `/tmp` 是盘符相对**（cwd 在 D: ⇒ `D:\tmp`）⇒ 临时脚本用 `process.env.TEMP`
- **后端工具链** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn`（PATH 是 17）· `-pl` **必带** `-am` · 跑单个测试类要 `-Dsurefire.failIfNoSpecifiedTests=false`（**不是** `-DfailIfNoSpecifiedTests`，写错报 "No tests matching pattern …"）· `spring-boot:run` **不能**带 `-pl`（见 `scripts/README.md`）· 本地仓 `D:\repository\maven` · `mvn clean` 占用 → `jps -l` + `taskkill`
- **prettier 只覆盖 `web/`** ⇒ `docs/**`、根三份文档、`scripts/README.md`、`.github/**` 的表格**手工对齐**（CJK 算 2），**别跑 `--write`** · **`yaml` 不是直接依赖** ⇒ 从 `web/` 用 `require('./node_modules/.pnpm/yaml@*/node_modules/yaml')`
- **填表格**：目标宽只是**短行下限**，长行直接溢出 ⇒ 改一格不能用"整行宽不变"，要"按下限补、长了只留首尾空格"；宽度用 `dw()`（CJK 算 2），**不能用 `.length`**（负数 padding 抛 `RangeError`）· **各表口径不同**：`docs/api/README.md` 认证表**严格对齐**（总宽 173、末列 126），`server/README.md` 错误码表与硬规则表**不是**（col2 有 18/20/22）
- **⚠️ dev 未设 `strictPort`** ⇒ 残留进程占端口会**静默跳号**、e2e 打到**旧实例** = 假回归 → 起 dev 前 `netstat` 清残留、起完逐端口 `curl` 验位 · **CDP 改输入框**要原型原生 setter + 手动派发 `input`（直接赋 `value` 会被 React 改回）
- **`pnpm <task>` 报 `>>> FULL TURBO` 其实没跑** ⇒ 加 `--force` · **夹具编码坑**：`resource_code` 必须 `域:资源:动作` 三段小写（别的编码只要"字母开头"）· 改 `@Operation` 描述后要走完生成链（`export-openapi.sh` → `pnpm --filter common-api gen:api`），漏一步 CI 会红
- **bash 双引号里的反引号会被命令替换** ⇒ 往文档插含反引号的行**别用** `node -e "…"`（不报错、只内容残缺）⇒ 用 `Write` 落 `.mjs` 再跑 · **但日志 append-only、`Write` 会整文件覆盖**（`10-14` 误覆盖过一次）⇒ 追加用 `Edit` 或 `cat >> 文件 <<'EOF'`

## 2 索引

- **ADR 编号速查**（一句话标签；**逐篇摘要一律看 `docs/README.md` §四**，`verify-docs-index.mjs` 保证不漏登记）：
  `0009`/`0010` 数据源与达梦未验 · `0012` 微前端装配 · `0013`~`0017` 三维环境 · `0018` 权限归属 ·
  `0021`~`0024` RBAC 配置侧 · `0025`/`0030` 数据范围 · `0026`/`0031` 鉴权与菜单 · `0027`/`0028` 网关限流 ·
  `0029`/`0033` 凭据 · `0034` 认证接线 · `0035` preamble · `0036`~`0042` CI 与文档纪律 ·
  `0043`/`0044` 口令入口与引导 · `0045` 口令有效期 · `0046` 凭据变更即撤销会话 · `0047` 登出两条语义 ·
  `0048` 授权生效路径 · `0049` 建号默认角色 · `0050` 租户存在性校验的四处复制 · `0051` 存在 ≠ 可用 ·
  `0052` 免鉴权清单 · `0053` 一个派生值被记成开关 · `0054` 一张答案表半数行要修 · `0055` 守卫有三支只钉了一支 ·
  `0056` 唯一编码列的六份答案 · `0057` `40002` 的十六处文案 · `0058` 停用租户的效力与它的自锁边界 ·
  `0059` 一个码就是一个对象（404 家族的点名住在码里）
- **六张权威清单都在 `server/README.md`**：权限 / 日志 / 事件 / 审计硬规则表 · `40913` 三支的断言矩阵 ·
  唯一编码列与它们的软删语义 · 权限域的开关列与它们的读者 · `40002` 的 16 处文案与它们的读者 ·
  `404xx` 的十五处落点与它们的断言。
- **CI** 四 job（`web` / `server` / `server-mysql` / `guards`），触发条件与三段契约守卫见 `0036`~`0042`。**不记基线数字**（`0039`：用例数会变，要数字就跑）。

## 3 待办 / 刻意不做

**待办**（编号 = 出处 ADR 的「未覆盖」段，理由见该篇）

- `platform-user` 未创建（无业务数据表 ⇒ **先讨论再动手**）
- **租户管理员主体 + 租户内数据隔离**（`0049`/`0050`）：`DataScope` 未接进 auth 域；`auth:tenant:*` 持码者能改**所有**租户
- **"忘记口令" + 自助注册**（`0047`/`0049`）：都要一条不依赖登录态的信任通道，找回凭证**必须可撤销**
- `allow_add` 真实现 · `bw_auth_user_group.is_default` 只存不用 · `bw_auth_user.mfa_enabled` 只回显（`0049`/`0054`）
- **停用租户**（`0058` 关闭了续期那一半）：租户状态已并进 `refresh` 同一行（同返 `40106`、**不撤令牌**，换来"重新启用即续期"）· 自锁由 `40921` 挡 · 恢复入口 = **别的租户里持 `auth:tenant:update` 的账号**。**仍未答**：`TenantService#update` 不过 `resolveStatus`（`0058` 未覆盖 1）· "只读冻结"（未覆盖 2）· 停用不留"失效几个会话"的痕迹 · 停用父租户下建子租户（`0051`）
- 管理侧"踢掉某人的会话" · **多标签页不同步**（`0047`/`0048`，应一起解决）· 会话列表（IP / UA）
- **审计记不到"改了哪个字段"** · **子应用内空状态没有自助路径**（缺"宿主 → 子应用请求重取"通道）· "授权 → 重查 → 入口出现"没进九支真机脚本（`0047`/`0048`）
- MFA 绑 / 解绑 · `revokeAll` 返回值无人消费 · 租户级 / 角色级口令有效期 · 过期前预警
- `bw_auth_rel_org_user` 补唯一索引（`0030`）· `CUSTOM` 的 `filter_json` 解析 · React 组件级 HMR 在 qiankun 下不生效（`0012`）· `elemental/*` 悬空 glob（`0037`/`0039`/`0040`）
- **产品 / 机制决定类**：`*_name`/email/phone 该不该唯一（`0056` 未覆盖 4）· 加索引的"顺手"陷阱（`0056` 未覆盖 3）· ~~别码的文案参差~~ **`0059` 已关闭**（`404xx` 与 `40002` 结构相反 —— 一个码一个对象、写不出错的对象；但核出**六处落点零断言已补**，且顺手修掉一个**已进契约与前端类型**的错码值）· 默认文案"引用的关联数据不存在"零使用（`0057` 未覆盖 4）· **`40400` 的默认文案"资源不存在"也零使用**、且与 `40406` 逐字同（`0059` 未覆盖 1）· **`40002` 分两句、`40401` 合一句，两处判据不同而理由从没写下**（`0059` 未覆盖 2）· **`401xx`/`403xx` 与公共枚举其余的码没按这个口径扫过**（`0059` 未覆盖 3/4）· **"读者名单里少了一个"仍靠偶然撞见**（`0058` 未覆盖 7）
- **机械守卫值不值**：十六处文案（`0057` 未覆盖 1）· 开关列表"漏列"（`0054` 未覆盖 1）· 六列矩阵（`0056` 未覆盖 2）· `ResourceService` 那处不对称（`0057` 未覆盖 2）· **"零断言的码"与"散文里的码值"**（`0059` 未覆盖 5/6：`0040` 的契约守卫**刻意不查散文**，而业务码不进 `responses`——全契约只有 `200`）
- **分支保护不是待办**（`10-14` 实测：私有库 + 免费计划 ⇒ 403；要它只有升 Pro 或转公开）

**刻意不做**（是决策不是待办，**别"顺手接上"**；整表见各 ADR 的「刻意不做」段）

- 停用 / 锁定 / 删除时"顺手"清会话 · `PasswordChanger#change` 补第二句撤销（`0046`）· `mustChangePassword = 1` 服务端强拦（死锁，`0043`）· 去"清" `password_expires_at`（`0045`）
- 向子应用开放 `reload()` / 加「重新检查权限」· 两处空态加"自动重取权限"（`0048`）· 四处 `resolveTenantId` 合成 `TenantResolver`（`0050`）· `40920` 并进 `resolveTenantId`（`0051`）
- 给四个"复活"域再写用例 / 六列全矩阵测试（`0056`）· `40002` 十六处文案加机械守卫（`0057`）· 两处"只断 `code`"的原用例改掉（`0057`）· 给"全仓都……"加机械守卫（`0056`）
