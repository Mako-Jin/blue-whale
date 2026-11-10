# T-DDD-9 验收清单

- [x] `domain` 层聚合补强落地：`User` 含 `avatar`/`lastLoginTime` 与 `changeProfile`/`changePassword`/`changeStatus`；`Menu`/`Permission`/`Dept` 含 `applyChanges`；`UserPermissionAttribute` 小聚合存在且校验 `attrKey` 非空
- [x] 6 个仓储端口签名与 spec §5.2 一致，`UserPermissionRepository` 已新增
- [x] 5 个 `*RepositoryImpl` 新方法实现完成，`UserPermissionRepositoryImpl` 与 `UserPermissionPersistenceAssembler` 已新增，`UserPersistenceAssembler` 双向映射 `avatar`/`lastLoginTime`
- [x] 6 个 VO 均已提供 `from(聚合)` 重载，原 `from(SysEntity)` 未改动
- [x] `application` 下 6 个 AppService + 18 个 Command/Query 记录类存在，且不 import `entity`/`mapper`/`infrastructure`/`service`
- [x] 事务注解覆盖 `UserAppService.delete/assignRoles`、`RoleAppService.delete/assignPermissions`、`UserPermissionAppService.replaceUserAttrs`
- [x] `RoleAppService.assignPermissions` 保留 `@DistributedLock(prefix="lock", biz="auth:role", key="#id", waitTime=1, leaseTime=30, failFast=true)`
- [x] 缓存失效点与旧实现对齐（`evictUser` / `evictByRole` / `evictByPermission`）
- [x] Controller、旧 `Sys*ServiceImpl`、`DddMigrationProperties`、`application.yml` 零改动（git status 可证）
- [x] 新增单测 ≥ 45 项且全部通过（实际新增 117 项，`Tests run: 336, Failures: 0, Errors: 0`）
- [x] `mvn -o -f whale-backend/pom.xml -pl whale-services/whale-auth -am test` BUILD SUCCESS
- [x] `docs/devlog/2026-10-23.md` 已追加 T-DDD-9 小节（范围/变更/验证/残留）
- [x] `docs/plans/whale-auth-backlog.md` AUTH-17 行已标注「T-DDD-9 已完成 / T-DDD-10~12 待做」
