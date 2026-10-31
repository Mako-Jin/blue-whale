# tasks：whale-auth → whale-service-auth 拆分

> **状态说明（2026-10-31 复核）**：本清单是拆分执行期的过程记录，**勾选未逐项回填**；
> 拆分结果已落地——`whale-service-auth` 下 5 个子模块（application / domain / infrastructure /
> interfaces / starter）与 architecture-test 均存在，后续「认证用例下沉」由
> [2026-10-27-auth-layer-decoupling](../2026-10-27-auth-layer-decoupling/spec.md) 完成（T1–T6 全勾选）。
> **代码事实以仓库结构与构建结果为准，请勿按本文未勾选项重复施工。**

> 执行顺序严格自上而下；阶段 2 前必须完成阶段 1 编译验证。

## 阶段 1：重构（消除包依赖环）

- [ ] T1.1 `vo/UserVO.java`：删除 `SysUser` import 与 `from(SysUser)` 重载
- [ ] T1.2 `vo/RoleVO.java`：删除 `SysRole` import 与 `from(SysRole)` 重载
- [ ] T1.3 `vo/MenuVO.java`：删除 `SysMenu` import 与 `from(SysMenu)` 重载
- [ ] T1.4 `vo/DeptVO.java`：删除 `SysDept` import 与 `from(SysDept)` 重载
- [ ] T1.5 `vo/PermissionVO.java`：删除 `SysPermission` import 与 `from(SysPermission)` 重载
- [ ] T1.6 `vo/UserAttrVO.java`：删除 `SysUserPermission` import 与 `from(SysUserPermission)` 重载
- [ ] T1.7 `domain/role/repository/RoleRepository.java`：新增 `listRoleCodesByUserId`、`listPermissionCodesByUserId` 声明与 Javadoc
- [ ] T1.8 `infrastructure/.../RoleRepositoryImpl.java`：用既有方法组合实现上述 2 个方法
- [ ] T1.9 `application/role/RoleAppService.java`：两个同名方法改为委托 `roleRepository`
- [ ] T1.10 `security/UserDetailsServiceImpl.java`：`RoleAppService` → `RoleRepository`（字段 + 2 处调用）
- [ ] T1.11 `security/PermissionInterceptor.java`：`RoleAppService` → `RoleRepository`（字段 + 1 处调用）
- [ ] T1.12 验证：`mvn -q -pl whale-services/whale-auth -am -DskipTests compile`

## 阶段 2：拆分到 whale-service-auth

- [ ] T2.1 写 `whale-service-auth/pom.xml`（parent=whale-parent，packaging=pom，5 modules）
- [ ] T2.2 写 5 个子模块 pom（依赖见 spec §4.1；starter 含全量旧依赖 + boot 插件 + finalName）
- [ ] T2.3 `git mv` `src/main/java/com/bluewhale/auth/domain` → `-domain/src/main/java/com/bluewhale/auth/domain`
- [ ] T2.4 `git mv` `application`、`vo` → `-application`
- [ ] T2.5 `git mv` `infrastructure`、`entity`、`mapper`、`jwt`、`security`、`config` → `-infrastructure`
- [ ] T2.6 `git mv` `controller`、`dto`、`service`、`interfaces` → `-interfaces`
- [ ] T2.7 `git mv` `WhaleAuthApplication.java` + `src/main/resources` → `-starter`
- [ ] T2.8 `git mv` `src/test` → `-starter/src/test`（含 `test/resources/application.yml`）
- [ ] T2.9 `backend/whale-services/pom.xml`：module 替换为 `whale-service-auth`
- [ ] T2.10 验证：`mvn -q -pl whale-services/whale-service-auth -am -DskipTests compile`

## 阶段 3：运行时改名 whale-auth → whale-service-auth

- [ ] T3.1 auth `application.yml`：`spring.application.name`
- [ ] T3.2 gateway `application.yml`：2 条 route + `lb://` + `apply-to-routes`
- [ ] T3.3 gateway `CircuitBreakerProperties` 默认集合
- [ ] T3.4 gateway `PermissionHeaderGlobalFilter.AUTH_ME_URI`
- [ ] T3.5 gateway 两个测试断言
- [ ] T3.6 whale-im `UserDirectoryClient` 默认值 + `application.yml` 环境变量默认值
- [ ] T3.7 prometheus（2 处）job_name
- [ ] T3.8 CI `docker-deploy.yml` matrix 行
- [ ] T3.9 启动脚本 4 处 jar 路径 + smoke 脚本 3 处 `lb://`
- [ ] T3.10 `deploy/backend/sample/Dockerfile` 注释与 `ARG MODULE_PATH`
- [ ] T3.11 文档：`naming-conventions.md` §八 + 服务目录；`ddd-whale-auth.md` 落地标记
- [ ] T3.12 Javadoc 服务名引用（gateway/whale-security/whale-common/whale-audit/whale-im/whale-agent/whale-archive）
- [ ] T3.13 验证：`mvn -q -pl whale-services/whale-gateway,whale-services/whale-im -am -DskipTests compile`

## 阶段 4：收尾

- [ ] T4.1 `git rm -r backend/whale-services/whale-auth`
- [ ] T4.2 残留 grep 门禁：`whale-services/whale-auth`、`lb://whale-auth`、`job_name: whale-auth` 均为 0
- [ ] T4.3 `mvn -q -DskipTests install`（全量）
- [ ] T4.4 `mvn -q -pl whale-services/whale-service-auth/whale-service-auth-starter -am test`
- [ ] T4.5 确认 `target/whale-service-auth.jar` 存在且 `git status` 干净（除预期变更）
