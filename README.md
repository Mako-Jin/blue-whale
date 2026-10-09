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
│   ├── common-config         共享 ESLint / Stylelint / TypeScript 配置
│   ├── common-tools          公共能力包：日志（logger）· 环境探测（env）· 请求内核（http）
│   └── common-api            契约包：由 docs/api/openapi.json 生成的类型 + 接口装配
├── platform/             平台级应用
│   ├── platform-portal       主应用（qiankun 容器，负责挂载与导航）
│   └── platform-log-console  日志控制台（端到端演示：上报 → 落库 → 查询）
└── elemental/            通用元素（预留）
```

规划中的其余子应用与公共包（`platform-user`、`platform-admin`、`common-theme`、`common-components`）**按业务需要创建**，不在骨架里预置。

**微前端结构**：`platform-portal` 是唯一的容器应用，负责登录态、导航骨架与子应用挂载；用户端与管理端作为互相隔离的子应用接入，各自独立构建、独立发布。

`packages/common-tools` 与框架无关（不依赖 React），应用、构建脚本、Node 工具链都能直接使用。本地演示：`pnpm --filter common-tools playground`（logger，:3100）、`pnpm --filter platform-log-console dev`（日志上报全链路，:3200，需后端在 :8080）。

---

## 二、后端工作区 `server/`

Maven 多模块，**按能力层次单向依赖**。包名统一 `com.bluewhale.sts`。

```
server/
├── pom.xml               聚合根 · 只列模块，不做依赖管理
├── sts-parent/           父 POM · 依赖版本统一治理（零代码）
├── sts-common/           技术底座层
│   ├── sts-common-basic      统一响应 R · 错误码 · 业务异常
│   ├── sts-common-web        全局异常处理 + CORS + 自动配置
│   └── sts-common-db         MyBatis-Plus 配置（分页 / 乐观锁 / 防全表更新）
├── sts-server/           服务层
│   └── sts-server-log        日志域服务（客户端日志上报与查询）
└── sts-app/              业务应用层
    └── sts-app-server        唯一可启动进程（装配 + 健康检查 + 接口文档）
```

依赖方向：`sts-app → sts-server → sts-components → sts-common → sts-parent`，**禁止反向依赖，禁止跨层跳跃**。

规划中的 `sts-components/`（通用能力组件）**按需创建**，不在骨架里预置空壳 —— 与前端 `packages/` 是同一约定。

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

| 端 | 选型 |
|----|------|
| 前端 | React 19 + Vite 8 + TypeScript 6.0.3 + React Router 7 |
| 微前端 | qiankun 2.x |
| 前端工程 | pnpm workspace + Turborepo |
| 样式 | Less（设计令牌注入） |
| 代码规范 | ESLint 10 + Prettier 3 + Stylelint 17 + commitlint + Husky + Vitest |
| 后端 | Java 21 + Spring Boot 3.3.5 |
| 后端构建 | Maven 多模块（聚合根 `server/pom.xml`） |
| 数据访问 | MyBatis-Plus 3.5.9（H2 内存库开发，`MODE=MySQL`） |
| 后端接口文档 | springdoc-openapi 2.6.0（`/swagger-ui.html`） |
| 接口契约 | OpenAPI 3（`docs/api/`，由代码生成） |

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
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar
# → http://localhost:8080/api/health
# → http://localhost:8080/swagger-ui.html
```

### 前端

```bash
cd web && pnpm install                           # prepare 钩子会自动挂载 Git 钩子
pnpm dev --filter platform-portal                # 主应用
pnpm dev --filter platform-log-console           # 日志控制台（:3200，需后端在 :8080）
```

### 日志端到端演示

启动后端与日志控制台后，打开 <http://127.0.0.1:3200>：左栏点级别按钮产生日志，等自动上报后右栏就会出现同样的记录 —— 左边产生、右边出现，链路才算打通。

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

| 文档 | 内容 |
|------|------|
| `docs/README.md` | 文档规范：「改动 → 需同步哪些文档」对照表 |
| `CONTRIBUTING.md` | 环境、Git 钩子、提交规范、目录归属速查 |
| `AGENTS.md` | AI 助手协作约定 |
| `docs/decisions/` | 架构决策记录（ADR） |

**核心要求：改代码必须同步改文档，且在同一次提交里完成。**

---

## 八、延伸阅读

- `web/README.md` — 前端工作区与微前端约定
- `server/README.md` — 后端模块分层与依赖规则
- `docs/README.md` — 文档规范与索引
- `docs/decisions/` — 架构决策记录
- `infra/README.md` — 部署与环境
