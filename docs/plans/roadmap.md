# 蓝鲸系统 - 搭建填充路线图

> 所有阶段性规划保存在本目录，完成一项在 `docs/devlog/` 中记录一项。

## Phase 1：基础设施就绪

- [ ] 1.1 配置环境变量 — 复制 `.env.example` → `infrastructure/docker/.env`，填入 MySQL/Redis/Nacos 密码
- [ ] 1.2 启动 Docker Compose — `cd infrastructure/docker && docker compose up -d`
- [ ] 1.3 验证中间件 — Nacos (http://localhost:8848/nacos)、MySQL 11 个业务库自动建库、Redis 连通
- [ ] 1.4 验证 Nginx 配置 — 静态资源 / API 网关 / 微前端三套 conf.d 联调

## Phase 2：后端核心服务填充（按优先级）

### 2.1 whale-auth 权限中心（最高优先级）

- [x] ~~数据库表设计：sys_user / sys_role / sys_permission / sys_menu / sys_dept / sys_user_role / sys_role_permission（+sys_user_permission ABAC 属性表）~~
- [x] ~~RBAC + ABAC 六层级模型：系统 → 菜单 → 接口 → 按钮 → 数据 → 属性~~
- [x] ~~JWT 登录 / 刷新 / 注销接口~~
- [x] ~~Spring Security + `@RequirePermission` 注解拦截~~
- [x] ~~接口权限注解全覆盖（31 处 `@RequirePermission`，3 → 31）+ 22 条权限点种子 + 2 菜单项（Wave 5.2，AUTH-04）~~
- [x] ~~当前用户菜单/权限接口 `GET /auth/me`（roles/permissions/menus，Wave 5.2，AUTH-21）~~
- [x] ~~对接 admin 前端登录（Wave 5.17，2026-10-19：登录/登出经网关联调通过，登出后旧 accessToken 实测 HTTP 401，黑名单端到端达成）~~

### 2.2 whale-gateway 网关

- [x] ~~Nacos 服务发现动态路由~~（**Wave 5.21 升级**：`spring.config.import=NacosRouteDefinitionRepository` 从 Nacos 配置中心动态加载路由，变更可热刷新）
- [x] ~~JWT 全局鉴权过滤器~~
- [x] ~~网关限流（Redis 计数器 + 429 `Result` 体，Wave 5.6，T5.4；Sentinel 接入转 backlog）~~
- [x] ~~网关熔断（Wave 5.21，T5.25：自研轻量熔断 `CircuitBreakerGlobalFilter`〔滑动窗口失败率 + 超时 + 半开探测〕，503 `Result` 体降级；**未引入 resilience4j**，Sentinel 接入转 backlog）~~
- [x] ~~内网接口屏蔽（Wave 5.21，T5.25：`InternalApiDenyGlobalFilter` 对 `/file/internal/**` 返回 403）~~
- [x] ~~请求日志 / TraceId 链路追踪~~

### 2.3 whale-common / whale-tools 公共组件

- [x] ~~统一返回体 `Result<T>` / `PageResult<T>`~~
- [x] ~~全局异常处理 `GlobalExceptionHandler`~~
- [x] ~~MyBatis-Plus 分页插件、字段自动填充~~

### 2.4 whale-i18n-sdk 国际化

- [x] ~~消息表 `sys_i18n_message` 设计~~
- [x] ~~三级优先级实现：数据库 > 属性文件 > 默认值~~
- [x] ~~Redis 缓存（+SPI 数据源实现，14 个单测全绿）~~
- [ ] 消息 CRUD 接口

### 2.5 其余服务骨架填充

- [x] ~~whale-job（定时任务）骨架填充~~（Wave 5.19，T5.24：依赖/配置 + `JobInfo`/`JobLog` + `JobHandler` SPI + `JobScheduler`（`ThreadPoolTaskScheduler`+`CronTrigger`）+ `/job/**` REST + 6 权限点种子 + `bluewhale_job` 建库；8082 经网关冒烟 `code=0`）
- [x] ~~whale-job 服务「继续完成」~~（Wave 5.20：调度健壮性〔实例内互斥 `JobExecutionGuard` + Redisson 分布式锁 `lock:job:execute:{jobId}` + `misfire_policy` 落地 + `JobHandlerRegistry` 存在性校验〕+ 可观测〔`JobInfoVO.nextFireTime`、`runOnce` 异步〕+ 日志清理〔`JobLogCleanupHandler` + `whale.job.log-retention-days` + 内置种子任务〕+ 单测基线 42 用例；29 模块构建 SUCCESS，8082 冒烟含分布式锁路径全绿；**归档导出至对象存储仍阻塞于 whale-file**）
- [x] ~~whale-file（文件）最小可用~~（Wave 5.21，T5.25：存储抽象 `FileStorage`+`LocalFileStorage` + `POST /file/upload`、`POST /file/internal/upload`、`GET /file/{id}/download`、`GET /file/page`、`DELETE /file/{id}` + `sys:file:*` 4 权限点种子 + 8 单测；**未接对象存储**，落地本地文件系统）
- [x] ~~whale-job 归档导出~~（Wave 5.21，T5.25：`JobLogArchiveHandler` + `jobLogArchiveClient` 导出 CSV 上传 whale-file 成功后再逻辑删除，内置种子任务 id=2；失败不删语义）
- [x] ~~whale-audit 导出与归档~~（Wave 5.21，T5.25：`POST /audit/log/export`〔`EXPORT` 元审计〕+ `AuditLogArchiver` 归档；**不删除 `audit_log`**，append-only 不变）
- [ ] whale-workflow（工作流）、whale-form（表单）、whale-pay（支付）
- [ ] whale-audit（审计日志，**四通道查询 + 元审计 + 出站/接口日志**已落地，Wave 5.19 建 `bluewhale_audit` 库并运行时验证）、whale-monitor（监控）、whale-alert（告警）

### 2.6 工程规范与分布式基础（2026-10-17 新增）

**工程规范（REQ-DOC-001/002、REQ-QA-001/002）**

- [x] ~~需求登记册：REQ 编号体系 + 变更控制七步流程 + 四条门禁规则（[register.md](../requirements/register.md)）~~
- [x] ~~代码检查清单：协议安全 12 / 代码漏洞 16 / 复杂度 9 / 魔法值 6 / 规范 14 项（[code-review-checklist.md](../checklists/code-review-checklist.md)）~~
- [x] ~~检查报告机制：每次变更产出 `docs/reviews/` 报告（首份已归档）~~
- [x] ~~CI/CD 代码质量门禁：Checkstyle 规则集 + `code-quality.yml`（报告模式）~~

**专项设计定稿（REQ-SEC-001 / REQ-DIST-001~003 / REQ-ARCH-001 / REQ-CICD-001）**

- [x] ~~审计日志方案（[audit-log.md](../design/audit-log.md)）~~
- [x] ~~分布式 ID / 分布式锁 / 其他分布式问题（[distributed.md](../design/distributed.md)）~~
- [x] ~~权限服务 DDD 架构与迁移路线（[ddd-whale-auth.md](../design/ddd-whale-auth.md)）~~
- [x] ~~CI/CD 流水线方案（[cicd.md](../design/cicd.md)）~~

**分布式能力落地（REQ-SEC-002 / REQ-DIST-002）**

- [x] ~~分布式令牌黑名单：登出后 accessToken 立即失效（Redis + SHA-256 摘要键 + 降级）~~
- [x] ~~分布式锁工具（`@DistributedLock` + SpEL + Redisson）~~
- [x] ~~审计日志落地（`@AuditLog` + AOP 异步写库 + 敏感字段脱敏）~~
- [x] ~~refreshToken 失效（`jti` + 黑名单）、网关侧黑名单校验（REQ-SEC-003/004）~~
- [x] ~~分布式 ID 统一与 Long→String 序列化~~
- [x] ~~幂等组件 `@Idempotent`（Redisson SETNX + 结果缓存，Wave 5.6，T5.3）~~
- [x] ~~缓存一致性工具（Cache-Aside + 空值哨兵 + TTL 抖动 + 延迟双删，Wave 5.6，T5.5）~~
- [x] ~~Gitee Go 流水线映射、JaCoCo 覆盖率门禁（Wave 5.6，T5.16/T5.18；Gitee 侧实机触发待验证）~~

## Phase 3：前端平台填充

### 3.1 whale-platform 基座

- [x] ~~登录页 + 路由守卫~~
- [x] ~~MainLayout（Header / Sidebar / 多标签页）~~
- [x] ~~子应用错误兜底（qiankun 加载动画待做）~~
- [x] ~~主题切换（@bluewhale/whale-common-theme，亮/暗）~~

### 3.2 whale-ui 组件库

- [x] ~~基于 antd 二次封装：ProTable / FormModal / SearchForm~~（Wave 5.18，另附 `PermissionProvider` / `AuthButton` 按钮级权限组件）
- [ ] 主题 CSS 变量注入

### 3.3 admin 后台管理

- [x] ~~登录页 → 用户 / 角色 / 菜单管理~~（登录 Wave 5.17 + 三管理页 Wave 5.18）
- [x] ~~对接 whale-auth API~~（Wave 5.18：user/role/menu/dept/permission 五模块客户端，经网关）
- [ ] 动态路由 + 按钮级权限渲染（**按钮级权限渲染已落地**：Wave 5.18 `PermissionProvider` + `AuthButton`；**动态路由待做**；后端数据源已就绪：Wave 5.2 `GET /auth/me` 返回 permissions/menus）

### 3.4 whale-portal 门户

- [ ] 工作台 / 待办 / 消息通知
- [ ] ERP / OA / MES 入口卡片

## Phase 4：业务应用与运维

- [ ] whale-erp / whale-mes / whale-pdm / whale-oa 各自列表页 + 表单页骨架
- [ ] 前后端 API 联调
- [ ] ops 运维平台：服务监控面板、审计日志查询、告警列表（对接 Prometheus）

## Phase 5：部署与 CI/CD

- [ ] Dockerfile 多阶段构建验证（后端 3 个模板 + 前端）
- [ ] GitHub Actions CI 跑通
- [ ] 生产环境 `docker-compose.prod.yml` 验证
- [ ] Nginx 生产配置 + HTTPS

---

## 里程碑顺序

```
Phase 1 ──► 2.1 auth ──► 2.2 gateway ──► 2.3 common ──► 3.1 基座 ──► 3.3 admin
                                    └──► 2.4 i18n ──► 2.5 其他服务 ──► 3.2 UI ──► 3.4 portal ──► Phase 4 ──► Phase 5
```
