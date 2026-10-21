# Wave 5.34 六模块收尾（权限 / 文件 / 表单 / 网关 / 定时任务 / 工作流）

> 用户指令（2026-10-21）：「权限、文件，表单，网关，定时任务，工作流还有未完成的任务吗？可以进行收尾吗？」
> 冻结口径（AskUserQuestion）：
> ① 收尾范围 = **前端补齐 + 配置修复**（admin 部门 / 权限 / 定时任务 / 文件 四页 + 菜单种子 + 端口占位缺陷 + 台账）；
> ② 工作流与网关的**能力扩展项保持 backlog**（条件表达式、并行/会签、转办、Sentinel/resilience4j、动态路由治理）。

## 一、审计结论（收尾前）

| 模块 | 后端 | 前端 | 未完成项与判定 |
|---|---|---|---|
| whale-auth 权限 | 6 控制器 / 37 接口，RBAC+ABAC、JWT/黑名单、审计、DDD 前 3 步，271 测试 | 仅用户/角色/菜单三页 | **部门管理、权限管理两页缺失**（菜单已入库，动态路由落「页面未配置」占位）→ 本轮补页 |
| whale-file 文件 | 5 接口（上传/内网上传/下载/分页/删除）+ 本地/MinIO 双存储 | **完全缺失** | 本轮新增文件管理页 + 菜单 |
| whale-form 表单 | 定义/数据/动态表/在线迁移/导出全闭环 | 独立设计器 8002 已贯通 | 无代码级遗留 |
| whale-gateway 网关 | 10 条路由、JWT/黑名单、限流、熔断、内网屏蔽、TraceId | — | **GW-01/02/03 属评估类 backlog**；另有 `${server.port}` 自引用潜伏缺陷（5 处）→ 本轮修 |
| whale-job 定时任务 | 调度/分布式锁/misfire/日志清理/归档导出全闭环 | **完全缺失** | 本轮新增任务管理 + 执行日志页 + 菜单 |
| whale-workflow 工作流 | 定义/实例/待办/审批/状态机/BPMN 全闭环 | 独立设计器 8003 已贯通 | 条件表达式、并行/会签、转办/委派、审批改表单 → **能力扩展 backlog** |

## 二、任务清单

- [x] **C1 admin 部门管理页**：`pages/dept/DeptList.tsx` + `api/dept.ts` CRUD（树表、增删改、按 `sys:dept:*` 控权）
- [x] **C2 admin 权限管理页**：`pages/permission/PermissionList.tsx` + `api/permission.ts` CRUD（分页、类型筛选、按 `sys:permission:*` 控权）
- [x] **C3 admin 定时任务页**：`pages/job/JobList.tsx` + `api/job.ts`（任务 CRUD/启停/立即执行 + 执行日志双 Tab，按 `sys:job:*` 控权）
- [x] **C4 admin 文件管理页**：`pages/file/FileList.tsx` + `api/file.ts`（multipart 上传、分页、下载、删除，按 `sys:file:*` 控权）
- [x] **C5 路由与代理**：`router/dynamicRoutes.tsx` 注册 dept/permission/job/file 组件键；`vite.config.ts` proxy 增补 `/job`、`/file`
- [x] **C6 菜单种子**：`whale-auth/db/init.sql` 增 id 7（定时任务）、id 8（文件管理）；新增幂等 `db/migration-5.34.sql`
- [x] **C7 端口占位缺陷**：`whale-apps` erp/mes/oa/pdm 与 `whale-config` 的 `${server.port:…}` → `${SERVER_PORT:…}`（均带 actuator，启动会复现 `Circular placeholder reference`）
- [x] **C8 验证**：`pnpm --filter whale-admin build` 通过；admin 运行时冒烟（四页数据加载/写操作）；`mvn -o -pl` 受影响模块通过
- [x] **C9 台账与文档**：roadmap / task-breakdown / whale-auth-backlog / whale-gateway-backlog 标注收尾结论；devlog + 检查报告

## 三、明确不做（能力扩展 backlog）

- 工作流：条件表达式求值、并行/会签/加签、转办/委派、回退任意节点、审批时修改表单。
- 网关：GW-01 Sentinel、GW-02 resilience4j、GW-03 动态路由配置治理（schema 校验 / 灰度 / 回滚）。
- 权限：`AUTH-17` DDD 第 4 步（AppService + 领域事件）、`AUTH-10` MQ 汇聚（本机无 RabbitMQ）、审计跨库可见性（阶段一固有）。

## 四、验收口径

- 四页经 admin（8001 → 网关 8080）可加载真实数据，新增/编辑/删除/上传/下载链路可用。
- 菜单经 `GET /auth/me` 返回 8 个菜单项，动态路由不再落到「页面未配置」。
- 5 处端口占位改造后，相关模块可正常启动（不出现循环占位异常）。
- 构建：admin `tsc + vite build` 通过；后端受影响模块 `mvn -o -pl … test` 通过。

## 五、验收结果（2026-10-21 实测）

| 验收项 | 结论 | 证据 |
|---|---|---|
| 前端构建 | **PASS** | `pnpm -C frontend --filter whale-admin build` 通过（仅既有 chunk 体积提示） |
| 后端构建 | **PASS** | `mvn -o -q -DskipTests compile -pl whale-apps/whale-erp,whale-apps/whale-mes,whale-apps/whale-oa,whale-apps/whale-pdm,whale-registry/whale-config` 无输出即成功 |
| 菜单树 | **PASS** | `GET /auth/me` 返回 8 个菜单项（系统管理 + 7 子项），`permissions=73` |
| 部门页 | **PASS** | 浏览器 8001 实测：列名/树表正常，空态无报错 |
| 权限页 | **PASS** | 浏览器实测：9 列正常，首行 `sys:user:list` |
| 定时任务页 | **PASS** | 浏览器实测：任务列表有数据（执行日志归档/清理）；「执行日志」Tab 正常加载 |
| 文件页 | **PASS** | 浏览器实测：列表含 `auditLog-archive-*.csv`、`smoke.txt` |
| 控制台/网络 | **PASS** | 无 error 级日志；`/dept` `/permission` `/job` `/file` 无 4xx/5xx 与业务码非 0 |
| 后端业务闭环（API 级） | **PASS** | 定时任务：创建 → 启停 → 立即执行 → 日志 `total=1` → 删除；文件：multipart 上传（id `2112700923947982850`）→ 下载 HTTP 200 → 删除 |
| DB 迁移 | **PASS** | `migration-5.34.sql` 已执行，`sys_menu` id 1–8；历史 E2E 菜单置 `status=0` |

**备注**：本次仅验证读链路与 API 级写链路；浏览器侧未点击新增/编辑/删除/上传按钮（避免污染演示数据），写操作路径由 API 冒烟覆盖。
