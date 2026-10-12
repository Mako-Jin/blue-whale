# BlueWhale

大型全栈项目工作空间。**前端与后端是两个各自独立的工作区**，各有独立构建体系，通过 HTTP + OpenAPI 契约对接。

```
BlueWhale/
├── web/          前端工作区 · pnpm workspace · React + qiankun
├── server/       后端工作区 · Maven 多模块 · Java + Spring Boot
├── docs/         架构文档与 API 契约        ┐
├── scripts/      跨端脚本                   ├ 横向层，不属于任何一端
└── infra/        容器 / 网关 / CI / 部署    ┘
```

---

## 一、前端工作区 `web/`

pnpm workspace + Turborepo，按**业务域**分组。

```
web/
├── turbo.json            任务编排与缓存策略
├── pnpm-workspace.yaml   工作区成员 + 公共依赖版本（catalog）
├── eslint.config.js      工作区根 ESLint 配置（lint-staged 依赖它）
├── packages/             公共包（common-* 前缀）
│   ├── common-config         共享 ESLint / Stylelint / TypeScript 配置 + 跨应用共享的 Vite 片段
│   ├── common-tools          公共能力包：日志 · 环境探测 · 请求内核 · JSON 安全层 · 批量通道 · 行为事件内核 · 微前端桥接
│   └── common-api            契约包：由 docs/api/openapi.json 生成的类型 + 接口装配
├── platform/             平台级应用
│   ├── platform-portal       主应用（qiankun 容器，唯一入口，:3000）
│   ├── platform-log-console  日志控制台（子应用 + 可独立运行，:3200）
│   ├── platform-event-console 行为事件控制台（子应用 + 可独立运行，:3300）
│   ├── platform-admin        权限管理后台（子应用 + 可独立运行，:3500，用户 / 租户，按钮级权限演示）
│   ├── platform-audit-console 审计日志控制台（子应用 + 可独立运行，:3600，只读 + 演练区）
│   └── platform-environment  三维环境应用（子应用 + 可独立运行，:3400，three.js 多场景，古建筑可操控，与业务无关）
└── elemental/            通用元素（预留）
```

规划中的其余子应用与公共包（`platform-user`、`common-theme`、`common-components`）**按业务需要创建**，不在骨架里预置。

**微前端结构**：`platform-portal` 是唯一的容器应用，负责导航骨架与全局状态，并通过 `loadMicroApp` 在路由出口处挂载子应用（而不是 `registerMicroApps` + 常驻容器 —— 理由见 `docs/decisions/0012`）。子应用各自独立构建、独立发布，**并且都能脱离基座单独启动**：调试微前端时第一件事就是区分"子应用本身坏了"还是"基座加载链路坏了"，没有这条退路就只能盲猜。接线方式：基座侧集中在 `platform-portal/src/micro/`，子应用侧只依赖 `common-tools/micro`。

`packages/common-tools` 与框架无关（不依赖 React），应用、构建脚本、Node 工具链都能直接使用。本地演示：`pnpm --filter common-tools playground`（logger，:3100）、`pnpm --filter platform-log-console dev`（日志上报全链路，:3200，需后端在 :8080）、`pnpm --filter platform-event-console dev`（行为事件全链路，:3300，需后端在 :8080）、`pnpm --filter platform-environment dev`（三维环境，:3400，无后端依赖）、`pnpm --filter platform-admin dev`（按钮级权限，:3500，需后端在 :8080）、`pnpm --filter platform-audit-console dev`（审计管道，:3600，需后端在 :8080）。

---

## 二、后端工作区 `server/`

Maven 多模块，**按能力层次单向依赖**。包名统一 `com.bluewhale.sts`。

```
server/
├── pom.xml               聚合根 · 只列模块，不做依赖管理
├── sts-parent/           父 POM · 依赖版本统一治理（零代码）
├── sts-common/           技术底座层
│   ├── sts-common-basic      统一响应 R · 错误码 · 业务异常
│   ├── sts-common-web        全局异常处理 + CORS + 自动配置 + Web 拦截器顺序常量
│   └── sts-common-db         MyBatis-Plus 配置（分页 / 乐观锁 / 防全表更新）
├── sts-components/       通用能力组件层
│   └── sts-components-audit  @Audited 注解 + 审计目标上下文（只提供类型，不装配 Bean）
├── sts-server/           服务层
│   ├── sts-server-log        日志域服务（客户端技术日志上报与查询）
│   ├── sts-server-track      行为事件域服务（埋点上报、查询、按事件名聚合）
│   ├── sts-server-auth       权限域服务（身份 / 访问控制 / 认证 / 鉴权）
│   └── sts-server-audit      审计域服务（拦截器自动产生、只读查询、按动作聚合）
└── sts-app/              业务应用层
    └── sts-app-server        唯一可启动进程（装配 + 健康检查 + 接口文档）
```

依赖方向：`sts-app → sts-server → sts-components → sts-common → sts-parent`，**禁止反向依赖，禁止跨层跳跃**。

`sts-components/` 已经落地（`sts-components-audit`，见 `docs/decisions/0019`）—— 它存在的判据是
「**一个类型被两个域同时需要，而下沉它不引入业务语义**」：`@Audited` 由权限域（标注解）与
审计域（读注解）共用，谁依赖谁都会成环。文件、消息这类通用能力依旧**按需创建，不预置空壳**。

详见 `server/README.md` 与 `docs/architecture/后端分层与依赖方向.md`。

---

## 三、前后端如何对接

Java 与 TypeScript 无法直接共享类型，因此以 **OpenAPI 契约**作为唯一真相源：

```
server/  ──生成──▶  docs/api/openapi.json  ──生成──▶  web/packages/common-api
   （后端实现）          （唯一契约）                    （前端类型 + 请求客户端）
```

- 后端新增或修改接口后，重新生成契约。
- 前端 SDK **禁止手写**，一律由契约生成。
- 任何一方私自改字段，都会在下一次生成时暴露出来。

---

## 四、铁律

1. **两端不共用构建体系。** `web/` 用 pnpm，`server/` 用 Maven，互不侵入。
2. **跨端只认契约。** 前端不得绕过 `common-api` 手写裸请求；后端不得绕过契约改字段。
3. **跨端共享物只放 `docs/`。** 需要两端共同遵守的内容（契约、枚举定义、错误码表）写在 `docs/`，不放在任何一端的代码里。
4. **`packages/*` 不得反向依赖 `platform/*`。** 依赖方向永远单向。
5. **子应用之间互不引用。** `platform-user` 与 `platform-admin` 需要复用的东西下沉到 `packages/`。
6. **后端模块依赖只能自上而下。** `sts-app` → `sts-server` → `sts-components` → `sts-common`，禁止反向或跨层跳跃。

---

## 五、技术栈

| 端           | 选型                                                                                                                                          |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| 前端         | React 19 + Vite 8 + TypeScript 6.0.3 + React Router 7                                                                                         |
| 微前端       | qiankun 2.x（基座 `loadMicroApp` 挂载 + 子应用 `vite-plugin-qiankun` 导出生命周期，见 ADR-0012）                                              |
| 前端工程     | pnpm workspace + Turborepo                                                                                                                    |
| 样式         | Less（设计令牌注入）                                                                                                                          |
| 代码规范     | ESLint 10 + Prettier 3 + Stylelint 17 + commitlint + Husky + Vitest                                                                           |
| 后端         | Java 21 + Spring Boot 4.1（Spring Framework 7 · Tomcat 11 · Jackson 3）                                                                       |
| 后端构建     | Maven 多模块（聚合根 `server/pom.xml`，enforcer 强制 JDK 21+）                                                                                |
| 数据访问     | MyBatis-Plus 3.5.17（H2 内存库为默认，可切本机 MySQL / 达梦 DM8）                                                                             |
| 表结构管理   | Flyway 12（迁移脚本按数据库厂商分目录，**版本号全仓唯一**；达梦无官方模块，改走交付脚本，见 ADR-0010）                                        |
| 前端埋点     | `common-tools/events` 语义化 `track()` + 内部总线（中间件：补齐上下文 / 屏蔽 / 脱敏）；上报复用 `common-tools/report` 的批量通道，见 ADR-0011 |
| 审计日志     | 由**后端拦截器**在写接口的 `afterCompletion` 自动产生（显式 `@Audited` 标注），前端**只读**、无写入路径；见 ADR-0019                          |
| 三维渲染     | three.js（r186）—— 环境应用 `platform-environment`，程序化生成多场景，见 ADR-0013 / ADR-0014                                                  |
| 后端接口文档 | springdoc-openapi 3.1.1（`/swagger-ui.html`）                                                                                                 |
| 接口契约     | OpenAPI 3.1（`docs/api/`，由代码生成）                                                                                                        |

> TypeScript 锁定在 **6.0.3**：`typescript-eslint` 的 peer 约束为 `<6.1.0`，7.x 尚不被支持。

### 依赖治理

第三方依赖的版本统一写在 `web/pnpm-workspace.yaml` 的 `catalog:` 段，各 `package.json` 一律写 `"catalog:"`。

- **一个版本只写一处**，升级不必满仓库查找。
- **不建"存放依赖的包"**——那会产生**幽灵依赖**（库能被引用到、却没在引用方的 `package.json` 里声明，运行时解析不到）。catalog 只统一版本号，依赖归属关系仍留在各自的 `package.json`。
- **自研公共代码**走 `packages/common-*`，与依赖版本是两件不同的事，不要混在一起。

详见 `docs/decisions/0002-前端公共包与公共依赖治理.md`。

---

## 六、快速开始

### 后端

```bash
cd server && mvn clean install -DskipTests       # 构建（需 JDK 21）

# 默认走 H2 内存库，零外部依赖
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar
# → http://localhost:8080/api/health
# → http://localhost:8080/swagger-ui.html

# 连本机 MySQL：数据落在磁盘上，重启后仍在（需先建库，见 server/README.md）
BW_DB_PASSWORD=<密码> java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=mysql
# 连达梦 DM8（国产库）：表结构不由应用创建，需先执行交付脚本（见 server/README.md 第六节）
BW_DB_USERNAME=<用户> BW_DB_PASSWORD=<密码> java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=dameng
```

> **上线前务必设 `BW_JWT_SECRET`** —— 配置里那个默认 secret 是只给本地开发用的占位符，
> 而且已经进了版本库（等于公开）。它用来签名令牌，属密钥级配置（`docs/decisions/0003`），
> 详见 `server/README.md` 的「权限域的几条硬规则」。

### 前端

```bash
cd web && pnpm install                           # prepare 钩子会自动挂载 Git 钩子
pnpm dev --filter platform-portal                # 主应用（qiankun 基座，:3000）
pnpm dev --filter platform-log-console           # 日志控制台（:3200，需后端在 :8080）
pnpm dev --filter platform-event-console         # 行为事件控制台（:3300，需后端在 :8080）
pnpm dev --filter platform-environment           # 三维环境应用（:3400，无后端依赖）
pnpm dev --filter platform-admin                 # 权限管理后台（:3500，需后端在 :8080）
pnpm dev --filter platform-audit-console         # 审计日志控制台（:3600，需后端在 :8080）
```

### 微前端端到端演示

调试微前端要**同时起各应用的 dev server**（基座按固定端口去找子应用）：

```bash
pnpm dev --filter platform-portal --filter platform-log-console --filter platform-event-console --filter platform-environment --filter platform-admin --filter platform-audit-console
```

打开 <http://127.0.0.1:3000>，点导航即可挂载子应用；顶栏的「全局主题」「登录」按钮用来验证**全局状态下发** —— 子应用右下角的调试标记会跟着变（`theme=dark · userId=u-xxx`），这是"状态确实传下去了"最直接的证据。

一键验证（用真实 Chrome 走完整链路：挂载 → 状态下发 → 子应用经基座代理请求后端 → 切换子应用 → 返回首页验证卸载清理；三维应用额外断言 WebGL 上下文已释放）：

```bash
node scripts/verify-micro-frontend.mjs          # 需上面各服务已在跑
```

> **子应用单独跑是常态。** 直接开 :3200 / :3300 / :3400 / :3500 / :3600 能立刻区分"问题在子应用本身"还是"问题在基座加载链路"。另：被基座加载时子应用的 **React 组件级热更新不生效**（样式正常），改组件时单独跑更快 —— 这是已记录的已知限制，见 `docs/decisions/0012`。

### 日志端到端演示

启动后端与日志控制台后，打开 <http://127.0.0.1:3200>：左栏点级别按钮产生日志，等自动上报后右栏就会出现同样的记录 —— 左边产生、右边出现，链路才算打通。

### 行为事件端到端演示

启动后端与行为事件控制台后，打开 <http://127.0.0.1:3300>：左栏点「触发语义化事件」产生埋点，等自动上报后右栏按事件名聚合与明细分页就会刷新。左栏还可以现场演示三件事：

- **身份缝合** —— 点「登录」后新产生的事件立刻带上 `userId`，而 `deviceId` 不变：两者同时挂在新事件上，分析侧才能把匿名期与登录期并成同一个人
- **脱敏** —— 点「查看联系方式」，`phone` / `email` 在**离开浏览器之前**就被打码，明文从未出现在任何一次请求里
- **屏蔽** —— 点某个事件的 chip，命中后该事件在进入缓冲之前就被丢弃，左栏不会出现、服务端更不会收到

### 登录态与按钮级权限端到端演示

后端 h2 profile 会种下两个对照账号：`admin`（21 项权限）与 `auditor`（3 项只读权限）。
打开 <http://127.0.0.1:3000>：未登录访问任何受保护路由都会被守卫弹到 `/login`；登录后
导航出现，子应用右下角的调试标记显示当前身份的 `userId` / `roles` / `perm` 数量。

打开 <http://127.0.0.1:3000/admin>（或直接开 <http://127.0.0.1:3500>）：**同一段代码、同一个页面**，
`admin` 看到全部操作入口，`auditor` 只看得到列表 —— 差异不写在代码里，只写在基座下发的权限集合里。

```bash
node scripts/verify-portal-auth.mjs         # 登录态链路（守卫 / 令牌持久化 / 会话恢复 / 登出）
node scripts/verify-admin-permissions.mjs   # 两种身份的按钮差异 + 真实增删一次
```

### 审计端到端演示

打开 <http://127.0.0.1:3600>（或基座的 <http://127.0.0.1:3000/audit>）：自上而下三块 ——
**流水**（按服务端时间倒序，带时间窗）→ **按动作聚合** → **演练区**。

演练区是这条管道唯一能"当场看见因果"的地方：点一下，真的调用 `POST /api/auth/tenants`
建一个租户，等审计表出现 `auth.tenant.create`，再删掉它、等 `auth.tenant.delete` 出现。
换成 `auditor` 点同样的按钮会拿到两次拒绝 —— **而拒绝同样留痕**，这正是演练区按钮
刻意不按权限显隐的原因（若沿用"没权限就不渲染"，最值得看的那类记录就被从演示里抹掉了）。

```bash
node scripts/verify-audit-pipeline.mjs      # 需后端 :8080、基座 :3000、审计台 :3600 在跑
```

### 三维环境端到端演示

打开 <http://127.0.0.1:3400>（或基座的 <http://127.0.0.1:3000/environment>）：顶部是卡片式场景选择器（古建筑 / 原始丛林 / 现代写字楼 / 太空飞船 / 渲染基线），点卡片即切换场景，右侧 HUD 实时显示帧率、绘制调用与三角面数。

**古建筑是第一个可操控场景**：殿前广场上有可以走的主人公 —— `WASD` / 方向键移动（方向永远相对镜头）、滚轮缩放视角、**鼠标右键设置行动终点**（人物会自己走过去），左上角小地图显示广场全貌、人物朝向、相机视野与指北方向。左下角"渲染控制"面板的开关照常可用。

反复切换场景不会产生第二个 WebGL 上下文、也不会累积 GPU 资源（切场景只换场景图，渲染器与画布复用），可用脚本断言：

```bash
node scripts/verify-scene-switching.mjs         # 需 :3400 在跑；可传截图目录
node scripts/verify-player-controls.mjs         # 主人公操控链路；需 :3400 在跑；可传截图目录
```

### Git 钩子

钩子脚本在 `web/.husky/`，通过 Git 原生 `core.hooksPath` 挂载（不是 `.git/hooks/`）。

- `pre-commit` —— 对暂存文件跑 lint-staged（ESLint / Prettier / Stylelint）
- `commit-msg` —— 校验提交信息符合 Conventional Commits

若钩子未生效，手动挂载：

```bash
git config core.hooksPath web/.husky
```

---

## 七、文档与约定

| 文档              | 内容                                      |
| ----------------- | ----------------------------------------- |
| `docs/README.md`  | 文档规范：「改动 → 需同步哪些文档」对照表 |
| `CONTRIBUTING.md` | 环境、Git 钩子、提交规范、目录归属速查    |
| `AGENTS.md`       | AI 助手协作约定                           |
| `docs/decisions/` | 架构决策记录（ADR）                       |

**核心要求：改代码必须同步改文档，且在同一次提交里完成。**

---

## 八、延伸阅读

- `web/README.md` — 前端工作区与微前端约定
- `server/README.md` — 后端模块分层与依赖规则
- `docs/README.md` — 文档规范与索引
- `docs/decisions/` — 架构决策记录
- `infra/README.md` — 部署与环境
