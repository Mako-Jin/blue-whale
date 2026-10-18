# Tasks：Wave 5.8 whale-auth 权限判定缓存场景接入（AUTH-20）

> 关联 spec：[spec.md](./spec.md) ｜ 关联需求：REQ-DIST-003、REQ-FUNC-002、REQ-QA-002
> 执行顺序即下表顺序；每项完成后按 REQ-QA-002 留痕，未通过构建不得提交。

| # | 任务 | 依赖 | 优先级 | 验收 | 状态 |
|---|---|---|---|---|---|
| [x] S1 | 文档先行：spec/tasks/checklist 落地 | 无 | 高 | 文档一致可追溯 | ✅ 已完成（含实测证据） |
| [x] S2 | 新增 `PermissionCacheProperties`（`whale.auth.permission-cache.enabled/ttl`）并按其既有 `@ConfigurationProperties` 风格注册 | S1 | 高 | 配置可外部覆盖；默认 true / 5m | ✅ 已完成（含实测证据） |
| [x] S3 | 新增 `PermissionCache`（`getPermissionCodes` 读 + `evictUser/evictUsers/evictByRole/evictByPermission` 写 + `afterCommit` 包装） | S2 | 高 | 不注入 `SysRoleService`（避免循环依赖）；不做权限码查询逻辑复制 | ✅ 已完成（含实测证据） |
| [x] S4 | `PermissionInterceptor` 读路径改为经 `PermissionCache`（loader 传 `roleService::listPermissionCodesByUserId`） | S3 | 高 | 放行/拒绝结论与直查一致 | ✅ 已完成（含实测证据） |
| [x] S5 | 失效接入：`SysRoleServiceImpl#assignPermissions` / `#delete` → `evictByRole` | S3 | 高 | 角色权限/角色删除后受影响用户缓存失效 | ✅ 已完成（含实测证据） |
| [x] S6 | 失效接入：`SysUserServiceImpl#assignRoles` / `#delete` / `#changeStatus` → `evictUser` | S3 | 高 | 用户角色/删除/启停后该用户缓存失效 | ✅ 已完成（含实测证据） |
| [x] S7 | 失效接入：`SysPermissionServiceImpl#update` / `#delete` → `evictByPermission` | S3 | 高 | 权限点变更后持有点位的用户缓存失效 | ✅ 已完成（含实测证据） |
| [x] S8 | 新增 `PermissionCacheTest`（命中/回源/空结果/`enabled=false`/降级/evict 各分支/`afterCommit`） | S3–S7 | 高 | 新增用例全绿 | ✅ 已完成（含实测证据） |
| [x] S9 | 改造既有测试：`PermissionInterceptorTest`、`SysRoleServiceImplTest`、`SysUserServiceImplTest`、`SysPermissionServiceImplTest` 构造与新依赖断言 | S4–S7 | 高 | 既有用例不回归且覆盖新增失效调用 | ✅ 已完成（含实测证据） |
| [x] S10 | 构建验证：`mvn -f backend/pom.xml -pl :whale-auth -am -Pcoverage-gate clean verify` | S8、S9 | 高 | 全绿 + LINE ≥ 0.40、BRANCH ≥ 0.30 | ✅ 已完成（含实测证据） |
| [x] S11 | 规范门禁：`mvn -f backend/pom.xml checkstyle:check` | S10 | 高 | 全 reactor 0 违规 | ✅ 已完成（含实测证据） |
| [x] S12 | 留痕收口：检查报告 `docs/reviews/2026-10-18-wave5.8-auth-permission-cache.md` + devlog §28 + §三新增问题项 | S10、S11 | 中 | 四类信息齐备 | ✅ 已完成（含实测证据） |
| [x] S13 | 台账销账：`whale-auth-backlog.md` AUTH-20、`next-steps.md`（T5.5 场景接入） | S12 | 中 | 删除线 + ✅ | ✅ 已完成（含实测证据） |
| [ ] S14 | 自动 commit + push（Gitee origin/main） | S13 | 中 | 工作区干净且 `main == origin/main` | ⬜ 待做 |

## 实施注意（给实现者的事实约束）

- 测试构造方式：`@ExtendWith(MockitoExtension.class)` + `new XxxServiceImpl(依赖...)` + `ReflectionTestUtils.setField(service, "baseMapper", mapperMock)`；**本波新增构造参数后，既有 3 个 `ServiceImplTest` 的 `new` 语句必须同步补参数**，否则编译失败。
- 涉及 `LambdaQueryWrapper#in` 的单测需 `@BeforeAll` 注册 `TableInfo`：`TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), 实体.class)`，否则报 `can not find lambda cache for this entity`（Wave 5.7 已踩坑）。
- `PermissionCache` 读路径的 DB 查询**必须由调用方以 `Supplier<List<String>>` 传入**，不得在 `PermissionCache` 内注入 `SysRoleService`（否则 `SysRoleServiceImpl → PermissionCache → SysRoleService` 循环依赖，Boot 默认拒绝启动）。
- `CacheConsistencyTemplate#getOrLoad(name, id, ttl, loader)` 的 `loader` 返回 `null` 时写空哨兵；空集合请返回**空串**（不要返回 `null`），读回空串即空集合。
- 事务提交后失效：`TransactionSynchronizationManager.isSynchronizationActive()` 为真时注册 `TransactionSynchronization#afterCommit`，否则立即执行；单测可用 `TransactionSynchronizationManager.initSynchronization()` / `clearSynchronization()` 构造真实同步状态。
- 缓存名常量：`PERMISSION_CACHE_NAME = "auth:user:perm"` → Key `whale:cache:auth:user:perm:{userId}`（REQ-ARCH-004）。

## 待做项（本次明确不做，登记备查）
- 击穿防护（同 Key 并发仅一次回源）→ AUTH-19（`@DistributedLock` 场景接入）
- 多实例缓存失效广播 / Canal 订阅 binlog（规模化后演进，[distributed.md](../../design/distributed.md) §3.2）
- `SysRoleServiceImpl#update` 的失效：当前判定逻辑不按角色状态过滤，暂不失效；若判定逻辑变更需同步补齐
- 经网关端到端实测（依赖中间件联调窗口）
- 控制器层单测、Mapper XML/SQL 覆盖
