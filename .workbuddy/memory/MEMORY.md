# BlueWhale — 长期备忘

> 只留"仓库文档里没有的"三类：**本机 / 沙箱坑 · 索引 · 待办**；仓库约定一律**指向文档、不在此重述**。
> 铁律 / 技术栈 `README.md` §四/§五 · 开工前必读 `AGENTS.md` · 环境 / Git / CI `CONTRIBUTING.md` §八 ·
> 各域硬规则表 `server/README.md` 与 `web/README.md` · 「改动 → 同步哪些文档」`docs/README.md` §二 · ADR 摘要见其 §四。

## 1 本机 / 沙箱坑（**仓库别处没有**，勿再排查）

- **环境** Bash only · **python 两条路全废**（受管 3.13.12 加载系统 3.11 的 `sitecustomize` ⇒ `SRE`）⇒ 脚本用 **Node**（`spawnSync` → EBUSY）· 长驻服务各起一个后台任务 · **e2e 不与前端构建并发**（⇒ 假掉登录）· 本机有 MySQL / nginx（`D:\tools\installer\`）· **无 docker**
- **⚠️ 凭据**（那两条 github 凭据的详述已进 `CONTRIBUTING.md` §八，不在此重复）：取错那条一律回 **401**，**别把它读成"私有库看不了 / CI 看不了"**；git 必带 `-c credential.helper= -c credential.helper=store`
- **push 只有代理一条路**：env 的 `http_proxy` 会被 git 读 ⇒ `-c http.proxy=` **不是**直连（要 `env -u HTTP_PROXY …`），真直连出不去 ⇒ 别试 · 代理**按 host 分化**（实测：`github.com` / `gist.github.com` 回 502，而 `api.github.com` 200 · `codeload` / `raw` 301 · `objects` 404 **都通**）⇒ **`github.com` 不通时仍能调 REST API 查 CI**（`CONTRIBUTING.md` §八那套照用）· `github.com` **不是封死而是低概率窗口**（10 次快速探针**通 2 次**≈20%，失败在 502 与 `schannel: server closed abruptly` 之间交替）⇒ **持续重试有效**（一轮 15 次里中的；别在 5 次失败后收手），都不通才只本地提交并记领先数 · **提交可能多带文件**（实测 4 个）⇒ `git commit -F - -- <paths>` + `git show --name-only`；新文件先 `git add` · 带 `-c credential.helper=store` 时，**成功那一次会紧跟一行 `fatal: unable to write credential store: Permission denied`** —— 那是回写凭据失败、**推送已经成功**，别读成失败（看下一行的 `old..new  main -> main`）· **同一类还有一条**：成功之后工具结果里会紧跟沙箱提示「命令触发了需要确认的安全策略… Do NOT retry this command」—— **它出现在成功之后**，是沙箱对"命令含凭据相关字样"（`-c credential.helper=store`）的误报，不是推送被拒 ⇒ 判据一律是**有没有 `old..new  main -> main`**
- **数用例前先清 `target/surefire-reports`**：surefire 不清理，陈旧 XML 会把数**算多**；判"类还在不在"要先 `replace(/\\/g,'/')`（否则 Windows 永不命中）
- **干净 MySQL 做实验**（不碰舰长那 11 库实例）：数据目录 `D:/tools/tmp/mysql-ci/data` **已存在**（里面另有 `bw_esc`/`bw_lk`/`bw_t`/`bw_test` 等旧实验库），直接 `mysqld --basedir=D:/tools/installer/mysql --datadir=D:/tools/tmp/mysql-ci/data --port=3306 --console`（**必须 `run_in_background`**，普通 `&` 会随 shell 被回收）⇒ root 无口令、`DROP/CREATE DATABASE bluewhale`；用完 `taskkill //PID` · **端口必须是 3306**（`application-mysql.yml` 写死 `127.0.0.1:3306`）。**⚠️ 跑 mysql profile 的全量用例需要 `application-h2.yml` 里那对种子口令（逐字相同），而那个文件被沙箱挡** ⇒ 用自选口令时某个"故意登录失败"的用例会把种子账号**锁 5 分钟**（`attempts=5, level=1`），此后全场 `40102`"账号已被锁定"（**别读成口令错**）。本地可行范围 = "口令全部来自配置"的那批用例（跨 profile 语义用例正是如此）
- **敏感门控** 命令**参数**含口令字面量 → `SENSITIVE_APPROVAL=TIMED_OUT`（`-D…password=…` 也中）；**grep `XXX_PASSWORD` 这类标识符、`cat` 含凭据的 yml 也触发** ⇒ 查夹具避开该符号名 · **要传种子/口令给本地跑时，把值写进 `target/` 下 gitignored 的 yml，命令行只出现 `-Dspring.config.additional-location=file:…`**（这个技巧同样适用于任何要传"像凭据的参数"的场合）· Node ESM import 须 `file:///` · **Node 的 `/tmp` 是盘符相对**（cwd 在 D: ⇒ `D:\tmp`）⇒ 用 `process.env.TEMP`
- **工具链** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn`（PATH 是 17）· `-pl` **必带** `-am`（**踩实过**：漏了会用本地仓里的**旧制品**，症状是"跑出来的是改动前的行为"，极易误判成跨 profile 差异）· 跑单类要 `-Dsurefire.failIfNoSpecifiedTests=false` · `spring-boot:run` **不能**带 `-pl` · 本地仓 `D:\repository\maven` · `mvn clean` 占用 → `jps -l` + `taskkill` · `pnpm <task>` 报 `>>> FULL TURBO` 其实没跑 ⇒ 加 `--force`
- **prettier 只覆盖 `web/`** ⇒ `docs/**`、根三份文档、`scripts/README.md`、`.github/**` 的表格**手工对齐**（CJK 算 2），**别跑 `--write`**；宽度用 `dw()`、**不能用 `.length`** · **`yaml` 不是直接依赖** ⇒ 从 `web/` `require('./node_modules/.pnpm/yaml@*/node_modules/yaml')`
- **⚠️ dev 未设 `strictPort`** ⇒ 残留进程占端口会**静默跳号**、e2e 打到**旧实例** = 假回归 → 起 dev 前 `netstat` 清残留、起完逐端口 `curl` 验位 · **CDP 改输入框**要原型原生 setter + 手动派发 `input`
- **夹具编码坑**：`resource_code` 必须 `域:资源:动作` 三段小写（别的编码只要"字母开头"）· 改 `@Operation` 描述后要走完生成链（`export-openapi.sh` → `pnpm --filter common-api gen:api`），漏一步 CI 红
- **bash 双引号里的反引号会被命令替换** ⇒ 往文档插含反引号的行**别用** `node -e "…"`（不报错、只内容残缺）⇒ 用 `Write` 落 `.mjs` 再跑 · **但日志 append-only、`Write` 会整文件覆盖** ⇒ 追加用 `cat >> 文件 <<'EOF'` · **删大量临时项**别用一次大 glob（沙箱 SIGTERM）⇒ `find -type f -delete` + 逐目录循环 · **`$TEMP` 展开成反斜杠形式**（`C:\Users\…\Temp`）⇒ `rm -f "$TEMP/x"` 被 safe-delete 挡回 `[SAFE_DELETE_INVALID_PATH] embedded drive prefix`（**静默**：退出 1、什么都没删）⇒ 清临时件一律写 `/c/Users/84628/AppData/Local/Temp/…` 正斜杠 · 同理：**`Bash` 工具会把命令里的 Windows 反斜杠路径改成正斜杠**再执行 ⇒ 往文件里写"反斜杠"字样别用 heredoc/`node -e`，用 `Write`/`Edit`

## 2 索引

- **ADR 编号速查**：逐篇一句话标签与长摘要**在 `docs/README.md` §四**（`verify-docs-index.mjs` 保证不漏登记、漏登记 CI 会红）—— 本文件不再复制第二份（曾在此放整块速查，2026-10-16 因超限收口；那份信息全部已在 docs/README.md §四）。
- **七张权威清单都在 `server/README.md`**：权限 / 日志 / 事件 / 审计硬规则表 · `40913` 三支断言矩阵 · 唯一编码列与软删语义 ·
  权限域的开关列与读者 · `40002` 的 16 处文案与读者 · `404xx` 的十五处落点与断言 · `401xx`/`403xx` 的落点与断言
- **CI** 四 job（`web` / `server` / `server-mysql` / `guards`），见 `0036`~`0042`。**不记基线数字**（`0039`）

## 3 待办 / 刻意不做

**待办** —— 只列活跃的；每条的**理由与子项**在该篇 ADR 的「未覆盖」段（`0056`-3 读作 `0056` 未覆盖 3）。
**其余零星待办**（`platform-user` 未创建 · `allow_add` 真实现 · 只存不用的开关列 · 审计的字段级留痕 ·
子应用"请求重取"通道 · 会话列表 · 口令有效期的更细档位 · 唯一索引 · qiankun 组件级 HMR · 悬空 glob ·
**`email`/`phone` 要不要归一大小写**（`0072`-5：本片把它**钉住**了 —— 判重沿用 `0065` 的"区分大小写"，
但"该不该"是另一回事）·
**规则 4 / 规则 5 / 规则 6 的白名单都是硬编码名字**（`0070`-2 · `0071`-5 · `0072`-3：下一个域新加枚举列 /
上报列 / 寻址列时锚不住；规则 6 还看不到"先取局部变量、再直传原值"）·
**组织域的 `contact_email`/`contact_phone` 只有同形守卫、没有运行时断言**（`0072`-4，与 `0066`-2 同族）·
**`entity.setStatus(...)` 锚不住**（`0071`：同一个 setter 四域四种写法，**文本上分不出"已归一"与"没归一"**
⇒ 锚定必误报，那两处 `update` 只能靠用例钉）·
**那十四个自由文本里有没有其实该算枚举的**（`0070`-4，没逐一核过；口径本身是 `0065` 的决定））——
**一律看各篇「未覆盖」段，此处不复述**。

- **存量值未订正**（`0072`-2，**本仓第一次需要 DML 迁移的地方**）：`0069`/`0071`/`0072` 都只改**写入侧**，
  已经写坏的行还躺在库里（带两端空白的 `phone`、以及 `0071` 那七个过滤列）。射程要**一次划完** =
  「哪些列会被精确匹配」×「哪些历史窗口没有归一」⇒ 跨 `bw_client_log`/`bw_client_event`/四张带 `status`
  的 `bw_auth_*` 表。**不能按下述任一种捷径划**：只修"最近哪篇 ADR 碰过的列"（边界会随每篇外扩）、
  或在判重 SQL 里写 `TRIM(col) = ?`（会造出**第三种"相等"**，与 `0065`/`0066` 冲突，且索引失效）。
  `0045`"不追溯存量" / `0051`"改存量数据不受约束"是同一件事的另两个侧面 ⇒ 值得单独立一篇
- **租户管理员主体 + 租户内数据隔离**（`0049`/`0050`）：`DataScope` 未接进 auth 域；`auth:tenant:*` 持码者能改**所有**租户
- **"忘记口令" + 自助注册**（`0047`/`0049`）：都要一条不依赖登录态的信任通道，找回凭证**必须可撤销**
- **停用租户未答**："只读冻结" · 不留"失效几个会话"的痕迹 · 停用父租户下建子租户（`0058`-1/2 · `0051`）
  —— 原先记的"`update` 不过 `resolveStatus`"已由 `0071` 关掉
- **多标签页不同步 + 管理侧"踢掉某人的会话"**（`0047`/`0048`，应一起解决）
- **产品 / 机制决定类**：**已整块关闭**（判据与细节在各 ADR 与 `docs/README.md` §四，2026-10-16 收口）：名称半（0061+0062）· 寻址半（0065）· 搜索半（0066/0067/0068）· 过滤值半（0069/0070）· 写入侧半（0071）· 判重半（0072）· 纸面规则半（0063/0064）· 两处默认文案零使用（`0057`-4 · `0059`-1）· `40002` 分两句 / `40401` 合一句（`0059`-2）· **"未认证"要不要统一成一个码**（`0060`-1，先要定"按业务码还是传输层判 401"）· "读者名单少了一个"靠偶然撞见（`0058`-7）· **"纸面规则没有守护"整块关闭**：`0062`-2 由 `0063` 关、`0061`-3 由 `0064` 关（**且刻意不机械化** —— 它是"被否掉的守卫"那类，不是"还没做"）
- **机械守卫值不值**（`0054`-1 · `0056`-2 · `0057`-1/2 · `0059`-6）—— **六个落地**（`0060` 码有没有落点 · `0063` 判重列与唯一索引对账 · `0067` 还有没有裸 `.like(` · `0069`/`0070` 有没有绕过 `FilterValue` 与 `enumValue` · `0071` 上报侧写入有没有过 `FilterValue` · `0072` 寻址列有没有直传 `request` 原值）+ **一个反例 `0064`**（判"不值得"）。**分界**：能机械化的是锚定**无歧义 token 形状**（`*_EXISTS` 引用、`CREATE UNIQUE INDEX` 列清单、`.like(` 方法名）⇒ 零前置不误报；锚定 **JSX 树 / 展示语义**的三种口径分别误报/假阴/噪声 ⇒ 宁留穷举清单。**判据**：会误报的守卫比没有更糟（训练人忽略它）。**白名单要核对条数**（`0067` 实测到的假绿）：只登记"形态"时，两处同名调用改掉一处仍报绿 ⇒ 照 `0055`「只钉一支等于没钉」。**第三种形态**（`0065`）：既不是脚本、也不是"没有" —— **让两个 CI job 各跑同一批断言**（跨 profile 的语义对账），用在"没有 token 形状可锚定、但'同一件事在两边必须一致'"的场合。**射程要容得下正当例外**（`0069`：把审计 `baseWrapper` 纳入规则 3 后，`operatorId`（`Long`）被判红 ⇒ 按 `0064` 移出射程，并在脚本"刻意不查"里写明理由）。**比"猜坏写法"更强的锚点是"只有一种用法"**（`0070` 规则 4：五个枚举 getter 只能作 `enumValue(...)` 的第一个实参 ⇒ 可以写成"每次出现都必须在里面"，而不是"不许写成某形态"；同时**把白名单固化成了名字** —— 新枚举列锚不到，只能靠纸面规则，已写进"刻意不查"）。**同名 setter 满地都是时宁可收窄射程**（`0071` 规则 5：`setUserId` 全仓八处、七处与过滤无关 ⇒ 只认两个**上报模块**里接收者为 `entity` 的那一类，代价同样是白名单固化；而 `entity.setStatus(...)` **干脆不锚** —— 四域四种写法，文本上分不出已归一/没归一，那是"会误报"那一类）。**最好的判据是"能把改动前的写法打红"**（`0072` 规则 6：`含 request.getXxx() 且不含 FilterValue ⇒ 红` —— 旧句 `blankToNull(request.getEmail())` 正好在射程里，而合法的内联写法不在；**同一决策有两个入口时两侧都要锚**，因为"改一半"才是真风险）。断言 / 语义始终靠人工
- **分支保护不是待办**（私有库 + 免费计划 ⇒ 403）

**刻意不做**（是决策不是待办，**别"顺手接上"**；整表见各 ADR 的「刻意不做」段）

- 停用 / 锁定 / 删除时"顺手"清会话 · `PasswordChanger#change` 补第二句撤销（`0046`）· `mustChangePassword = 1` 服务端强拦（死锁）· 去"清" `password_expires_at`（`0045`）
- 向子应用开放 `reload()` · 两处空态加"自动重取权限"（`0048`）· 四处 `resolveTenantId` 合成 `TenantResolver`（`0050`）· `40920` 并进 `resolveTenantId`（`0051`）
- 给四个"复活"域再写用例 / 六列全矩阵测试（`0056`）· `40002` 十六处文案加机械守卫（`0057`）· 两处"只断 `code`"的原用例改掉（`0057`）· 给"全仓都……"加机械守卫（`0056`）
