# CI/CD 流水线方案设计

| 项 | 内容 |
|---|---|
| 需求编号 | REQ-CICD-001 |
| 状态 | 设计已定稿 / CI、镜像矩阵与部署契约检查已落地；编排部署段待建 |
| 最后更新 | 2026-10-28 |
| 作者 | BlueWhale |
| 落点 | `.github/workflows/`（现状）+ `deploy/`（Dockerfile）+ Gitee Go（新增） |

---

## 1. 现状盘点

### 1.1 现有 Workflow（如实描述）

`.github/workflows/` 下共 **3 条** 流水线：

| 文件 | 触发条件 | 关键步骤 | 产物 | 并发策略 |
|---|---|---|---|---|
| `backend-ci.yml` | `push` main（paths: `whale-backend/**`、`pom.xml`、本文件）；`pull_request` → main（同 paths） | Checkout → Setup JDK 21（temurin + maven cache）→ Maven Wrapper 初始化（`mvn -N wrapper:wrapper -Dmaven=3.9.9`）→ `./mvnw -B -ntp test` → `./mvnw -B -ntp clean package -DskipTests` → Upload JAR | `backend-jars`（`**/target/*.jar`，保留 7 天，仅 push main） | `backend-ci-${{ github.ref }}`，`cancel-in-progress: true` |
| `frontend-ci.yml` | `push` main（paths: `whale-frontend/**`、`pnpm-workspace.yaml`、`package.json`、本文件）；`pull_request` → main（同 paths） | Checkout → Setup Node 24（pnpm cache）→ corepack 启用 pnpm → `pnpm install --frozen-lockfile` → `pnpm -r lint` → `pnpm turbo run build` → Upload dist | `frontend-dist`（`**/dist/`，保留 7 天，仅 push main） | `frontend-ci-${{ github.ref }}`，`cancel-in-progress: true` |
| `docker-deploy.yml` | `push` main（paths: `whale-backend/**`、`whale-frontend/**`、`deploy/**`、本文件；2026-10-28 新增，docs 等无关提交不触发）；`push` tag `v*.*.*`；`workflow_dispatch`（可选 `image_tag` 输入，默认短 SHA） | Checkout → **部署契约静态检查** → 登录 GHCR（`GITHUB_TOKEN`）→ `docker/metadata-action` 生成 tag → `setup-buildx-action` → 矩阵构建推送 21 个后端服务 + 14 个前端应用 | 镜像推送至 GHCR；`cache-from/to: type=gha`（后端 `be-*`、前端 `fe-*` 分 scope） | `docker-deploy-${{ github.ref }}`，`cancel-in-progress: false` |
| `deploy-contract-check.yml` | `push` / `pull_request`：`scripts/ci/**`、`infrastructure/docker/nginx/**`、`docker-compose.prod.yml`、`deploy/frontend/Dockerfile`、两个部署 workflow 自身 | Checkout → PowerShell 部署契约静态检查（21+14 矩阵、前后端同名 namespace、8 base、Nginx 8 前缀、TLS/read-only） | 无镜像构建；失败阻断合并/后续部署 | 默认 GitHub Actions 并发 |

`docker-deploy.yml` 关键变量：

- `REGISTRY=ghcr.io`，后端镜像 `ghcr.io/${{ github.repository }}/${{ matrix.service }}`；前端默认沿用 `ghcr.io/${{ github.repository }}/${{ matrix.image }}`，其中与后端同名的 8 个应用使用 `ghcr.io/${{ github.repository }}/whale-frontend/${{ matrix.image }}`。
  `whale-im`/`erp`/`oa`/`mes`/`pdm`/`pm`/`agent`/`contract` 与后端服务同名，若不隔离会写入同一个 GHCR package 并互相覆盖 `latest`/sha tag；`whale-web` 等不冲突应用保留原名，避免破坏既有部署引用。
  构建缓存同步使用 `be-*` / `fe-*` scope，避免同名服务串用缓存层。部署契约由 `scripts/ci/bw-deploy-contract-check.ps1` 在构建前阻断；
  前端容器需接入 `bw-net`，并使用网络别名 `whale-fe-<app>`（portal/contract/erp/oa/mes/pdm/pm/im），供生产 Nginx 按前缀反代；
- 镜像 tag 规则：`type=ref,event=tag`（即 `v1.2.3`）+ `type=sha,prefix=sha-` + `latest`（仅默认分支，`enable={{is_default_branch}}`）+ 手动触发的 `image_tag`（仅在输入非空时生效，`enable=${{ inputs.image_tag != '' }}`）；
- 后端矩阵 21 项（`whale-gateway`、auth starter、job、workflow、form、file、pay、agent、im、archive、message、erp、mes、pdm、oa、pm、**contract**、audit、monitor、alert、config），统一 `deploy/backend/sample/Dockerfile` + `MODULE_PATH`/`SERVICE_PORT`；
- 前端矩阵 14 项（`whale-web`=admin、`whale-platform` 基座、portal、ops、agent、contract、erp、oa、mes、pdm、pm、im、form-designer、workflow-designer），统一 `deploy/frontend/Dockerfile` + `APP_NAME`/`APP_DIR`/`APP_BASE`；
- 后端镜像构建使用 `mvn -Dmaven.test.skip=true`（跳过测试编译与执行）+ wagon 重试 5 次 + 整体 3 次重试：镜像环境无 `.git`/`docs`/MySQL，单测由 `backend-ci` 负责；父 pom 曾硬编码 `<skipTests>false</skipTests>` 使命令行 `-DskipTests` 失效，已移除（2026-10-28）。
- `whale-agent` 额外传 `NEED_FACT_ASSETS=true`：`fact-assets` 阶段把 `whale-backend/**/*.{java,sql,yml,yaml,properties,pom.xml}`、`whale-frontend/**/{package.json,*.ts,tsx,js,jsx,vue}`、`docs/**/*.md` 裁剪为 `/opt/whale-repo` 只读素材包，并设置 `WHALE_AGENT_REPO_ROOT=/opt/whale-repo`、`WHALE_AGENT_INDEX_DIR=/app/data/agent-index`；其余服务为空目录，不增加体积。因该阶段需要 `docs`，`.dockerignore` 已不再整体排除 `docs`（V-16）。
- `APP_BASE`（默认 `/`）必须与各应用 `vite.config.ts` 的 `base` 一致：子应用为 `/portal`、`/contract`、`/erp`、`/oa`、`/mes`、`/pdm`、`/pm`、`/im`，其余（admin、基座、ops、agent、两个设计器）为 `/`。镜像内产物落到 `/usr/share/nginx/html${APP_BASE}`，并按 base 选用 `deploy/frontend/nginx/root.conf` 或 `subapp.conf.template`（前缀内 SPA 回退、前缀外 404、`/base` 301 到 `/base/`）。

### 1.2 现状补充事实

| 事实 | 说明 |
|---|---|
| 代码托管 | `origin = https://gitee.com/StrongToStop/blue-whale.git`，分支 `main`；GitHub 推送曾因网络超时，改推 Gitee |
| 后端构建 | Maven 多模块（`whale-parent` 为根，25 模块），`whale-parent` 继承 `spring-boot-starter-parent:4.0.8` |
| 前端构建 | pnpm workspace + Turborepo（根 `package.json`：`build=turbo run build`、`lint=eslint .`），ESLint 9 flat config + Prettier |
| 质量门禁 | **当前无后端静态检查（无 Checkstyle / SpotBugs / JaCoCo）；前端仅有 ESLint（`pnpm -r lint`）** |
| 部署环境 | `infrastructure/docker/docker-compose.yml`（dev）；`deploy/`（镜像模板）；**本机未安装 Docker**，镜像构建无法本地实跑 |
| 依赖版本不一致 | `whale-parent` 管理 jjwt `0.12.5`，但 `whale-gateway/pom.xml` 显式覆盖为 `0.12.6`（需收敛） |

---

## 2. 目标流水线全阶段

```
代码提交 ──► 静态检查 ──► 构建 ──► 单元测试 ──► 制品 ──► 镜像 ──► 部署 ──► 通知
   │            │           │          │            │        │        │       │
 commit/PR    lint/规范    compile    test+覆盖率   jar/dist  GHCR    env    IM/邮件
                                                归档         推送   deploy
```

| 阶段 | 后端 | 前端 | 门禁 | 现状 |
|---|---|---|---|---|
| ① 代码提交 | Git push / PR | 同 | 无（人工） | ✅ 已有 paths 过滤 |
| ② 静态检查 | Checkstyle（+ SpotBugs 可选） | ESLint + Prettier check | **阻断** | ⚠️ 前端有，后端无 |
| ③ 构建 | `mvn -B clean package` | `turbo run build` | 失败阻断 | ✅ |
| ④ 单测 | `mvn test` + JaCoCo 报告 | Vitest（待建） | 覆盖率阈值 | ⚠️ 有测试无覆盖率 |
| ⑤ 制品 | `**/target/*.jar` 上传 | `**/dist/` 上传 | 保留 7 天 | ✅ |
| ⑥ 镜像 | GHCR 21 个后端服务镜像 | GHCR 14 个前端应用镜像（`whale-frontend/` 子命名空间） | main push / tag / 手动触发 | ✅ |
| ⑦ 部署 | dev/staging/prod 环境部署 | 同 | 人工审批（prod） | ❌ 待建 |
| ⑧ 通知 | IM/邮件（失败必达） | 同 | — | ❌ 待建 |

---

## 3. 代码质量门禁设计

### 3.1 后端

| 工具 | 定位 | 建议 | 阻断策略 |
|---|---|---|---|
| Checkstyle | 命名/格式/魔法值/圈复杂度 | 引入 `maven-checkstyle-plugin`，规则集定制（禁魔法值、单方法复杂度 ≤ 15、类行数上限） | **error 阻断**（warning 可放行） |
| SpotBugs（可选） | 潜在缺陷/协议漏洞 | 引入 `spotbugs-maven-plugin`，先 INFO 观察 | 初期仅报告不阻断，稳定后阻断 High |
| JaCoCo | 覆盖率 | `jacoco-maven-plugin` 生成报告 + `check` 规则 | 见 3.3 |

> 现有代码含占位骨架，Checkstyle 首轮建议以 `warning` 模式全量扫描，修正后再切 `error` 阻断，避免一次性红灯。

### 3.2 前端（已有基础）

| 工具 | 现状 | 增量建议 |
|---|---|---|
| ESLint 9 flat config | ✅ `pnpm -r lint`（`no-console`/`no-explicit-any` 等为 warn） | 关键规则（如 `no-unused-vars`）升级为 error |
| Prettier | ✅ `format:check` 脚本存在，**CI 未执行** | CI 增加 `pnpm format:check` 阻断 |
| 类型检查 | Vite 构建内含 | 显式 `tsc --noEmit` 步骤 |

### 3.3 覆盖率阈值建议

| 范围 | 建议阈值 | 说明 |
|---|---|---|
| 全仓库行覆盖率 | ≥ 40% | 首轮基线（当前大量骨架），逐季上调 |
| `whale-common` / `whale-i18n-sdk` 核心模块 | ≥ 60% | 已有单测基础（i18n 14 用例全绿） |
| 新增/变更代码（增量） | ≥ 70% | 防止新增无测试 |
| 分支覆盖率 | ≥ 30% | 首轮 |

阈值按下列方式落地，未达阈值 **构建失败**。

落地方式（2026-11-13 调整）：`jacoco:check` 的 BUNDLE 规则作用于 reactor 中的**每个模块**，
本工程 69 个模块里大量是 DTO / 装配层，单模块 40%/30% 不可达，门禁会在第 2 个模块
（`whale-common-core`）即失败，并连带挡掉后续全部模块的测试信号。故 CI 改为：

- `mvn verify` 只负责跑测试、产出 `target/site/jacoco/jacoco.xml`；
- `scripts/ci/check-coverage.py` 判定门禁：**项目聚合覆盖率**对齐上表阈值
  （行 40% / 分支 30%），并对**每个模块**做基线棘轮（`scripts/ci/coverage-baseline.json`，
  只升不降，回落超过 0.5 个百分点即失败）；核心模块未达 60% 时在日志中提示目标值。

核心模块达标记录（2026-11-14）：`whale-common` 与 `whale-i18n-sdk` 全部 ≥ 60%——
starter 62.27%（补 `DataSourcePasswordGuard` / `TenantContextFilter` / `LocalLogSink` /
`OutboundLogRestTemplateCustomizer` / `RestApiElapsedAspect` / `MybatisPlusConfig` 六个类的单测，
原 57.16%）、`whale-tools` 100%（补 `AssertX` / `IdUtils` / `StatusEnum`，原 48.98%）。

`-Pcoverage-gate` 保留为**本地单模块自检**用（`-pl :whale-common-core -Pcoverage-gate verify`），
CI 不再使用。

---

## 4. 分支策略与触发矩阵

| 分支/事件 | 触发流水线 | 静态检查 | 构建 | 单测 | 制品 | 镜像 | 部署 |
|---|---|---|---|---|---|---|---|
| `feature/*` push | ✅ | ✅ | ✅ 编译 | ✅ | ❌ | ❌ | ❌ |
| PR → `main` | ✅ | ✅ 阻断 | ✅ | ✅ + 覆盖率门禁 | ❌ | ❌ | ❌ 仅预览 |
| `main` push | ✅ | ✅ | ✅ | ✅ | ✅ 归档 | ✅ `latest` + `sha-xxx`（paths 命中时，2026-10-28 起） | ✅ dev 自动 |
| tag `v*.*.*` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ 打版本 tag | ✅ staging/prod（人工审批） |
| 手动 `workflow_dispatch` | ✅ | ✅ | ✅ | 可选 | 可选 | ✅（可选 `image_tag`） | 指定环境 |

分支模型建议：**主干 + 短生命周期特性分支**（main 保护 + PR 必须通过门禁），发布以 tag 标记。

---

## 5. 制品与镜像命名规范

### 5.1 制品

| 类型 | 命名 | 说明 |
|---|---|---|
| 后端 JAR | `{artifactId}.jar`（`finalName=${project.artifactId}`，如 `whale-service-auth.jar`） | 各模块 `target/` 下，CI 汇总为 `backend-jars` |
| 前端产物 | `**/dist/` | qiankun 基座与子应用各自 dist，汇总为 `frontend-dist` |

### 5.2 镜像（GHCR 现状 + 规范）

| 项 | 现状 | 规范化建议 |
|---|---|---|
| Registry | `ghcr.io` | 保持不变（GitHub 场景）/ `registry.gitee.com`（Gitee 场景） |
| 镜像名 | `ghcr.io/{owner}/{repo}/whale-gateway`、`.../whale-web` | 后端统一 `whale-{service}`；前端默认 `whale-{app}`，与后端同名的 8 项使用 `whale-frontend/whale-{app}` 隔离，admin 等既有名保持不变；2026-10-28 起后端 21 项 + 前端 14 项全部纳入 |
| Tag | `v1.2.3`（ref tag）、`sha-{short}` | 主分支追加 `latest`；生产仅用语义化版本 tag |
| 标签（Label） | `org.opencontainers.image.*` 已在 Dockerfile | 保持一致，补 `revision` = commit SHA |
| 多架构 | 未启用 | 按需 `linux/amd64,linux/arm64` |
| 覆盖服务 | 后端 21 项 + 前端 14 项（2026-10-28 补齐 contract 与全部前端应用） | 后续仅需按新增服务追加矩阵项（模板 `deploy/backend/sample/Dockerfile`、`deploy/frontend/Dockerfile`） |

---

## 6. 双代码托管说明（Gitee ↔ GitHub）

### 6.1 问题

仓库 `origin` 指向 **Gitee**（`https://gitee.com/StrongToStop/blue-whale.git`），**GitHub Actions 只在 GitHub 侧事件触发，无法在 Gitee 上的 push 时运行**。`.github/workflows/` 现有 3 条流水线在 Gitee 上**不会执行**。

### 6.2 方案对比

| 方案 | 触发源 | 说明 | 优点 | 缺点 |
|---|---|---|---|---|
| A. Gitee Go（主） | Gitee push/tag/PR | 在 Gitee 侧用 `.workflow/` 目录编写流水线，走 Gitee 制品库/镜像仓库 | 与当前 origin 一致，可立即触发 | 需重写一套流水线；生态与 Actions 不通用 |
| B. GitHub Actions（保留） | GitHub push/tag/PR | 保持现有 3 条 workflow，需代码同步到 GitHub 并开通 Actions | 复用现有流水线 | GitHub 网络不稳定（历史上推送超时） |
| C. 双写（A+B） | 两侧 | Gitee Go 为日常 CI 主链路；GitHub 作镜像仓库同步后跑 Actions | 双保险 | 维护两套配置，成本最高 |

### 6.3 推荐（2026-10-22 更新：实际落地方案 B；2026-10-28 冻结）

**实际落地：方案 B（GitHub Actions）**。Gitee Go 为预付费增值服务，本仓库未开通，无法免费触发；已在 GitHub 新建私有镜像仓 `Mako-Jin/blue-whale-platform`（remote `ci`）承接 CI，Gitee `origin` 继续作为主托管与推送目标。

> **2026-10-28 决策（V-07）**：Gitee Go 流水线**不做（won't do）**，不再以“实机触发”为待办；CI/CD 完全依赖 GitHub Actions。`.workflow/` 仅作历史留存，不再维护，也不迁移 Gitee Go 配置。

日常推送需双 remote：

```powershell
git push origin main   # Gitee 主仓
git push ci main       # GitHub 镜像仓，触发 Backend CI / Frontend CI / Code Quality
```

实测（2026-10-22）：push 触发正常；故意引入未使用变量 + 缺 Javadoc 后三条流水线全部变红，revert 后恢复全绿。GitHub 直连偶发超时，push 失败时重试即可。

`.workflow/` 仅作历史留存，Gitee Go **不做**；下表仅保留为历史参考，不再执行迁移：

| 阶段 | Gitee Go 配置 |
|---|---|
| 环境 | `maven:3.9-eclipse-temurin-21` / `node:24`（或使用 Gitee 提供的构建镜像） |
| 触发 | `push` main + `PR` + `tag v*.*.*` |
| 后端 | `mvn -B -ntp test` → `mvn -B -ntp clean package -DskipTests` |
| 前端 | `pnpm/action-setup` 等价安装 pnpm 10.33 → `pnpm install --frozen-lockfile` → `pnpm lint` → `pnpm turbo run build` |
| 制品 | 上传到 Gitee 制品库（替代 GHCR 上传） |
| 镜像 | Gitee 支持推送至 `registry.gitee.com` 或自建 Harbor |

> 因本机**未安装 Docker**，镜像构建/推送类步骤暂无法实跑，先落地"构建 + 测试 + 制品"三段，镜像段标记为待验证。

---

## 7. 环境矩阵

| 环境 | 用途 | 触发 | 中间件 | 部署方式 | 审批 |
|---|---|---|---|---|---|
| dev | 开发自测 | main push 自动 | 本机/单机 compose（`infrastructure/docker`） | Docker Compose | 无 |
| staging | 预发布验证 | tag 自动 | 独立 compose（数据隔离） | Compose / K8s | 无（或轻量） |
| prod | 生产 | tag + 人工 | 生产集群 | K8s（`docker-compose.prod.yml` 备选） | **必须人工审批** |

配置隔离：环境变量注入（`SPRING_PROFILES_ACTIVE=dev|staging|prod`）；密钥经 Secrets 注入，禁止入库入镜像。

---

## 8. 密钥与变量管理

| 名称 | 用途 | 存放 |
|---|---|---|
| `GITHUB_TOKEN` | GHCR 登录（现有 workflow 已用） | GitHub 自动提供 |
| `GITEE_TOKEN` / 私密变量 | Gitee Go 制品/镜像推送 | Gitee 环境变量 |
| `JWT_SECRET` | 覆盖默认 `bluewhale-jwt-secret-2026-...`（auth/gateway 必须一致） | CI/CD Secrets，**禁止**硬编码 |
| `MYSQL_PASSWORD` / `REDIS_PASSWORD` | 数据源 | Secrets |
| `NACOS_ADDR` / `NACOS_NAMESPACE` / `NACOS_AUTH_IDENTITY_*` | 注册配置中心 | Secrets / 环境变量 |
| `MYSQL_ROOT_PASSWORD` | compose 初始化 | 本地 `.env`（**不提交**） |

原则：① 所有密钥走平台 Secrets，仓库内仅保留默认占位值与 `.env.example`；② 生产密钥与应用密钥分离；③ Dockerfile 不写入任何密钥，运行时注入。

---

## 9. 风险与任务拆分

### 9.1 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| origin 在 Gitee，Actions 不触发 | 现有 3 条 workflow 实际未运行 | **已决（2026-10-28）**：不做 Gitee Go，完全依赖 GitHub Actions；push 到 `ci` remote 触发，失败重试 |
| 本机未安装 Docker | 镜像构建/部署段不可实跑 | 先落地构建/测试/制品，镜像段待容器就绪 |
| Checkstyle 首轮全量红灯 | 阻塞合并 | 先 warning 观察，修正后切 error |
| 覆盖率基线极低 | 阈值过高压制开发 | 40% 起步渐进上调 |
| 依赖版本不一致（jjwt 0.12.5 vs 0.12.6） | 潜在运行时差异 | 收敛到 parent 统一版本 |
| Token 泄露 | 仓库/镜像泄露密钥 | 全量走 Secrets + 轮换 |

### 9.2 任务拆分

| 编号 | 任务 | 依赖 | 工作量 | 验收 |
|---|---|---|---|---|
| T-CI-1 | 后端 Checkstyle 引入（warning 模式）+ 规则集 | 无 | M | `mvn checkstyle:check` 出报告 |
| T-CI-2 | JaCoCo 引入 + 覆盖率门禁（§3.3） | 无 | M | 未达阈值构建失败 |
| T-CI-3 | SpotBugs 引入（INFO 观察） | T-CI-1 | S | 产出报告 |
| T-CI-4 | 前端 CI 补 `format:check` + `tsc --noEmit` | 无 | S | 门禁生效 |
| T-CI-5 | ~~新增 Gitee Go 流水线（构建/测试/制品）~~ **已冻结（won't do，2026-10-28）** | 无 | — | 不做；CI 完全依赖 GitHub Actions |
| T-CI-6 | 通知环节（失败必达 IM/邮件） | T-CI-5 | S | 失败收到通知 |
| T-CI-7 | 镜像构建扩至全服务（模板 `deploy/backend/sample`） | T-CI-5 | M | 容器就绪后验证 |
| T-CI-8 | dev/staging 自动部署 + prod 人工审批 | T-CI-7 | L | 环境矩阵落地 |
| T-CI-9 | 收敛 jjwt 版本（gateway 0.12.6 → parent 统一） | 无 | S | 版本一致 |

> 工作量量级：S 小 / M 中 / L 大，不含具体工期。
