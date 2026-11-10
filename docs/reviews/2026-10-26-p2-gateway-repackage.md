# P2-8 发布前用最新配置重新打包网关 jar —— 检查报告

- 日期：2026-10-26
- 来源：待办队列 P2（发布前从已提交状态可复现构建，确保运行中网关与最新 main 一致）
- 结论：**已完成**

## 一、范围

- 核对运行中网关 jar 与最近提交的配置/代码差异；从已提交 main 状态重新打包 whale-gateway；重启 8080 并做经网关冒烟验证。

## 二、变更点

- 无代码变更（纯重新打包 + 重启）。
- 触发原因：运行中 jar（LastWriteTime 10:32:55）之后网关有提交 `2c6a663`（P0-3 修复 D：网关 `/auth/me` 回落透传 `X-Tenant-Id`）；后续 4 个提交（P2-4/5/6/7）不触网关代码，但为「从已提交状态可复现构建」仍整体重打包。

## 三、验证

1. 构建：`mvn -o -pl whale-backend/whale-services/whale-gateway -am package` BUILD SUCCESS，**84 用例全绿**（含 `NacosRouteDefinitionRepositoryTest` 16 项、`PermissionHeaderGlobalFilterTest` 等）。
2. 重启：停旧进程（pid 2764），以最新 jar 启动（注入 `MYSQL_PASSWORD` / `SPRING_DATA_REDIS_PASSWORD` / `SPRING_CLOUD_NACOS_USERNAME` / `SPRING_CLOUD_NACOS_PASSWORD` 环境变量），`/actuator/health` 轮询 **UP**。
3. 经网关冒烟（8080）：
   - `POST /auth/login` admin：code=0，token 280 字符；
   - `GET /auth/me`：code=0，username=admin，permissions=142（JwtAuthGlobalFilter → PermissionHeaderGlobalFilter 注入链正常）；
   - `GET /pm/project/page?current=1&size=1`：code=0，total=1（`lb://whale-pm` 路由 + Nacos 发现正常）。

## 四、风险与观察

- 网关重启瞬间存在秒级不可用窗口，属预期；PM 审批开启态环境（PM_APPROVAL_DEFINITION_ID=2114553480332849153）不受影响（PM 进程未动）。
- 无新增观察项。
