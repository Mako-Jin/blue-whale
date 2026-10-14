# 贡献与开发约定

面向所有参与 BlueWhale 的人（含 AI 助手）。

---

## 一、环境要求

| 工具    | 版本                                    | 用途                                                  |
| ------- | --------------------------------------- | ----------------------------------------------------- |
| Node.js | >= 20                                   | 前端工作区                                            |
| pnpm    | 10.x（`packageManager` 已锁定 10.33.0） | 前端包管理                                            |
| JDK     | **21+**                                 | 后端工作区（enforcer 在构建期强制，低于 21 直接失败） |
| Maven   | 3.6.3+                                  | 后端构建（enforcer 同样检查；已在 3.8.1 验证）        |
| Git     | 2.40+                                   | 版本控制                                              |

---

## 二、首次搭建

```bash
# 前端
cd web && pnpm install          # 会自动执行 prepare 钩子挂载 Git 钩子

# 后端
cd server && mvn clean install -DskipTests
```

### Git 钩子

钩子脚本位于 `web/.husky/`，通过 Git 原生 `core.hooksPath` 挂载（**不是** `.git/hooks/`）。

`web/package.json` 的 `prepare` 脚本会自动执行 `husky` 完成挂载。若钩子未生效，手动挂载：

```bash
# 在仓库根执行
git config core.hooksPath web/.husky
```

| 钩子         | 作用                                                                          |
| ------------ | ----------------------------------------------------------------------------- |
| `pre-commit` | 对**暂存文件**跑 lint-staged：ESLint --fix、Prettier --write、Stylelint --fix（`*.html` 也由 Prettier 处理） |
| `commit-msg` | 校验提交信息符合 Conventional Commits，不合规直接拦截                         |

---

## 三、提交规范

格式：

```
<type>(<scope>): <subject>
```

- `type` 取值：`feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- 标题上限 100 字符

```bash
feat(portal): 新增子应用挂载容器
fix(portal): 修复路由前缀匹配错误
docs(decisions): 补充 ADR-0001
```

---

## 四、文档同步（硬性要求）

**改代码必须同步改文档，且在同一次提交里完成。**

对照表见 `docs/README.md` 第二节——那里列了每类改动需要同步哪些文档。

---

## 五、换行符与二进制文件

仓库统一以 **LF** 存储与检出，由根目录 `.gitattributes` 强制（Windows 专有脚本如 `.bat`/`.ps1` 保留 CRLF）。

**二进制文件必须显式标 `binary`。** `.gitattributes` 里的 `* text=auto` 会让 git 去猜
"这是不是文本"，猜错时**在检出/提交那一刻改写它的换行符** —— 对 `.blend` / `.glb`
这类模型文件，一次改写就是文件损坏（Blender 直接打不开，且没有任何报错提示）。
仓库已给 `*.blend`、`*.blend1`、`*.glb`、`*.gltf`、`*.fbx`、`*.obj` 加了 `binary` 标记，
**新增其他二进制格式时记得往 `.gitattributes` 里补一行**。

Windows 用户首次 clone 后若看到全文件差异，执行一次规范化即可：

```bash
git add --renormalize .
```

> 为什么强制 LF：husky 的 shell 钩子一旦变成 CRLF，在 Linux/macOS 上会因 `\r` 直接执行失败。

---

## 六、依赖治理

### 公共依赖的版本

第三方依赖的版本**只写一处**——`web/pnpm-workspace.yaml` 的 `catalog:` 段。`package.json` 里出现具体版本号即视为违规：

```json
{ "devDependencies": { "typescript": "catalog:" } }
```

新增依赖的正确流程：

```bash
# 1. 在 web/pnpm-workspace.yaml 的 catalog 里加一行
# 2. 在用到它的 package.json 里写 "catalog:"
cd web && pnpm install
```

> 为什么不是"再建一个包放依赖"：那会产生**幽灵依赖**——某个库能被引用到，却没出现在引用方的 `package.json` 里，运行时解析不到、IDE 也提示不出来。catalog 只统一版本号，依赖归属关系仍留在各自的 `package.json`。详见 `docs/decisions/0002-前端公共包与公共依赖治理.md`。

### 后端依赖的版本

后端是 Maven，没有 catalog，但规矩同样只有一条：**版本只写一处 —— `server/sts-parent/pom.xml`**。

- 子模块的 `<dependency>` **一律不写 `<version>`**
- Boot 已经纳管的（MySQL 驱动、Flyway、Jackson…）交给 `spring-boot-starter-parent` 的 BOM
- Boot **没有**纳管的（MyBatis-Plus、springdoc、达梦驱动）在 `sts-parent` 的 `<properties>` + `dependencyManagement` 里各声明一次

### 公共代码

自研公共代码放 `web/packages/common-<能力>/`。建包前先问一句：**这份代码是不是只有一个应用会用？** 是则不建包。

当前已有的公共包：

| 包              | 内容                                                                                                                                                      |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `common-config` | 共享 ESLint / Stylelint / TypeScript 配置，以及跨应用共享的 Vite 插件片段（`common-config/vite`）                                                         |
| `common-tools`  | 日志（`logger`）· 环境探测（`env`）· 请求内核（`http`）· JSON 安全层（`json`）· 批量上报通道（`report`）· 行为事件内核（`events`）· 微前端桥接（`micro`） |
| `common-api`    | 契约包：OpenAPI 生成的类型 + 接口装配                                                                                                                     |

> `common-tools` 与框架无关（不依赖 React），应用、构建脚本、Node 工具链都能直接用。本地演示：`pnpm --filter common-tools playground`（logger）、`pnpm --filter platform-log-console dev`（日志上报全链路，:3200）、`pnpm --filter platform-event-console dev`（行为事件全链路，:3300）、`pnpm --filter platform-environment dev`（三维环境，:3400）、`pnpm --filter platform-admin dev`（按钮级权限，:3500）、`pnpm --filter platform-audit-console dev`（审计管道，:3600）。
>
> **`common-tools/micro` 为什么在公共包里**：它是子应用侧的代码，但所有子应用都要用，而**子应用之间禁止互相引用**（铁律 5），复用内容只能下沉到 `packages/`。它按结构化描述实现、不引入 qiankun 类型，因此不破坏本包"不依赖任何运行时第三方库"的约束。

### 微前端相关的改动

微前端跨越多个应用（基座 + 五个子应用），改动时最容易漏掉配合项：

| 我想改…                         | 必须同时确认                                                                                                                     |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| 子应用的**应用名**              | 基座 `src/micro/apps.ts` 的 `name` 逐字一致（不一致不报错，只会卡在"加载中"）                                                    |
| 子应用的**端口**                | 基座 `apps.ts` 的 `devPort`、`vite.config.ts` 的 `server.port` 两处一致                                                          |
| 子应用的**路由前缀**            | 基座 `apps.ts` 的 `routePath` 与子应用 `base`（仅 build）一致                                                                    |
| 主应用的**挂载方式 / 沙箱策略** | `docs/decisions/0012` 第 1、2 条要同步更新（这是决策，不是实现细节）                                                             |
| 跨应用共享的 **Vite 片段**      | `packages/common-config/README.md` · `docs/decisions/0012` 第 10 条                                                              |
| 子应用里的**全局状态订阅**      | 一律用 `common-tools/micro` 的 `onHostGlobalStateChange()`，不要直接调 `props.onGlobalStateChange`（qiankun 单槽位，会互相覆盖） |

改完跑一次端到端验证（各服务都在跑时）：

```bash
node scripts/verify-micro-frontend.mjs    # 微前端全链路（基座 :3000 + 需调试的子应用）
node scripts/verify-scene-switching.mjs   # 三维应用多场景切换与显存释放（仅需 :3400）
node scripts/verify-player-controls.mjs   # 主人公操控链路（仅需 :3400）
```

> 需要**登录态**的三种验证（都要后端 :8080 与基座 :3000）：
> `node scripts/verify-portal-auth.mjs`（登录链路，另需 :3200）、
> `node scripts/verify-admin-permissions.mjs`（按钮级权限，另需 :3500）、
> `node scripts/verify-audit-pipeline.mjs`（审计管道，另需 :3600）。

### 三维场景相关的改动

`platform-environment` 的场景实现与元数据分离：**实现**（three.js 代码）只能在前端，**元数据**（`SceneMeta`）才是"放前端还是后端"的对象。当前用**前端注册表**，依据见 `docs/decisions/0014`。

| 我想改…                              | 必须同时确认                                                                                                         |
| ------------------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| **新增场景**                         | 顺序：`scenes/<id>.ts` 实现 → `types.ts` 加 `SceneId` 字面量 → `catalog.ts` 注册一行（顺序错是编译期报错，不是白屏） |
| 场景的**清单来源 / 覆盖**            | 三条护栏见 `docs/decisions/0014`：调用方只认 `loadSceneCatalog()`、远端只能覆盖不能发明、`SceneId` 与实现一一对应    |
| **渲染生命周期**（画布/上下文/释放） | `docs/decisions/0013` 第 1–4 条要同步更新（画布与上下文同生共死、`dispose()` 必 `forceContextLoss()`）               |
| 场景里的**散布 / 随机**              | 一律用 `common.ts` 的 `createPrng(seed)`，禁用 `Math.random()`（可复现、可断言）                                     |
| **切场景管线**                       | `stage.loadScene()` 只换场景图，渲染器/画布/相机/环境贴图复用；切场景要重置渲染选项为该场景默认值                    |
| **新增可操控场景**（主人公）         | 契约见 `docs/decisions/0015`：声明可行走范围与障碍（**一份**，同时喂移动内核与小地图）+ 实现 `createControls`        |
| **移动手感**（速度/转身/障碍推出）   | 只改 `scene/player/motor.ts`（纯内核，不碰 three/DOM）；`tests/player-motor.test.ts` 是行为契约                      |
| **相机跟随 / 右键 / 滚轮分工**       | 左键旋转与滚轮归 OrbitControls，右键在有控制器的场景让给设终点；跟随必须"整体平移"（`stage.ts` 的 `syncFocus`）      |
| **小地图**                           | 自绘 canvas，**不走 React 状态**；地物与碰撞矩形同源；投影与几何在 `scene/minimap.ts`（纯函数，可单测）              |

---

## 七、接口契约的更新流程（改后端接口必走）

```bash
cd server && mvn -DskipTests install     # 1. 构建后端
cd .. && bash scripts/export-openapi.sh  # 2. 从运行中的应用导出契约（默认端口 18080）
cd web && pnpm --filter common-api gen:api   # 3. 重新生成前端类型
```

三步的产物（`docs/api/openapi.json` 与 `common-api/src/generated/schema.d.ts`）都入库，且**必须与接口代码同一次提交**。

- 契约由代码生成，**不要手改** `docs/api/openapi.json` 或 `generated/` 下的任何文件；
- 唯一允许人工介入类型的位置是 `common-api/src/types.ts` 里的**字段替换工具**（`WithJsonPayload` / `WithJsonProps`，生成器表达不了"任意值"）。`JsonValue` 等类型本体与 `sanitize` 函数住在 `common-tools/json`，本包只做转出。细则见 `docs/decisions/0006`；
- **新增带"任意 JSON 字段"的域时，三处都要收紧**：类型本体、分页结果的 `records` 元素、请求体的数组元素。只改第一处会被类型系统放过，然后在跨域取值时以 `unknown` 的形态暴露出来；
- 前端调用接口一律走 `common-api`，**禁止**直接 `fetch`。
- **第 2、3 步的产物是否跟上了它的上游，都由 CI 逐字节核对** ——
  契约有没有跟上代码（重跑一遍导出、与入库的比，见 `docs/decisions/0042`）·
  生成物有没有跟上契约（重跑一遍生成器、与入库的比，见 `docs/decisions/0041`）。
  漏跑 `export-openapi.sh` 或 `gen:api` **都会红**，不必靠人记得。

---

## 八、常用命令

### 前端

```bash
cd web

pnpm install          # 安装依赖
pnpm dev              # 启动全部应用（turbo 聚合日志）
pnpm build            # 按依赖顺序构建
pnpm lint             # ESLint
pnpm stylelint        # 样式规范
pnpm typecheck        # 类型检查
pnpm test             # 单元测试
pnpm format           # Prettier 格式化

# 调试微前端：基座 + 五个子应用是六个独立 dev server，按需同时启动
pnpm dev --filter platform-portal --filter platform-log-console --filter platform-event-console --filter platform-environment --filter platform-admin --filter platform-audit-console
```

> 微前端端到端验证：各服务都起来后执行 `node scripts/verify-micro-frontend.mjs`（用真实 Chrome 走完整链路，见 `scripts/README.md`）。
> 三维应用多场景验证：`:3400` 起来后执行 `node scripts/verify-scene-switching.mjs`（断言反复切场景不耗尽显存）。
> 登录态与按钮级权限验证：`node scripts/verify-portal-auth.mjs`、`node scripts/verify-admin-permissions.mjs`（需 :3000 / :3200 / :3500 与后端 :8080）。
> 审计管道验证：`node scripts/verify-audit-pipeline.mjs`（需 :3000 / :3600 与后端 :8080）。

### 后端

```bash
cd server

mvn clean install -DskipTests                # 构建全部模块

# 启动（默认 8080）—— 默认 profile 是 h2 内存库，零外部依赖
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar

# 连本机 MySQL（数据落磁盘，重启后仍在）。库要先建好，见 server/README.md 第六节
BW_DB_PASSWORD=<密码> java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=mysql

# 连达梦 DM8（国产库）。表结构不由应用创建，需先执行交付脚本
BW_DB_USERNAME=<用户> BW_DB_PASSWORD=<密码> java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=dameng
```

> **数据源由 profile 决定，凭据走环境变量**，两者都不要写进文件 ——
> 用哪个库应当是启动参数（显式可见），密码进版本库则会永久留在 git 历史里。
> 详见 `docs/decisions/0009`（H2 / MySQL）与 `docs/decisions/0010`（达梦）。

> **Maven 实际使用的 JDK 必须是 21+**，低于 21 时构建会在 enforcer 阶段直接失败并给出中文提示
> （不再依赖编译器那句含混的 `release version 21 not supported`）。用 `mvn -v` 可查看当前指向。
> 注意：enforcer 只保护 `mvn` 构建；直接运行 `java -jar` 或 `scripts/export-openapi.sh` 时
> 版本检查由脚本自查，`JAVA_HOME` 指向旧版本仍会失败：
>
> ```bash
> JAVA_HOME=/path/to/jdk-21 mvn clean install
> ```

---

### CI（`.github/workflows/ci.yml`）

流水线分四个互不阻塞的 job，各自的失败就代表各自的层：

| job | 内容 |
| --- | --- |
| `web` | 装依赖 → **契约生成物同步**（重跑 `gen:api`，要求产物与入库版本逐字节相同，`decisions/0041`）→ ESLint → Stylelint → **格式检查** → 类型检查 → 单测 → 构建 → **产物自检**（种子口令不得进包） |
| `server` | `mvn -B -ntp verify`（构建 + 测试，含 enforcer 的 JDK 门禁；**不需要数据库服务**，默认 profile 是 h2 内存库）→ **契约与代码同步**（复用上一步产出的 jar 起一次应用重新导出 `openapi.json`，要求与入库版本逐字节相同，`decisions/0042`） |
| `server-mysql` | 同一条命令 + `-Dspring.profiles.active=mysql`，**对真实 MySQL**（`services: mysql:8.0`）跑全量用例；空库从零迁移一遍。带一条**防假绿**自检（查日志里的 `Database: jdbc:mysql` 等实证） |
| `guards` | 网关配置结构自检 · 文档索引一致性 · 契约清单一致性（**都不需要前置服务**） |

在本机等价地跑一遍：

```bash
# 前端
cd web
pnpm install --frozen-lockfile && pnpm lint && pnpm stylelint && pnpm format:check && pnpm typecheck && pnpm test && pnpm build

# 契约生成物与契约是否同步（CI 的 web job 查这一步，见 docs/decisions/0041；cwd 仍在 web/）
pnpm --filter common-api gen:api && git status --porcelain -- packages/common-api/src/generated

# 产物自检（脚本自己算仓库根，与 cwd 无关）
node ../scripts/verify-bundle-secrets.mjs

# 后端（默认 profile 就是 h2 内存库，不需要先装数据库）
cd ../server && mvn -B -ntp verify

# 契约与代码是否同步（CI 的 server job 查这一步，见 docs/decisions/0042）。
# 复用上一步产出的 jar：起一次应用（h2、18080 端口）重新导出，再要求与入库版本一致。
# 需要 JDK 21（脚本自查 JAVA_HOME）—— 见上面那段"enforcer 只保护 mvn 构建"。
cd .. && bash scripts/export-openapi.sh && git status --porcelain -- docs/api/openapi.json

# 后端 · 真实 MySQL：先起一个库（数据源地址写死在 application-mysql.yml = 127.0.0.1:3306，
# root + 空口令），建好 bluewhale 库，再跑同一条命令 + profile。
# 种子开关必须显式打开并给口令（种子口令没有默认值，见 docs/decisions/0029），
# 口令与 application-h2.yml 里的是同一对。
mvn -B -ntp verify -Dspring.profiles.active=mysql \
  -Dbluewhale.auth.seed-admin.enabled=true -Dbluewhale.auth.seed-admin.password=<同 h2 profile> \
  -Dbluewhale.auth.seed-auditor.enabled=true -Dbluewhale.auth.seed-auditor.password=<同 h2 profile>

# 仓库自检
cd .. && node scripts/verify-nginx-config.mjs && node scripts/verify-docs-index.mjs && node scripts/verify-api-contract.mjs
```

> **后端为什么有两个 job**：h2 是"clone 下来就能跑"的默认，**但它是开发便利、不是交付形态**。
> 只跑 h2 时，"全套用例在真实库上还成立吗"完全没有机器保证 —— `0038` 第一次真跑就抓到 5 处
> 只会在换库时现形的缺陷（其中 4 处的根因是 MySQL 默认的 `REPEATABLE_READ` 快照与
> "审计走独立事务"撞车）。达梦仍未进 CI（真实实例不可得）。

> **真机层（九个 `scripts/verify-*.mjs` 浏览器脚本，清单见 `scripts/README.md` 的「前置条件」列）刻意不在流水线里。**
> 它们依赖真实浏览器、GPU、冷启动的 dev server 与端口占用，放进必过项只会得到**随机红**，
> 而随机红的流水线比没有流水线更坏 —— 红久了就没人看了。理由见 `docs/decisions/0036`。
>
> **为什么格式检查在流水线里、而提交钩子里也有一份。** 两者查的不是同一件事：钩子只看
> **本次 `git add` 的文件**（没被碰过的文件会一直保持旧格式），且它的 glob 里原本没有 `*.html`。
> 补这个盲区的是 `pnpm format:check`（全树）。见 `docs/decisions/0037`。
>
> **改 workflow 里的命令时，先把那条命令在本机跑一遍再推。** CI 配置本身没有测试，
> 它唯一的验证机会就是下一次推送。

> **动 workflow 时的两个实现硬点**（都在 `pnpm/action-setup`）：① 必须显式写
> `package_json_file: web/package.json` —— 它默认读**仓库根**的 `package.json`，而本仓根目录
> **没有**（前端工作区在 `web/`）；② 它**必须排在 `actions/setup-node` 之前** —— 后者的
> `cache: pnpm` 要求 `pnpm` 已在 `PATH` 上。两条踩错都**不是**"配置报错"，而是 **job 起不来**。

> **怎么看 CI 跑成什么样了（不必装 `gh`）。** 网页 Actions 页最省事；要脚本化、或想把某次运行的
> 逐步结论一次看全，就用仓库凭据里的 token 调 REST API。**token 不要出现在命令行参数上**
> （会进 shell 历史、也会进进程表），写进临时配置文件让 `curl -K` 读：
>
> ```bash
> # 取出 token 并只写进临时文件（不回显）。用的是别的凭据管理器就从那里取。
> sed -n 's#.*://[^:@/]*:\([^@]*\)@.*#\1#p' ~/.git-credentials | head -1 > "$TEMP/bw.token"
> printf 'header = "Authorization: Bearer %s"\n' "$(cat "$TEMP/bw.token")" > "$TEMP/bw-curl.conf"
>
> # 最近的运行
> curl -s -K "$TEMP/bw-curl.conf" \
>   "https://api.github.com/repos/Mako-Jin/blue-whale/actions/runs?per_page=5"
>
> # 某次运行里每个 job、每个 step 的结论（{run_id} 换成上面输出里的 id）
> curl -s -K "$TEMP/bw-curl.conf" \
>   "https://api.github.com/repos/Mako-Jin/blue-whale/actions/runs/{run_id}/jobs" \
>   | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{for(const j of JSON.parse(s).jobs){console.log(j.name,j.conclusion);for(const t of j.steps)console.log('   ',t.conclusion.padEnd(8),t.name)}})"
> ```
>
> **凭据文件里 github 那一条可能不止一行 —— 取错那条的报错是 `Bad credentials`，
> 而它极易被误读成"这个库我看不了"。** 本机实测 `~/.git-credentials` 有**两行** github 凭据
> （前缀一个 `gho_`、一个 `ghp_`，用户不同；后者调 REST 一律回 **401**）。
> 上面那句 `head -1` 取的是**第一条**；`sed -n …p` 单看会打印**全部**匹配行 ——
> 于是临时 token 文件里有两行，拼出来的 `Authorization` 头是坏的。
> **REST 回 401 时，先核凭据取到的是哪一条**：`git ls-remote` / `push` 会按 host
> **逐条试**（所以它们成功并不证明你手上那条能用），REST 只用你给的那一个。
>
> **判据在 `steps[].conclusion` 上，不在 job 的颜色上。** 每个 step 各有一份结论：`success`
> 才是跑了且过了，`skipped` 是**没跑**。一个 job 整体是绿的、其中某一步被 `if:` 跳掉时，
> 从 job 上看与"这步通过"**完全一样** —— 所以看 CI 要逐 step 看，尤其那三步契约守卫
> （`0040`~`0042`）与 `server-mysql` 的防假绿自检：它们"没跑"和"通过"是两件不同的事。
> 取整段日志用 `…/jobs/{job_id}/logs`，**必须带 `-L`**（它会 302 到另一个域）。
>
> **四个 job 目前不是 required checks。** 私有库 + 免费计划下，分支保护与 rulesets 两个 API
> 都回 **403**（换成 `admin` 的 token 仍是 403 ⇒ 不是权限问题，是套餐限制）；要它只有升 Pro
> 或把库转公开。**"加了流水线"与"合并必须过流水线"是两件事**，别把前者当成后者。
>
> **运行号不进活文档。** run id、用例数这类会变的值不写进 `README` / `AGENTS` / 本文件（`0039`）——
> 要数字就现跑一遍。它们属于**有日期的记录**（PR 描述、issue、提交信息）：那里贴当时的 URL / id
> 恰恰是对的，因为那条记录说的就是"那一天跑出过什么"。

---

## 九、目录归属速查

| 我想加…                | 放哪                                                       |
| ---------------------- | ---------------------------------------------------------- |
| 一个新的前端应用       | `web/platform/<应用名>/`                                   |
| 一个跨应用复用的前端包 | `web/packages/common-<名字>/`                              |
| 一个新的后端业务模块   | `server/` 下按层次归位，规则见 `docs/decisions/0005`       |
| 一个新的后端接口       | `sts-server-<域>` 的对应特性包内，随后走第七节流程更新契约 |
| 一份架构决策           | `docs/decisions/NNNN-短标题.md`                            |
| 一个跨端脚本           | `scripts/`                                                 |
| 一份部署配置           | `infra/`                                                   |
| 一份流水线定义         | **只能是 `.github/workflows/`**（平台硬要求，见下）        |

> **CI 配置不放 `infra/`。** `infra/README.md` 曾约定"流水线定义放 `infra/ci/`"，但
> GitHub Actions **只从 `.github/workflows/` 读取**流水线 —— 这是平台的硬要求，不是我们的选择。
> 那个空占位目录已删除（留着一个永远装不进内容的目录只会给人指错路），
> `infra/` 从此只放与平台无关的部署资产。理由与分层见 `docs/decisions/0036`。
