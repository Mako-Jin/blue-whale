# 蓝鲸系统 - 搭建填充路线图

> 所有阶段性规划保存在本目录，完成一项在 `docs/devlog/` 中记录一项。

## Phase 1：基础设施就绪

- [ ] 1.1 配置环境变量 — 复制 `.env.example` → `infrastructure/docker/.env`，填入 MySQL/Redis/Nacos 密码
- [ ] 1.2 启动 Docker Compose — `cd infrastructure/docker && docker compose up -d`
- [ ] 1.3 验证中间件 — Nacos (http://localhost:8848/nacos)、MySQL 11 个业务库自动建库、Redis 连通
- [ ] 1.4 验证 Nginx 配置 — 静态资源 / API 网关 / 微前端三套 conf.d 联调

## Phase 2：后端核心服务填充（按优先级）

### 2.1 whale-auth 权限中心（最高优先级）

- [x] 数据库表设计：sys_user / sys_role / sys_permission / sys_menu / sys_dept / sys_user_role / sys_role_permission（+sys_user_permission ABAC 属性表）
- [x] RBAC + ABAC 六层级模型：系统 → 菜单 → 接口 → 按钮 → 数据 → 属性
- [x] JWT 登录 / 刷新 / 注销接口
- [x] Spring Security + `@RequirePermission` 注解拦截
- [ ] 对接 admin 前端登录

### 2.2 whale-gateway 网关

- [x] Nacos 服务发现动态路由
- [x] JWT 全局鉴权过滤器
- [ ] Sentinel 限流熔断
- [x] 请求日志 / TraceId 链路追踪

### 2.3 whale-common / whale-tools 公共组件

- [x] 统一返回体 `Result<T>` / `PageResult<T>`
- [x] 全局异常处理 `GlobalExceptionHandler`
- [x] MyBatis-Plus 分页插件、字段自动填充

### 2.4 whale-i18n-sdk 国际化

- [x] 消息表 `sys_i18n_message` 设计
- [x] 三级优先级实现：数据库 > 属性文件 > 默认值
- [x] Redis 缓存（+SPI 数据源实现，14 个单测全绿）
- [ ] 消息 CRUD 接口

### 2.5 其余服务骨架填充

- [ ] whale-job（定时任务）、whale-workflow（工作流）、whale-form（表单）、whale-file（文件）、whale-pay（支付）
- [ ] whale-audit（审计日志）、whale-monitor（监控）、whale-alert（告警）

## Phase 3：前端平台填充

### 3.1 whale-platform 基座

- [x] 登录页 + 路由守卫
- [x] MainLayout（Header / Sidebar / 多标签页）
- [x] 子应用错误兜底（qiankun 加载动画待做）
- [x] 主题切换（@bluewhale/whale-common-theme，亮/暗）

### 3.2 whale-ui 组件库

- [ ] 基于 antd 二次封装：ProTable / FormModal / SearchForm
- [ ] 主题 CSS 变量注入

### 3.3 admin 后台管理

- [ ] 登录页 → 用户 / 角色 / 菜单管理
- [ ] 对接 whale-auth API
- [ ] 动态路由 + 按钮级权限渲染

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
