# P3 OA 请假闭环 + CI/部署收口检查报告（2026-10-25）

> 对应台账：[priority-execution-ledger-2026-10-25.md](../plans/priority-execution-ledger-2026-10-25.md) 序号 9（P3）
> 对应需求：REQ-FUNC-017（OA 请假闭环，本次新增）、REQ-CICD-001（镜像扩面 + coverage-gate 接 CI）
> 目标：`whale-apps` OA 域 DDL→六端点→权限种子→路由→前端页面闭环；T-CI-7 镜像扩面；coverage-gate 接入 CI。

---

## 一、链路

```
前端 whale-oa（qiankun base /oa，8103）
  → 相对路径 /oa/leave/**
  → 网关 8080（Path=/oa/** → lb://whale-oa）
  → whale-oa 9004（权限拦截器校验 sys:oa:leave:*）
  → MyBatis-Plus（租户行级插件 + oa_leave）
```

CI/部署：

```
Backend CI：./mvnw -B -ntp -Pcoverage-gate verify（单测 + JaCoCo 校验 + 打包）
Docker Deploy：matrix（18 个后端服务）+ deploy/backend/sample/Dockerfile（MODULE_PATH / SERVICE_PORT）
```

## 二、变更清单

### 后端（whale-oa）

| 文件 | 变更 |
|---|---|
| [pom.xml](../../backend/whale-apps/whale-oa/pom.xml) | 引入 `whale-spring-boot-starter`、MyBatis-Plus、MySQL 驱动、Micrometer Prometheus |
| [application.yml](../../backend/whale-apps/whale-oa/src/main/resources/application.yml) | 9004、`bluewhale_oa` 数据源、Nacos、权限头开关、`/actuator/prometheus` 暴露 |
| [db/init.sql](../../backend/whale-apps/whale-oa/src/main/resources/db/init.sql) | `oa_leave` 表（租户 + 公共字段、`uk_tenant_code`）+ 2 条种子数据 |
| [OaLeave.java](../../backend/whale-apps/whale-oa/src/main/java/com/bluewhale/oa/entity/OaLeave.java) | 实体，继承 `BaseEntity`，逻辑删除 |
| [OaLeaveMapper.java](../../backend/whale-apps/whale-oa/src/main/java/com/bluewhale/oa/mapper/OaLeaveMapper.java) | `BaseMapper<OaLeave>` |
| [OaLeaveQueryRequest.java](../../backend/whale-apps/whale-oa/src/main/java/com/bluewhale/oa/dto/OaLeaveQueryRequest.java) / [OaLeaveSaveRequest.java](../../backend/whale-apps/whale-oa/src/main/java/com/bluewhale/oa/dto/OaLeaveSaveRequest.java) / [OaLeaveStatusRequest.java](../../backend/whale-apps/whale-oa/src/main/java/com/bluewhale/oa/dto/OaLeaveStatusRequest.java) | 分页查询、保存校验（类型枚举/天数 0.5–365/日期非空）、状态变更枚举 |
| [OaLeaveVO.java](../../backend/whale-apps/whale-oa/src/main/java/com/bluewhale/oa/vo/OaLeaveVO.java) | 视图对象 |
| [OaLeaveService.java](../../backend/whale-apps/whale-oa/src/main/java/com/bluewhale/oa/service/OaLeaveService.java) / [OaLeaveServiceImpl.java](../../backend/whale-apps/whale-oa/src/main/java/com/bluewhale/oa/service/impl/OaLeaveServiceImpl.java) | 分页（单页上限 200）、详情、创建、更新、状态机、逻辑删除；单号租户内唯一、日期区间校验 |
| [OaLeaveController.java](../../backend/whale-apps/whale-oa/src/main/java/com/bluewhale/oa/controller/OaLeaveController.java) | `/oa/leave` 6 端点，`@RequirePermission` + `@AuditLog`（写操作） |
| [OaLeaveServiceImplTest.java](../../backend/whale-apps/whale-oa/src/test/java/com/bluewhale/oa/service/impl/OaLeaveServiceImplTest.java) | 6 用例：创建/重复单号/非法日期、审批通过/终态拒绝、分页映射 |

### 权限种子

| 文件 | 变更 |
|---|---|
| [whale-auth/db/init.sql](../../backend/whale-services/whale-auth/src/main/resources/db/init.sql) | 新增权限 168–171：`sys:oa:leave:list/add/edit/delete` |
| [migration-5.46-oa-leave.sql](../../backend/whale-services/whale-auth/src/main/resources/db/migration-5.46-oa-leave.sql) | `INSERT IGNORE` 幂等补齐；不自动写 `sys_role_permission`（授权由租户管理员分配） |

### 前端（whale-oa）

| 文件 | 变更 |
|---|---|
| [package.json](../../frontend/platform-apps/whale-oa/package.json) | 新增 `dayjs` |
| [api/client.ts](../../frontend/platform-apps/whale-oa/src/api/client.ts) | 恢复基座 token（`whale_platform_access_token`）+ 当前用户读取，请求自动带 Authorization |
| [api/oa.ts](../../frontend/platform-apps/whale-oa/src/api/oa.ts) | 请假 6 API + 类型（状态/类型/入参/分页） |
| [pages/List.tsx](../../frontend/platform-apps/whale-oa/src/pages/List.tsx) | 请假列表：单号/状态筛选、分页；PENDING 可编辑/通过/驳回/删除 |
| [pages/Form.tsx](../../frontend/platform-apps/whale-oa/src/pages/Form.tsx) | 新增/编辑弹窗：`RangePicker`→起止日期、类型/天数/事由/备注；新增默认申请人取当前用户 |
| [App.tsx](../../frontend/platform-apps/whale-oa/src/App.tsx) | 标题与菜单改为「请假管理 / 新增申请」，类型改 `OaLeave` |
| [main.tsx](../../frontend/platform-apps/whale-oa/src/main.tsx) | `AntdApp` 包裹，支持 `App.useApp()` 消息 |

### CI / 部署

| 文件 | 变更 |
|---|---|
| [.github/workflows/backend-ci.yml](../../.github/workflows/backend-ci.yml) | `Maven Test` + `Maven Package` 合并为 `-Pcoverage-gate verify`；新增 JaCoCo 报告上传 |
| [.github/workflows/docker-deploy.yml](../../.github/workflows/docker-deploy.yml) | 后端改为 18 服务矩阵，统一 `deploy/backend/sample/Dockerfile`（`MODULE_PATH`/`SERVICE_PORT`），前端保持独立 job |
| [.workflow/backend-ci.yml](../../.workflow/backend-ci.yml) | Gitee Go 备用流水线同步 `-Pcoverage-gate verify` |

## 三、验证记录

| 项 | 命令 | 结果 |
|---|---|---|
| whale-oa 单测 | `mvn -pl whale-apps/whale-oa test` | **通过，6 用例 0 失败** |
| whale-oa Checkstyle | `mvn -pl whale-apps/whale-oa checkstyle:check` | **0 violation** |
| 全 reactor coverage-gate | `mvn -B -ntp -Pcoverage-gate verify`（backend） | **BUILD SUCCESS**（各模块 `All coverage checks have been met`；无测试模块按 JaCoCo 规则跳过） |
| OA 前端构建 | `pnpm build`（whale-oa） | **通过**（tsc + vite，2086 modules） |
| OA 前端 lint | `pnpm lint`（whale-oa，`--max-warnings 0`） | **通过** |
| 镜像矩阵语法 | YAML 人工核对 | 18 个服务与端口对齐 `application.yml` / `prometheus.yml`；本机未构建镜像 |
| 网关 `/oa/**` | 配置核对 | 已存在 `lb://whale-oa`（台账序号 6 落地） |
| 浏览器 E2E | 未执行 | 需 MySQL/Redis/Nacos + whale-auth/whale-oa 全部启动 |

## 四、边界与残留

1. **审批人模型**：当前为「有 `sys:oa:leave:edit` 即可审批」，未区分申请人与审批人，也未接工作流引擎；如需多级审批，后续接入 `whale-workflow`。
2. **权限授权**：迁移脚本只补权限点，不写角色映射；需租户管理员在角色管理中分配，否则调用返回 403。
3. **单号规则**：由前端/调用方传入，后端只校验租户内唯一；未做自动编号。
4. **删除语义**：仅 `PENDING` 可删除且有审计；已审批单不可删除，符合「终态留痕」。
5. **前端默认申请人**：取基座缓存 `whale_platform_user` 的昵称/用户名，缓存缺失时留空由用户填写。
6. **CI 镜像矩阵**：`docker/build-push-action` 逐服务 `-am` 构建共享模块，18 个并行任务耗时与 runner 配额需在标签发布时观察；本机无 Docker，未实机构建。
7. **coverage-gate 范围**：无测试模块（`whale-config`/`whale-auth-sdk`/未填充的 `whale-apps`）由 JaCoCo 自动跳过，不构成门禁；后续补测试后自动纳入。

## 五、检查清单对照（REQ-QA-001）

| 检查项 | 结论 |
|---|---|
| 协议 / 接口安全 | 6 端点均带权限码；网关统一 JWT；无内网端点暴露 |
| 输入校验 | `@Valid` + Bean Validation（类型枚举、天数区间、字符串长度） |
| 多租户 | `BaseEntity` 租户字段 + 行级插件；单号唯一键含 `tenant_id` |
| 审计 | 创建/更新/审批/删除均 `@AuditLog(targetType="oa_leave")` |
| 魔法值 | 页码上限与状态常量已提为 `static final` |
| 命名 / 格式 | 包结构 `com.bluewhale.oa.{controller,dto,entity,mapper,service,vo}`；Checkstyle 0 |
| 前端规范 | 统一 `api/client.ts` 信封与鉴权；`App.useApp()` 消息；`--max-warnings 0` 通过 |
