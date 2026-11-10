# BlueWhale 工作空间全量审计报告

> 审计日期：2026-10-29 · 范围：backend / frontend / ai / infrastructure / deploy / scripts / config / CI / docs / 仓库卫生 · 方式：只读扫描，未修改任何文件

## 总体评价

工程化成熟度较高：依赖锁定（uv.lock / pnpm-lock）、覆盖率门禁、prod compose 加固、非 root 容器、零运行时产物入库、无 SQL 注入、无循环依赖、提交信息规范率 283/287。**但存在 5 个 P0 级问题、约 15 个 P1、20+ 个 P2**，集中在：支付并发竞态、前端零错误边界零测试、网关 internal 路由绕过、.gitignore 静默吞源码、CI 安全扫描空缺。

---

## P0 — 立即修复

| # | 问题 | 证据 |
|---|---|---|
| P0-1 | **支付单状态流转 check-then-act，并发下可重复扣款/重复退款**。无 @Version、无条件更新；项目已有 @DistributedLock 但未用于此 | `whale-backend/whale-services/whale-pay/.../PayOrderServiceImpl.java:133-167` |
| P0-2 | **全前端 0 个 ErrorBoundary**，任何渲染异常 = 白屏；qiankun 全局错误处理不覆盖 React 渲染错误 | `whale-frontend/platform-apps/whale-platform/src/main.tsx:12-20` |
| P0-3 | **前端测试名存实亡**：全仓仅 1 个单测文件；turbo.json 无 test 任务；CI 只跑 lint+build；9 个 Playwright e2e 在 CI 中无任何步骤 | `whale-frontend/turbo.json:1-19`、`.github/workflows/frontend-ci.yml:54-63` |
| P0-4 | **`.gitignore:74` `data/` 无前导 `/`，静默忽略任意层级 data/ 目录**——`git check-ignore` 实测吞掉 `whale-backend/.../resources/data/seed.json`、`whale-frontend/admin/src/data/mock.ts`；`.gitignore:68` `out/` 同理。git add 无任何报错 | `.gitignore:68,74` |
| P0-5 | **qiankun 子应用 entry 硬编码 localhost:8101~8110**，无 import.meta.env 生产配置；props.token 写死占位值，生产部署即失效 | `whale-frontend/platform-apps/whale-platform/src/qiankun/apps.ts:20-111` |

## P1 — 高优先级

**后端**
1. 网关 `discovery.locator.enabled: true` 的服务名前缀改写可能绕过 `InternalApiDenyGlobalFilter`（改写后路径不匹配 deny 规则），internal 接口可经网关直入（`whale-gateway/application.yml:31-34, 236-260`）
2. internal 接口服务端口直连完全无认证（`/file/internal/**` 可任意读/写存储），纵深防御缺口（`SecurityConfig.java:52-56`）
3. whale-alert webhook-token 硬编码默认值 `bluewhale-alert-token` 且无启动守卫（其他服务有 SecretPlaceholderChecker，alert 没有）

**前端**
4. token.ts 在 7 个应用逐行复制、permission.ts 3 份拷贝，登录态 key 靠注释对齐，应抽共享包
5. token 明文存 localStorage（access+refresh），XSS 可窃取，无 HttpOnly 兜底
6. 公共依赖未 external，react/antd 打进每个子应用 bundle + `prefetch:'all'` 首屏拉 8 份重复
7. ops 监控页把 Prometheus/Grafana 内网地址硬编码进构建产物，且直跳无鉴权

**基础设施 / CI**
8. dev compose 全部中间件端口绑定 0.0.0.0（MySQL/Redis/Nacos/RabbitMQ/Prometheus/Grafana），RabbitMQ 回退 guest/guest、Grafana 回退 admin（`docker-compose.yml:35~236`）
9. 种子口令 admin/123456 入 init.sql，且 29 个 smoke 脚本硬编码（`init.sql:379-380`）
10. CI 零安全扫描（无 CodeQL/依赖漏洞/Trivy/gitleaks）；无 dependabot
11. `docker-deploy.yml` 不依赖 backend-ci 通过即可推镜像到 GHCR
12. 根 `.env.example` 缺 NACOS_AUTH_TOKEN 等 compose 必需变量，按其注释操作 Nacos 起不来；与 `infrastructure/docker/.env.example` 的 MYSQL_USER 互相冲突；JWT_EXPIRE_MS/JWT_EXPIRE_HOURS 均为死变量（后端 expire-minutes:120 硬编码）

## P2 — 中低优先级（择要）

**后端**：LocalChunkUploadStore.merge() InputStream 泄漏；DistributedLockAspect 默认 failFast=false 竞争失败静默放行；全平台 Hikari 零显式配置；auth SQL 日志 StdOutImpl 打生产 stdout；大量 BeanUtils.copyProperties 应换 MapStruct；支付状态魔法字符串应枚举化；FileController.download 缺 nosniff 头。

**前端**：9 个 500+ 行巨型组件（最大 DocEditor.tsx 1133 行）；`shamefully-hoist=true`；12 处 VITE_ 环境变量无文档无 .env.example；useImData 定时器未清理；whale-common-three 僵尸包；qiankun initGlobalState 死代码。

**基础设施/脚本**：裸机 prometheus.yml 告警规则全注释不加载，与 docker 版 Nacos 指标路径不一致；4 个 dev 脚本缺 $ErrorActionPreference；23 处重复 Assert 应抽公共模块；脚本硬编码 `D:\tools\installer\...`；3 个旧 Dockerfile 与 sample 模板漂移；Grafana 仪表盘死配置未挂载。

**AI**：无 ruff/mypy 门禁；根 main.py 与 whale_ai/main.py 双入口。

**文档/仓库**
- `docs/architecture.md` 严重滞后：后端缺 6 个已交付服务、前端 3 个子应用（实际 13 个）、引用不存在的 docs/devops.md
- README 目录结构缺 `ai/`（正式服务 8092）、tools/、data/
- docs/README.md 列 6 个"待建"文档全部不存在
- 60 个逐字节重复文档（whale-auth/originals、whale-audit/originals 重复收录 specs/reviews/devlog）；wave5.35/wave5.61 编号冲突
- `.trae/` 14 个 IDE 私有文件已入库；缺 `.screenshots/` 忽略规则；缺 .gitattributes、LICENSE
- git 历史死重 16.4MB（旧分支 web/platform + docs/assets）；作者身份两名字共用同一邮箱
- 根目录 114MB 运行时残留（logs/、data/agent-index 32MB、*.log、dump.rdb）——未入库但应清理
- `tools/` 18GB 安装包/解压体位于工作区内（未入库，拖慢索引/备份）；Nacos 版本双轨（compose 2.4.0 vs 本地 3.2.4）
- README 快速开始用 `mvn` 与 mvnw 设计意图矛盾

## 已确认健康项

零运行时产物入库、HEAD 零二进制、端口文档与代码一致、无 SQL 注入、CORS 白名单、BCrypt、拦截器 fail-closed、网关清除伪造身份头、模块分层无循环依赖、无 TODO 欠账、前端依赖版本全仓一致、pem 私钥未入库。

## 建议修复顺序

1. P0-1 支付竞态（条件 update 或复用 @DistributedLock，改动最小收益最大）
2. P0-4 `.gitignore` 加根锚定 `/data/` `/out/`（一行修复，防静默丢数据）
3. P0-2 全局 ErrorBoundary 放入 whale-ui，各 main.tsx 包裹
4. P0-3 CI 加 `pnpm -r test` + Playwright job；turbo.json 加 test 任务
5. P0-5 + P1(前端4/7)：entry/token/监控地址全部走 import.meta.env
6. P1(基建8/12)：compose 端口绑 127.0.0.1、统一 .env.example
7. P1(基建10/11)：CI 加 gitleaks + CodeQL + Trivy，docker-deploy needs backend-ci
8. 其余 P2 按迭代消化
