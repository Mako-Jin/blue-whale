# Wave 5.22 任务分解（并行执行）

> 规格：[spec.md](spec.md)。执行原则：**先冻结契约（spec §三）→ 轨内并行 → 模块级自检 → 收尾串行全量回归**。
> 文件所有权：各轨仅改自己模块与已授权文件；跨轨文件冲突为零（A=gateway / B=frontend-admin / C=file+job+audit / D=auth）。

## 并行轨与任务

| 编号 | 任务 | 落点模块 | 依赖 | 产物 | 自检 |
|---|---|---|---|---|---|
| **A1** | GW-04 熔断 Micrometer 指标：`CircuitBreakerGlobalFilter` 注入 `ObjectProvider<MeterRegistry>`（缺省静默）；注册 `gateway.circuit.calls{route,result}` / `gateway.circuit.short_circuits{route}` / `gateway.circuit.state{route}`；状态迁移更新 Gauge | `whale-services/whale-gateway` | 无 | 1 指标类/过滤器改动 + 1 测试类 | `mvn -o -pl backend/whale-services/whale-gateway test` + `checkstyle:check` |
| **A2** | GW-05 多路由纳入熔断：`application.yml` `apply-to-routes` 扩为 `whale-auth,whale-file,whale-job,whale-audit` | 同上 | 无（与 A1 同模块，文件不重叠：A2=yml 的 circuit 段） | yml 变更 | 同 A1 |
| **A3** | GW-06 内网屏蔽覆盖：确认 `patterns` 配置化语义，补「多 pattern / 通配 / 前缀匹配」单测；文档记录「其余服务当前无内网端点，规则已就绪可配置追加」 | 同上 | 无 | 1 测试类（扩充） | 同 A1 |
| **A4** | GW-07 测试基线：随 A1/A3 扩充，保证新增过滤器/指标均有单测，合计用例数上升且 0 违规 | 同上 | A1、A3 | 测试 | 同 A1 |
| **B1** | `menus` 类型建模：`api/token.ts` 的 `AuthMeResponse.menus` 由 `unknown[]` 改为 `MenuNode[]`；`api/auth.ts` `fetchMe` 保存 `menus` | `frontend/admin` | 无 | 类型 + api | `pnpm --filter whale-admin build` |
| **B2** | 动态路由构建器：新增 `router/dynamicRoutes.tsx`（`menus → RouteObject[]` + `component` 映射 + 侧栏 items + 空回退） | 同上 | B1 | 1 文件 | 同上 |
| **B3** | 接线：`AdminLayout` 侧栏 items 改由 menus 生成；`App.tsx` 合并静态基础路由 + 动态路由（`useRoutes`）；空 menus 回退静态导航 | 同上 | B2 | 3 文件 | 同上 |
| **B4** | 浏览器端到端验证：登录后侧栏由 menus 驱动、点击跳转正确、越权/未知 component 不崩、登出后拦截 | 同上 | B3 | e2e 证据 | 浏览器 |
| **C1** | whale-file 对象存储抽象扩展：`whale.file.storage.type=local\|minio`；新增 `MinioFileStorage`（`io.minio:minio:8.5.12`）+ `MinioProperties`；`LocalFileStorage` 保留；装配按 type 选择 | `whale-services/whale-file` | 无 | 1 实现 + 1 配置 + 1 装配 + 单测 | `mvn -o -pl backend/whale-services/whale-file test` + `checkstyle:check` |
| **C2** | 归档链路运行时端到端取证（串行，收尾段）：job id=2 → CSV 上传 whale-file（file_info 落库）→ 过期 job_log 逻辑删除；audit `retention.enabled=true`+`archive-enabled=true` → 导出 CSV 且 `audit_log` 未删除 | `whale-job`、`whale-audit`、运行时 | C1 | 冒烟证据 | 运行时 |
| **D1** | 控制器层单测：`@WebMvcTest` + `@MockitoBean`，覆盖 6 控制器关键端点（成功 + 参数校验失败 + 权限注解存在性），安全过滤器放行 | `whale-services/whale-auth` | 无 | 若干测试类 | `mvn -o -pl backend/whale-services/whale-auth test` + `checkstyle:check` |
| **D2** | DDD feature flag 运行时灰度：起 auth 并置 `whale.auth.ddd.role/menu/permission/dept=true`，验证 page/tree/detail 端点与 flag=false 等价 | 同上 + 运行时 | D1 | 灰度证据 | 运行时 |
| **D3** | AUTH-10 阻塞登记：确认 pom 无 MQ 依赖 + 本机无 RabbitMQ → 台账标注阻塞，不实现 | `docs/**` | 无 | 台账 | 人工核对 |
| **E** | 收尾（串行，主执行）：全量 `mvn clean install -T 1C` → 起中间件与 5 服务冒烟（A2 熔断 503 / C2 归档 / D2 灰度）→ 检查报告 `docs/reviews/2026-10-19-wave5.22-four-tracks.md` → devlog 追加 → roadmap/task-breakdown/backlog 勾选 → commit + push origin | 全仓 | A–D | 报告 + 日志 + 提交 | 全量 BUILD SUCCESS + 冒烟证据 |

> **A1–A4 由同一执行单元串行完成**（同属 `whale-gateway`，`application.yml`/过滤器为单点所有权文件）。
> **B1–B4 由同一执行单元完成**（同属 `frontend/admin`，`App.tsx`/`AdminLayout` 互相耦合）。

## 执行波次

```
Wave 5.22-a（并行）：A（gateway） ｜ B（admin） ｜ C1（whale-file） ｜ D1（auth 单测）
Wave 5.22-b（并行）：C2（归档 e2e） ｜ D2（DDD 灰度） ｜ B4（浏览器 e2e） ｜ A2（熔断 503 取证）
Wave 5.22-c（串行）：E 收尾
```

## 完成定义（DoD）

- 各轨模块级 `test` + `checkstyle:check` 全绿（0 违规）。
- 全量 `mvn clean install -T 1C` BUILD SUCCESS；前端 `pnpm --filter whale-admin build` 通过。
- 运行时冒烟：网关多路由熔断 503、job 归档 e2e、audit 归档不删、auth DDD 灰度等价、admin 动态路由浏览器 e2e。
- `docs/reviews/` 检查报告 + `docs/devlog/2026-10-19.md` 追加 + 台账勾选 + commit & push origin（Gitee）。
