# AUTH-17 / T-DDD-9：whale-auth application 层 AppService + Command/Query

| 项 | 内容 |
|---|---|
| 关联需求 | REQ-ARCH-001（DDD 改造）、AUTH-17 ↔ T-DDD-9 |
| 设计依据 | [ddd-whale-auth.md](../../design/ddd-whale-auth.md) §7 Step 4、§8 T-DDD-9 |
| 台账 | [whale-auth-backlog.md](../../plans/whale-auth-backlog.md) 第 115 行 |
| 本轮范围 | 仅 T-DDD-9；**不含** Controller 切换（T-DDD-10）、领域事件（T-DDD-11）、删除旧实现（T-DDD-12） |
| 契约约束 | REST 路径/出入参**零变更**；旧 `ServiceImpl` 全部保留；灰度开关本轮不变（默认全 `false`） |
| 验证基线 | `whale-auth` 现有 219 用例全绿，本轮新增用例后仍全绿 |

---

## 1. 目标与验收

**目标**：把 6 个控制器承载的用例编排下沉到 `com.bluewhale.auth.application`，以 AppService + Command/Query 形式实现，事务边界统一放在 AppService；AppService 只依赖领域聚合与仓储接口，不依赖 `infrastructure`/`mapper`/`entity`。

**验收口径**（对应设计 §8 T-DDD-9「用例编排完成」）：

1. `application/{user,role,menu,permission,dept,userpermission}` 六个 AppService 覆盖现有全部管理端用例（不含 `AuthController` 登录链路，见 §7 非目标）。
2. 每个 AppService 方法均以 Command/Query 记录类入参，返回既有 VO / `PageResult`（不返回领域对象、不返回 PO）。
3. 写用例与旧实现**语义逐条对齐**：同样的校验文案、同样的业务码、同样的缓存失效点、同样的分布式锁。
4. 新增单测全部通过，`whale-auth` 全量测试 219 → 约 264 全绿。
5. 不修改任何 Controller、`Sys*ServiceImpl`、`DddMigrationProperties`、`application.yml`。

---

## 2. 关键设计决策

| 决策 | 内容 | 理由 |
|---|---|---|
| D-1 分层依赖 | AppService 仅注入 `domain/*/repository/*` 接口、`PasswordEncoder`、`PermissionCache`、`@DistributedLock`；**禁止**注入 `mapper`/`entity`/`infrastructure` | 满足 §2 依赖方向 `application → domain` |
| D-2 VO 映射 | 为 `UserVO`/`RoleVO`/`MenuVO`/`PermissionVO`/`DeptVO`/`UserAttrVO` 各新增 `from(聚合)` 静态重载，AppService 直接聚合 → VO | 避免 application 反向依赖 infrastructure 的 `PersistenceAssembler` |
| D-3 事务边界 | `@Transactional(rollbackFor = Exception.class)` 标在 AppService 的多写方法上（delete/assignRoles/assignPermissions/replaceUserAttrs） | §9.1「事务放 application 层」 |
| D-4 缓存失效 | 与旧实现逐点一致：用户启停/删除/角色变更 `evictUser`；角色删除/权限变更 `evictByRole`；权限点改删 `evictByPermission` | 灰度切换后行为不变 |
| D-5 分布式锁 | `RoleAppService.assignPermissions` 保留 `@DistributedLock(prefix="lock", biz="auth:role", key="#id", waitTime=1, leaseTime=30, failFast=true)` | 与 `SysRoleServiceImpl` 一致，T-DDD-10 接线后仍生效 |
| D-6 分页载体 | 仓储返回领域层自有 record：`UserRepository.UserPage` / 复用 `RoleRepository.RolePage` / `PermissionRepository.PermissionPage` / `OrgPage`（菜单/部门不需要分页） | 领域层不泄漏 MyBatis 类型 |
| D-7 ABAC 属性 | 新增 `domain/permission/aggregate/UserPermissionAttribute` 小聚合 + `UserPermissionRepository` + `UserPermissionRepositoryImpl` + 手写 Assembler | 设计 §4/§4.4 已明确其为 Authorization 上下文小聚合，避免该用例游离在 DDD 之外 |
| D-8 User 聚合补字段 | `User` 增加 `avatar`、`lastLoginTime` 字段并纳入 `create`/`reconstitute`/`UserPersistenceAssembler` | 否则灰度后 `UserVO.avatar/lastLoginTime` 丢失，违反 §10 契约冻结 |

---

## 3. 新增文件（application 层）

包根：`whale-backend/whale-services/whale-auth/src/main/java/com/bluewhale/auth/application/`

### 3.1 user

| 文件 | 内容 |
|---|---|
| `user/UserAppService.java` | 见 §4.1 |
| `user/command/CreateUserCommand.java` | `record CreateUserCommand(String username, String rawPassword, String nickname, String phone, String email, String avatar, Long deptId, Integer status)` |
| `user/command/UpdateUserCommand.java` | `record UpdateUserCommand(String rawPassword, String nickname, String phone, String email, String avatar, Long deptId, Integer status)` |
| `user/command/ChangeUserPasswordCommand.java` | `record ChangeUserPasswordCommand(String rawPassword)` |
| `user/command/ChangeUserStatusCommand.java` | `record ChangeUserStatusCommand(Integer status)` |
| `user/command/AssignUserRolesCommand.java` | `record AssignUserRolesCommand(List<Long> roleIds)` |
| `user/query/UserPageQuery.java` | `record UserPageQuery(long current, long size, String keyword)` |

### 3.2 role

| 文件 | 内容 |
|---|---|
| `role/RoleAppService.java` | 见 §4.2 |
| `role/command/CreateRoleCommand.java` | `record CreateRoleCommand(String roleCode, String roleName, String description, Integer sortNo, Integer status)` |
| `role/command/UpdateRoleCommand.java` | `record UpdateRoleCommand(String roleName, String description, Integer sortNo, Integer status)` |
| `role/command/AssignRolePermissionsCommand.java` | `record AssignRolePermissionsCommand(List<Long> permissionIds)` |
| `role/query/RolePageQuery.java` | `record RolePageQuery(long current, long size, String keyword)` |

### 3.3 menu

| 文件 | 内容 |
|---|---|
| `menu/MenuAppService.java` | 见 §4.3 |
| `menu/command/SaveMenuCommand.java` | `record SaveMenuCommand(Long parentId, String name, String path, String component, String icon, Integer sort, String menuType, String permissionCode, Integer visible, Integer status)` |

### 3.4 permission

| 文件 | 内容 |
|---|---|
| `permission/PermissionAppService.java` | 见 §4.4 |
| `permission/command/SavePermissionCommand.java` | `record SavePermissionCommand(Long parentId, String permCode, String permName, String permType, String path, String method, Integer sortNo, Integer status)` |
| `permission/query/PermissionPageQuery.java` | `record PermissionPageQuery(long current, long size, String keyword, String permType)` |

### 3.5 dept

| 文件 | 内容 |
|---|---|
| `dept/DeptAppService.java` | 见 §4.5 |
| `dept/command/SaveDeptCommand.java` | `record SaveDeptCommand(Long parentId, String deptName, Integer sort, String leader)` |

### 3.6 userpermission（ABAC）

| 文件 | 内容 |
|---|---|
| `userpermission/UserPermissionAppService.java` | 见 §4.6 |
| `userpermission/command/ReplaceUserAttrsCommand.java` | `record ReplaceUserAttrsCommand(List<UserAttrCommand> attrs)`，内层 `record UserAttrCommand(Long permissionId, String attrKey, String attrValue)` |

---

## 4. AppService 契约

### 4.1 `UserAppService`

依赖：`UserRepository`、`RoleRepository`、`PasswordEncoder`、`PermissionCache`（构造注入，`@Service`）。

```java
PageResult<UserVO> page(UserPageQuery query);
UserVO getDetail(Long id);
UserVO create(CreateUserCommand command);
UserVO update(Long id, UpdateUserCommand command);
void changePassword(Long id, ChangeUserPasswordCommand command);
void changeStatus(Long id, ChangeUserStatusCommand command);
void delete(Long id);                    // @Transactional
void assignRoles(Long id, AssignUserRolesCommand command); // @Transactional
List<Long> listRoleIds(Long id);
```

语义对齐 `SysUserServiceImpl`：

- `create`：`Username.of` 前置校验 → `userRepository.existsByUsername` 命中抛 `用户名已存在`（PARAM_ERROR）→ `EncryptedPassword.of(passwordEncoder.encode(rawPassword))` → `User.create(...)` → `save` → `UserVO.from(User)`。
- `update`：不存在抛 `NOT_FOUND`；`rawPassword` 非空时 `EncryptedPassword.of(encode)` 后 `changePassword`；其余字段 `changeProfile` → `save`。
- `changeStatus`：`status` 为空或非 0/1 抛 `状态值非法，仅支持 1（启用）/ 0（停用）`；成功后 `permissionCache.evictUser(id)`。
- `delete`：不存在抛 `NOT_FOUND` → `userRepository.deleteById(id)`（同一方法内清理 `sys_user_role`）→ `evictUser(id)`。
- `assignRoles`：不存在 `NOT_FOUND`；`roleIds` 非空时 `roleRepository.countByIds(roleIds) != roleIds.size()` 抛 `存在无效的角色ID`；`replaceRoles` → `evictUser`。
- `page/getDetail/listRoleIds`：只读，不做事务。

### 4.2 `RoleAppService`

依赖：`RoleRepository`、`PermissionRepository`、`PermissionCache`。

```java
PageResult<RoleVO> page(RolePageQuery query);
RoleVO getDetail(Long id);
RoleVO create(CreateRoleCommand command);
RoleVO update(Long id, UpdateRoleCommand command);
void delete(Long id);                                   // @Transactional
void assignPermissions(Long id, AssignRolePermissionsCommand command); // @Transactional + @DistributedLock
List<Long> listPermissionIds(Long id);
```

- `create`：`RoleCode.of` → 仓储唯一性：新增 `RoleRepository.existsByCode(String)`，命中抛 `角色编码已存在` → `Role.create` → `save`。
- `update`：`findById` 不存在抛 `NOT_FOUND`；依次 `rename`/`changeDescription`/`changeSortNo`（非空）/`changeStatus`（非空）→ `save`。
- `delete`：`deleteById`（内部清理 `sys_role_permission`）→ `permissionCache.evictByRole(id)`。
- `assignPermissions`：不存在 `NOT_FOUND`；`permissionIds` 非空时 `permissionRepository.countByIds != size` 抛 `存在无效的权限ID`；`roleRepository.replacePermissions` → `evictByRole`。
- `listPermissionIds` → `roleRepository.listPermissionIds`。

### 4.3 `MenuAppService`

依赖：`MenuRepository`。

```java
List<MenuVO> tree();
List<MenuVO> treeByPermissionCodes(Set<String> permissionCodes);
MenuVO getDetail(Long id);
void create(SaveMenuCommand command);
void update(Long id, SaveMenuCommand command);
void delete(Long id);
```

- `tree`：`findAll()` → `MenuVO.from(Menu)` → 按 `parentId=Menu.ROOT_PARENT_ID` 组树（保留旧实现的递归 + 排序语义）。
- `treeByPermissionCodes`：`findEnabledVisible()` → 命中权限码的菜单 + 祖先目录保留（逻辑与 `SysMenuServiceImpl#retainGrantedWithAncestors` 等价，迁入 AppService 私有方法）→ 组树。
- `update`：`findById` 不存在抛 `NOT_FOUND` → `menu.applyChanges(...)`（空白 `menuType` 默认 `MENU`、空值默认见 `Menu.create`）→ `save`。
- `delete`：`menuRepository.countChildren(id) > 0` 抛 `存在子菜单，不允许删除` → `deleteById`。

### 4.4 `PermissionAppService`

依赖：`PermissionRepository`、`PermissionCache`。

```java
PageResult<PermissionVO> page(PermissionPageQuery query);
List<String> listAllCodes();
PermissionVO getDetail(Long id);
void create(SavePermissionCommand command);
void update(Long id, SavePermissionCommand command);
void delete(Long id);
```

- `create`：`PermCode.of` → `permissionRepository.existsByPermCode(code, null)` 命中抛 `权限编码已存在` → `Permission.create` → `save`。
- `update`：不存在 `NOT_FOUND`；`existsByPermCode(code, id)` 命中抛 `权限编码已存在`；`applyChanges` → `save` → `evictByPermission(id)`。
- `delete`：`countChildren > 0` 抛 `存在子权限，不允许删除` → `deleteById` → `evictByPermission(id)`。
- `listAllCodes`：`findEnabledAll()` → `PermCode.value()` 去重。

### 4.5 `DeptAppService`

依赖：`DeptRepository`。

```java
List<DeptVO> tree();
DeptVO getDetail(Long id);
void create(SaveDeptCommand command);
void update(Long id, SaveDeptCommand command);
void delete(Long id);
```

- `delete`：`countChildren > 0` 抛 `存在子部门，不允许删除` → `deleteById`。
- `update`：`Dept.reconstitute` 后 `applyChanges(parentId, deptName, sort, leader)` → `save`。

### 4.6 `UserPermissionAppService`

依赖：`UserPermissionRepository`。

```java
List<UserAttrVO> listByUserId(Long userId);
void replaceUserAttrs(Long userId, ReplaceUserAttrsCommand command); // @Transactional
```

- `replaceUserAttrs`：先删后批量插（与旧实现一致，避免 `uk_user_perm_attr` 冲突）；空列表只删不插。

---

## 5. 领域层与基础设施层改动

### 5.1 聚合

| 文件 | 改动 |
|---|---|
| `domain/user/aggregate/User.java` | 新增 `private String avatar; private LocalDateTime lastLoginTime;`；`create(...)`/`reconstitute(...)` 增参；新增 `changeProfile(String nickname, String phone, String email, String avatar, Long deptId)`、`changePassword(EncryptedPassword)`、`changeStatus(int)`（校验 0/1） |
| `domain/menu/aggregate/Menu.java` | 新增 `applyChanges(Long parentId, String name, String path, String component, String icon, Integer sort, String menuType, String permissionCode, Integer visible, Integer status)`，内部复用既有 `change*`/`moveTo` 并补齐缺失行为 |
| `domain/permission/aggregate/Permission.java` | 新增 `applyChanges(...)`（同上） |
| `domain/dept/aggregate/Dept.java` | 新增 `applyChanges(Long parentId, String deptName, Integer sort, String leader)` |
| `domain/permission/aggregate/UserPermissionAttribute.java` | **新增**小聚合：字段 `id/userId/permissionId/attrKey/attrValue`；`create`/`reconstitute`；`attrKey` 非空校验（PARAM_ERROR） |

### 5.2 仓储接口扩展（`domain/*/repository`）

```java
// UserRepository
UserPage page(long current, long size, String keyword);
void deleteById(Long id);                 // 同时清理 sys_user_role
List<Long> listRoleIds(Long userId);
record UserPage(List<User> records, long total) {}

// RoleRepository
boolean existsByCode(String roleCode);
long countByIds(Collection<Long> ids);
void deleteById(Long id);                 // 同时清理 sys_role_permission
void replacePermissions(Long roleId, List<Long> permissionIds);
List<Long> listPermissionIds(Long roleId);

// MenuRepository
long countChildren(Long parentId);
void deleteById(Long id);

// PermissionRepository
boolean existsByPermCode(String permCode, Long excludeId);
long countByIds(Collection<Long> ids);
long countChildren(Long parentId);
void deleteById(Long id);

// DeptRepository
long countChildren(Long parentId);
void deleteById(Long id);

// UserPermissionRepository（新增）
List<UserPermissionAttribute> listByUserId(Long userId);
void replaceAll(Long userId, List<UserPermissionAttribute> attributes);
```

### 5.3 仓储实现（`infrastructure/persistence/repository`）

| 文件 | 改动 |
|---|---|
| `UserRepositoryImpl.java` | 实现 `page`（`keyword` 模糊 `username/nickname/phone`，`createTime` 降序）、`deleteById`（逻辑删除 + `sys_user_role` 物理删除）、`listRoleIds` |
| `RoleRepositoryImpl.java` | 实现 `existsByCode`、`countByIds`、`deleteById`、`replacePermissions`、`listPermissionIds` |
| `MenuRepositoryImpl.java` | 实现 `countChildren`、`deleteById` |
| `PermissionRepositoryImpl.java` | 实现 `existsByPermCode`（`excludeId` 非空时 `.ne(id)`）、`countByIds`、`countChildren`、`deleteById` |
| `DeptRepositoryImpl.java` | 实现 `countChildren`、`deleteById` |
| `UserPermissionRepositoryImpl.java` | **新增**，基于 `SysUserPermissionMapper` 实现先删后插 |
| `assembler/UserPermissionPersistenceAssembler.java` | **新增** PO ↔ `UserPermissionAttribute` |
| `assembler/UserPersistenceAssembler.java` | 补 `avatar`、`lastLoginTime` 双向映射 |

### 5.4 VO 重载（`com.bluewhale.auth.vo`）

`UserVO.from(User)`、`RoleVO.from(Role)`、`MenuVO.from(Menu)`、`PermissionVO.from(Permission)`、`DeptVO.from(Dept)`、`UserAttrVO.from(UserPermissionAttribute)` 各新增静态工厂；原 `from(SysEntity)` 保持不变。

---

## 6. 测试计划（新增约 45 用例）

| 测试类 | 用例要点 | 数量 |
|---|---|---|
| `application/user/UserAppServiceTest` | 分页、详情不存在、创建重名、创建成功、更新重加密、改密、状态非法/成功+evict、删除+evict、角色无效/成功+evict、查角色 | 11 |
| `application/role/RoleAppServiceTest` | 分页、详情不存在、创建重码/成功、更新字段、删除+evictByRole、权限无效/成功+evictByRole、查权限 | 9 |
| `application/menu/MenuAppServiceTest` | 树组装、权限码过滤+祖先保留、详情不存在、创建默认值、更新不存在/成功、删除有子节点 | 7 |
| `application/permission/PermissionAppServiceTest` | 分页、全量码去重、详情不存在、创建重码/成功、更新重码/成功+evict、删除有子节点/成功+evict | 9 |
| `application/dept/DeptAppServiceTest` | 树组装、详情不存在、创建默认值、更新、删除有子部门 | 5 |
| `application/userpermission/UserPermissionAppServiceTest` | 查询、空列表只删、非空先删后插 | 3 |
| `domain/permission/aggregate/UserPermissionAttributeTest` | 创建、非法 attrKey、重建 | 3 |
| `domain/user/aggregate/UserTest`（修改） | 补 avatar/lastLoginTime/changeProfile/changePassword/changeStatus 用例 | +4 |
| `infrastructure/persistence/assembler/UserPersistenceAssemblerTest`（修改） | 补 avatar/lastLoginTime 往返 | +2 |
| `infrastructure/persistence/repository/UserRepositoryImplTest`（修改） | 补 page/deleteById/listRoleIds | +3 |

测试一律 Mockito 纯单测，不启动 Spring 上下文，不连库。

---

## 7. 非目标（明确不做）

1. **不改 Controller**：接线与开关分流属 T-DDD-10；本轮只新增 AppService，旧 `Sys*ServiceImpl` 仍是被调用方。
2. **`AuthController` 登录/刷新/登出**：属 Authentication 核心域，涉及 JWT 与令牌黑名单，留待 AUTH-17 后续增量或独立任务。
3. **领域事件**：T-DDD-11。
4. **删除旧实现 / 改 `DddMigrationProperties` / 改 `application.yml`**：T-DDD-10/T-DDD-12。
5. 不新增 REST 接口、不改任何请求/响应字段。

---

## 8. 验证与交付

**构建/测试**

```powershell
$env:JAVA_HOME='D:\tools\installer\jdk\java21'
mvn -o -f whale-backend/pom.xml -pl whale-services/whale-auth -am test
```

通过口径：`Tests run` 总数 ≥ 264，失败 0、错误 0、跳过数不增加（除既有 `*IT` 默认跳过）。

**文档与提交**

1. `docs/devlog/2026-10-23.md` 追加「T-DDD-9」小节（范围、新增类、单测结果、残留）。
2. `docs/plans/whale-auth-backlog.md` AUTH-17 行标注「T-DDD-9 已完成 / T-DDD-10~12 待做」。
3. 提交信息：`feat(auth): T-DDD-9 落地 application 层 AppService 与 Command/Query`，推送 Gitee `origin`，并尝试 GitHub 镜像。

**回滚**：本轮无外部行为变化，直接 `git revert` 即可；不涉及数据库变更与配置变更。

---

## 9. 风险

| 风险 | 缓解 |
|---|---|
| 聚合 `applyChanges` 与旧实现默认值不一致 | 逐字段对照 `Sys*ServiceImpl#copyProperties` 写单测断言默认值 |
| User 聚合扩参破坏既有测试/装配器 | 同步更新 `UserTest`、`UserPersistenceAssemblerTest`、仓储实现，编译期暴露遗漏 |
| 新增仓储写方法绕过 `PermissionCache` | 缓存失效只放 AppService，仓储保持纯持久化 |
| ABAC 小聚合新增面 | 仅 1 聚合 + 1 仓储 + 1 实现 + 1 装配器，单测覆盖空/非空两条路径 |
