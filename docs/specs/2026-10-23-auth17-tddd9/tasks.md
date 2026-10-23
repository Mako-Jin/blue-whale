# T-DDD-9 任务清单（AUTH-17 第 1 子步骤）

> 说明：本清单只覆盖 T-DDD-9（application 层 AppService + Command/Query）。
> 不在本轮：Controller 接线（T-DDD-10）、领域事件（T-DDD-11）、删除旧 `ServiceImpl`（T-DDD-12）。

## 执行顺序

- [x] **T9-1 领域补强**
  - `User` 增 `avatar`/`lastLoginTime` 与 `changeProfile`/`changePassword`/`changeStatus`
  - `Menu`/`Permission`/`Dept` 增 `applyChanges(...)`
  - 新增 `UserPermissionAttribute` 小聚合
  - 验收：`domain` 包编译通过，聚合单测覆盖新行为

- [x] **T9-2 仓储接口扩展**
  - 扩展 `UserRepository`/`RoleRepository`/`MenuRepository`/`PermissionRepository`/`DeptRepository`
  - 新增 `UserPermissionRepository`
  - 验收：接口方法签名与 spec §5.2 完全一致

- [x] **T9-3 仓储实现与装配器**
  - 补齐 5 个 `*RepositoryImpl` 新方法；新增 `UserPermissionRepositoryImpl`
  - 新增 `UserPermissionPersistenceAssembler`；`UserPersistenceAssembler` 补 avatar/lastLoginTime
  - 验收：`whale-auth` 主源码编译通过

- [x] **T9-4 VO 聚合工厂**
  - `UserVO`/`RoleVO`/`MenuVO`/`PermissionVO`/`DeptVO`/`UserAttrVO` 新增 `from(聚合)` 重载
  - 验收：字段与既有 `from(SysEntity)` 逐字段一致（编译期 + 单测断言）

- [x] **T9-5 user 应用服务**
  - `UserAppService` + 6 个 Command/Query 记录类
  - 验收：9 个方法语义对齐 `SysUserServiceImpl`（文案、业务码、缓存失效点）

- [x] **T9-6 role / menu / permission / dept 应用服务**
  - 4 个 AppService + 各自 Command/Query
  - `assignPermissions` 保留 `@DistributedLock`
  - 验收：写路径默认值、子节点校验文案与旧实现一致

- [x] **T9-7 ABAC 应用服务**
  - `UserPermissionAppService` + `ReplaceUserAttrsCommand`
  - 验收：空列表只删、非空先删后插，`@Transactional`

- [x] **T9-8 单测**
  - 新增 6 个 AppService 测试类 + `UserPermissionAttributeTest`
  - 修改 `UserTest`、`UserPersistenceAssemblerTest`、`UserRepositoryImplTest`
  - 验收：新增 ≥ 45 用例，全绿

- [ ] **T9-9 全量验证与文档**
  - `mvn -o -f backend/pom.xml -pl whale-services/whale-auth -am test` 全绿（≥ 264 用例）
  - 追加 `docs/devlog/2026-10-23.md`；更新 backlog AUTH-17 行
  - 提交并推送 Gitee，尝试 GitHub 镜像
  - 验收：测试报告 0 失败；文档含范围/变更/验证/残留

## 完成定义（DoD）

1. 6 个 AppService 覆盖各自上下文全部用例，且不依赖 infrastructure/mapper/entity。
2. `whale-auth` 全量单测全绿，基线 219 用例无回归。
3. Controller、`Sys*ServiceImpl`、`DddMigrationProperties`、`application.yml` 零改动。
4. 开发日志与 backlog 已更新，提交已推送 origin。
