# whale-service-auth 权限服务 DDD 架构设计

| 项 | 内容 |
|---|---|
| 需求编号 | REQ-ARCH-001 |
| 状态 | 已落地（2026-10-26 完成 Maven 五模块拆分） |
| 最后更新 | 2026-10-26 |
| 作者 | BlueWhale |
| 落点模块 | `backend/whale-services/whale-service-auth`（可部署子模块 `whale-service-auth-starter`，端口 8081） |

---

## 1. 现有分层现状（如实盘点）

包根 `com.bluewhale.auth`，当前为**经典三层 + 安全横切**结构：

| 包 | 内容 | 类数量（现有） |
|---|---|---|
| `controller` | `AuthController` / `UserController` / `RoleController` / `MenuController` / `PermissionController` / `DeptController` | 6 |
| `service` | 接口：`AuthService` / `SysUserService` / `SysRoleService` / `SysMenuService` / `SysPermissionService` / `SysDeptService` / `SysUserPermissionService` | 7 |
| `service.impl` | 实现：`SysUserServiceImpl` / `SysRoleServiceImpl` / `SysMenuServiceImpl` / `SysPermissionServiceImpl` / `SysDeptServiceImpl` / `SysUserPermissionServiceImpl` | 6 |
| `mapper` | `SysUserMapper` / `SysRoleMapper` / `SysMenuMapper` / `SysPermissionMapper` / `SysDeptMapper` / `SysUserRoleMapper` / `SysRolePermissionMapper` / `SysUserPermissionMapper` | 8 |
| `entity` | `SysUser` / `SysRole` / `SysMenu` / `SysPermission` / `SysDept` / `SysUserRole` / `SysRolePermission` / `SysUserPermission` | 8 |
| `dto` | 请求对象：`LoginRequest` / `RefreshRequest` / `UserCreateRequest` / `UserUpdateRequest` / `RoleCreateRequest` / `RoleUpdateRequest` / `MenuRequest` / `PermissionRequest` / `DeptRequest` / `AssignRolesRequest` / `AssignPermissionsRequest` / `UpdateAttrsRequest` / `UserAttrItem` | 13 |
| `vo` | 响应对象：`LoginResponse` / `UserVO` / `RoleVO` / `MenuVO` / `PermissionVO` / `DeptVO` / `UserAttrVO` | 7 |
| `security` | `JwtAuthenticationFilter` / `PermissionInterceptor` / `UserDetailsServiceImpl` / `LoginUser` / `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` | 6 |
| `jwt` | `JwtTokenProvider` | 1 |
| `config` | `SecurityConfig` / `JwtProperties` | 2 |
| 根 | `WhaleAuthApplication` | 1 |

### 1.1 现状问题清单

| 问题 | 具体表现 | 影响 |
|---|---|---|
| 贫血模型 | `SysUser`/`SysRole` 等仅为 `@Data` 数据载体，无行为与不变量 | 业务规则散落在 Service，易漏校验 |
| Service 直接操作 Mapper | 如 `SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>`，`save/updateById/removeById` 直连 | 领域规则与持久化耦合，难测试 |
| 跨聚合直接调用 | `SysUserServiceImpl` 直接注入 `SysRoleMapper`/`SysUserRoleMapper` 处理角色关联 | 聚合边界模糊，事务扩散 |
| 关联表"物理删除"特例 | 注释：关联表实体无 `deleted` 字段，走物理删除 | 与逻辑删除语义并存，易误用 |
| DTO/VO 双份 | 请求/响应对象逐字段手工 `from()` 转换 | 样板代码多，无统一映射 |
| 权限校验在横切层兜底 | `PermissionInterceptor` 兜底直查 DB（`listPermissionCodesByUserId`） | 领域规则外泄到框架层 |
| 无领域事件 | 权限变更无事件发布 | 无法解耦后续动作（审计、缓存刷新） |

---

## 2. 目标四层架构

```
com.bluewhale.auth
├── interfaces            # 接口层：对外契约
│   ├── controller        # REST 控制器（薄，仅编排与转换）
│   ├── assembler         # DTO <-> Command/Query <-> VO 转换
│   ├── request           # 入参 DTO
│   └── response          # 出参 VO
├── application           # 应用层：用例编排（无业务规则）
│   ├── auth/（AppService、Command、Query）
│   ├── user/ / role/ / menu/ / dept/
│   └── dto               # 应用内 DTO
├── domain                # 领域层：业务核心（不依赖框架）
│   ├── user/（aggregate/User、entity/UserRole、vo/Username、service/UserDomainService、repository/UserRepository、event/UserCreatedEvent）
│   ├── role/（aggregate/Role、vo/RoleCode、repository/RoleRepository、event/RolePermissionChangedEvent）
│   ├── resource/（aggregate/Menu、aggregate/Permission、vo/RoutePath）
│   └── org/（aggregate/Dept、vo/DeptName）
└── infrastructure        # 基础设施层：技术实现
    ├── persistence/（repository 实现、PO、mapper）
    └── external/（外部适配器：Feign/HR 同步等 ACL）
```

| 层 | 职责 | 依赖方向 | 禁止 |
|---|---|---|---|
| interfaces | 协议适配、参数校验、DTO 转换 | → application | 写业务逻辑、直连 Mapper |
| application | 用例编排、事务边界、调用领域服务 | → domain（+ 通过仓储接口） | 含业务规则、返回领域对象 |
| domain | 聚合/实体/值对象/领域服务/仓储接口/领域事件 | 仅依赖自身与 JDK | 依赖 Spring/Mapper/HTTP |
| infrastructure | 仓储实现、PO 映射、外部适配 | → domain（实现其接口） | 被动依赖（domain 不得依赖它） |

依赖规则：`interfaces → application → domain ← infrastructure`（**依赖倒置**：仓储接口在 domain，实现在 infrastructure）。

---

## 3. 限界上下文与子域划分

| 限界上下文 | 子域类型 | 职责 | 现有类归属 |
|---|---|---|---|
| Authentication 认证 | **核心域** | 登录、令牌签发/刷新、登出、认证失败处理 | `AuthService` / `JwtTokenProvider` / `JwtAuthenticationFilter` / `UserDetailsServiceImpl` |
| Authorization 授权(RBAC) | **核心域** | 角色-权限分配、权限判定、权限点管理 | `SysRole*` / `SysPermission*` / `PermissionInterceptor` |
| Resource 资源(菜单/权限点) | 通用域 | 菜单树、路由元数据、权限点定义 | `SysMenu*` / `SysPermission*` |
| Organization 组织(部门) | 支撑域 | 部门树、数据范围（ABAC 属性）依据 | `SysDept*` / `SysUserPermission*` |

上下文关系：

- Authentication → Authorization：**客户-供应商**（认证产出身份，授权消费身份）。
- Authorization → Resource：**共用内核**（权限点编码 `perm_code` 为共享标识）。
- Organization → Authorization：**防腐层**（部门树为 ABAC 数据范围输入，通过端口隔离）。
- ABAC 属性（`sys_user_permission`）横跨 Authorization 与 Organization，归入 Authorization 上下文的 `UserPermissionAttribute` 值对象。

---

## 4. 核心聚合设计

### 4.1 User 聚合

| 项 | 内容 |
|---|---|
| 聚合根 | `User`（对应 `sys_user`） |
| 不变量 | ① `username` 全局唯一且非空；② 密码必须为 BCrypt 密文；③ `status ∈ {启用, 停用}`；④ 停用用户不得签发令牌 |
| 值对象 | `Username`（格式校验）、`EncryptedPassword`（BCrypt 封装，禁止读明文）、`Phone`/`Email`（格式校验） |
| 内部实体 | `UserRoleAssignment`（对应 `sys_user_role`，聚合内维护，删除随聚合根级联） |
| 领域服务 | `UserDomainService`：用户唯一性校验（跨聚合需仓储）、密码加密策略 |
| 领域事件 | `UserCreatedEvent` / `UserDisabledEvent` / `UserRolesChangedEvent`（供审计、缓存刷新订阅） |
| 仓储接口 | `UserRepository`：`save` / `findById` / `findByUsername` / `existsByUsername` / `replaceRoles` |

### 4.2 Role 聚合

| 项 | 内容 |
|---|---|
| 聚合根 | `Role`（对应 `sys_role`） |
| 不变量 | ① `roleCode` 唯一且非空；② 内置角色 `super_admin` 不可删除；③ 停用角色不参与权限判定 |
| 值对象 | `RoleCode`（编码规范）、`RoleStatus` |
| 内部实体 | `RolePermissionGrant`（对应 `sys_role_permission`） |
| 领域服务 | `RoleDomainService`：权限授予合法性（权限点必须存在且启用） |
| 领域事件 | `RolePermissionChangedEvent`（权限变更后需刷新授权缓存） |
| 仓储接口 | `RoleRepository`：`save` / `findById` / `findByCode` / `listRoleCodesByUserId` / `replacePermissions` |

### 4.3 Menu 资源聚合（含 Permission 权限点）

| 项 | 内容 |
|---|---|
| 聚合根 | `Menu`（对应 `sys_menu`）、`Permission`（对应 `sys_permission`）分别成聚合 |
| 不变量 | ① 父节点必须存在（树完整性、无环）；② `menuType ∈ {DIR, MENU}`；③ 存在子节点时不可删除；④ `permType ∈ {SYSTEM, MENU, API, BUTTON, DATA, ATTR}`；⑤ `permCode` 唯一 |
| 值对象 | `RoutePath` / `ComponentPath` / `PermCode` / `MenuType` / `PermType` |
| 领域服务 | `MenuDomainService`：树组装与移动校验；`PermissionDomainService`：编码唯一性 |
| 领域事件 | `MenuChangedEvent`（前端动态路由缓存失效）、`PermissionChangedEvent` |
| 仓储接口 | `MenuRepository` / `PermissionRepository` |

### 4.4 Dept 聚合

| 项 | 内容 |
|---|---|
| 聚合根 | `Dept`（对应 `sys_dept`） |
| 不变量 | ① 父部门必须存在且不得形成环；② 有子部门时不可删除；③ 顶级部门 `parentId = 0` |
| 值对象 | `DeptName` / `Leader` |
| 领域服务 | `DeptDomainService`：树形变换与 ABAC 数据范围推导 |
| 领域事件 | `DeptChangedEvent`（数据范围变更，需重算用户可见数据） |
| 仓储接口 | `DeptRepository` |

> ABAC 值对象 `UserPermissionAttribute`（`attr_key`/`attr_value`，对应 `sys_user_permission`）作为 Authorization 上下文内的独立小聚合，聚合根为 `(userId, permissionId)` 组合。

---

## 5. 聚合 ↔ 现有表/实体映射

| 聚合根 | 现有表 | 现有实体 | 现有 Service（对应 AppService/领域服务） |
|---|---|---|---|
| User | `sys_user` | `SysUser` | `SysUserService(Impl)` / `AuthService(登录部分)` |
| User 内部 | `sys_user_role` | `SysUserRole` | `SysUserServiceImpl.assignRoles` |
| Role | `sys_role` | `SysRole` | `SysRoleService(Impl)` |
| Role 内部 | `sys_role_permission` | `SysRolePermission` | `SysRoleServiceImpl.assignPermissions` |
| Menu | `sys_menu` | `SysMenu` | `SysMenuService(Impl)` |
| Permission | `sys_permission` | `SysPermission` | `SysPermissionService(Impl)` |
| Dept | `sys_dept` | `SysDept` | `SysDeptService(Impl)` |
| UserAttribute | `sys_user_permission` | `SysUserPermission` | `SysUserPermissionService(Impl)` |

映射原则：**PO（持久化对象）与领域对象分离**。`infrastructure/persistence/po` 保留现有 `SysUser` 等 `@TableName` 注解类（改造为 PO），领域对象 `domain/user/aggregate/User` 为不含 MyBatis 注解的纯对象；`RepositoryImpl` 负责 PO ↔ 领域对象转换（可用 MapStruct，parent 已管理 1.5.5.Final）。

---

## 6. 防腐层与外部依赖隔离

| 外部依赖 | 现状 | 目标设计 |
|---|---|---|
| `whale-i18n-sdk` | 直接依赖 | 通过 `interfaces/application` 使用，不进 domain |
| `whale-security`（加密/JWT） | 直接依赖 | JWT 签发封装为 `domain/.../service` 端口 + infrastructure 适配器 |
| `whale-auth-sdk`（`@RequirePermission`） | 注解在 controller | 保持 interfaces 层；判定逻辑经 application 端口下探 domain |
| 未来 HR/钉钉 部门同步 | 无 | 新增 `infrastructure/external/HrDeptAcl`，映射为内部 `Dept`，domain 只依赖端口 |
| 跨服务调用（Feign） | 已引入 openfeign | 统一放 `infrastructure/external`，domain 定义端口接口 |

原则：**domain 内不得出现任何框架/HTTP/数据库注解**；所有外部能力以接口（端口）形式在 domain 声明，由 infrastructure 适配。

---

## 7. 渐进式迁移路线（不中断 API）

采用 **Strangler Fig（绞杀者）** 模式逐层替换，每步均可独立上线与回滚。

| 步骤 | 动作 | 不中断保障 | 回滚方式 |
|---|---|---|---|
| Step 0 契约冻结 | 冻结现有 REST 契约（路径/出入参），登记 REQ 编号 | 对外契约不变 | — |
| Step 1 值对象与不变量 | 引入 `Username`/`EncryptedPassword`/`RoleCode` 等 VO + 断言（`AssertX`）；Service 内先用 | 内部重构，REST 输出不变 | 直接回退代码 |
| Step 2 领域模型抽取 | 领域对象 `User`/`Role`/... 与 PO 分离，Repository 转换；AppService 内部先调用领域对象 | REST 与 Service 接口签名不变 | 保留旧 ServiceImpl 分支 |
| Step 3 仓储替换 | `domain` 定义 `UserRepository` 等接口，`infrastructure` 实现；**新旧实现并行**（feature flag `whale.auth.ddd.user.enabled`） | 开关切换，双跑比对 | 关闭开关即回旧实现 |
| Step 4 应用服务编排 | 引入 `UserAppService`/`RoleAppService`，接管原 ServiceImpl 用例与事务边界 | controller 内部指向 AppService，路径不变 | 开关回退 |
| Step 5 领域事件 + ACL | 发布 `UserRolesChangedEvent` 等，接审计/缓存；外部依赖收口到 ACL | 事件订阅方按需接入 | 关闭事件发布 |

关键约束：

- 每步保持 **REST API 路径与出入参完全不变**（qiankun 前端与网关路由已依赖 `/auth/**`、`/user/**` 等）。
- **并行新旧实现**通过配置开关（如 `whale.auth.ddd.{context}.enabled`，默认 `false`），先灰度再到默认开启。
- **回滚**：开关回退 + 保留旧 `ServiceImpl` 至迁移完成后再清理（不提前删）。
- 每步结束跑 `mvn -pl whale-services/whale-service-auth/whale-service-auth-starter -am compile` + 接口回归。

---

## 8. 迁移任务拆分

| 编号 | 任务 | 依赖 | 工作量 | 验收 |
|---|---|---|---|---|
| T-DDD-1 | 契约冻结清单（现有 6 Controller / 接口路径） | 无 | S | 清单入库，路径无变更 |
| T-DDD-2 | 值对象（`Username`/`EncryptedPassword`/`RoleCode`/`PermCode`）+ 断言 | 无 | M | 非法值构造即抛错 |
| T-DDD-3 | `domain` 包骨架与依赖规则（无框架注解） | T-DDD-2 | S | 编译期不依赖 Spring |
| T-DDD-4 | User 聚合迁移（聚合根 + 内部实体 + 仓储接口） | T-DDD-3 | L | 单测覆盖不变量 |
| T-DDD-5 | Role 聚合迁移 | T-DDD-3 | M | 同上 |
| T-DDD-6 | Menu/Permission 资源聚合迁移（树校验） | T-DDD-3 | M | 树完整性用例通过 |
| T-DDD-7 | Dept 聚合迁移（无环校验） | T-DDD-3 | M | 环检测用例通过 |
| T-DDD-8 | `infrastructure/persistence` 仓储实现 + PO 拆分 + MapStruct | T-DDD-4~7 | L | 新旧结果一致 |
| T-DDD-9 | `application` 层 AppService + Command/Query | T-DDD-8 | L | 用例编排完成 |
| T-DDD-10 | 开关并行 + 灰度切换 + 回退验证 | T-DDD-9 | M | 开关可秒级回退 |
| T-DDD-11 | 领域事件发布（对接审计/缓存） | T-DDD-9 | M | 事件可被订阅 |
| T-DDD-12 | 清理旧 ServiceImpl | T-DDD-10 稳定后 | S | 无残留引用 |

> 工作量量级：S 小 / M 中 / L 大，不含具体工期。整体工作量评估为 **L**（该模块为平台最大服务）。

---

## 9. 风险与决策记录

### 9.1 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 迁移周期长、易中途搁置 | 新旧结构长期共存 | 分步可上线，每步小闭环 |
| 未装 Docker 无法联调 | 只能编译 + 单测验证 | 前置单测，容器就绪后回归 |
| MapStruct 映射遗漏字段 | 数据丢失 | 单测比对 PO↔领域对象字段 |
| 事务边界变更引入并发问题 | 数据不一致 | 锁（REQ-DIST-002）与事务放在 application 层统一 |
| 领域事件订阅方未就绪 | 事件空转 | 事件发布与订阅解耦，可后置接入 |

### 9.2 决策记录（ADR）

| 编号 | 决策 | 备选 | 理由 |
|---|---|---|---|
| ADR-AUTH-001 | 采用 DDD 四层 + 依赖倒置 | 保持三层反射式 | 降低领域规则外泄，提升可测性 |
| ADR-AUTH-002 | 渐进式绞杀者迁移，非一次性重写 | 大爆炸重写 | 现有 RBAC 已可用，重写风险高 |
| ADR-AUTH-003 | PO 与领域对象分离 | 领域对象直接加 MyBatis 注解 | 保持 domain 纯净无框架依赖 |
| ADR-AUTH-004 | 仓储接口置于 domain，实现置 infrastructure | 接口置于 application | 依赖方向正确（内层定义契约） |
| ADR-AUTH-005 | 保留 `@RequirePermission` 注解在 interfaces 层 | 移入 domain | 属协议层关注点，与 Security 拦截器配套 |

---

## 10. 契约冻结清单（T-DDD-1，Wave 5.2 落地）

> 目的：DDD 迁移（AUTH-15/16/17）为**结构性重构**，**外部 REST 契约不得变更**。本清单为迁移期间的**冻结基线**：路径、方法、语义均不得改动；新增接口须另立需求并在此追加。现状：**6 控制器 / 35 个 REST 接口**（原 34 + Wave 5.2 新增 `/auth/me`）。对照 [whale-auth-backlog.md](../plans/whale-auth-backlog.md) §1.1 逐行核对。

| 控制器（对外一级前缀） | 冻结的方法 + 路径 | 权限码（`@RequirePermission`） |
|---|---|---|
| `AuthController`（`/auth`） | POST `/auth/login` ｜ POST `/auth/refresh` ｜ POST `/auth/logout` ｜ **GET `/auth/me`**（Wave 5.2，AUTH-21） | login/refresh/logout 为白名单；`/auth/me` 仅登录校验（无权限码） |
| `UserController`（`/user`） | GET `/user/page` ｜ GET `/user/{id}` ｜ POST `/user` ｜ PUT `/user/{id}` ｜ PUT `/user/{id}/attrs` ｜ DELETE `/user/{id}` ｜ POST `/user/{id}/roles` ｜ GET `/user/{id}/roles` ｜ GET `/user/{id}/attrs` | `sys:user:list` / `sys:user:add` / `sys:user:edit` / `sys:user:delete` / `sys:user:assign` |
| `RoleController`（`/role`） | GET `/role/page` ｜ GET `/role/{id}` ｜ POST `/role` ｜ PUT `/role/{id}` ｜ DELETE `/role/{id}` ｜ POST `/role/{id}/permissions` ｜ GET `/role/{id}/permissions` | `sys:role:list` / `sys:role:add` / `sys:role:edit` / `sys:role:delete` / `sys:role:assign` |
| `MenuController`（`/menu`） | GET `/menu/tree` ｜ GET `/menu/{id}` ｜ POST `/menu` ｜ PUT `/menu/{id}` ｜ DELETE `/menu/{id}` | `sys:menu:list` / `sys:menu:add` / `sys:menu:edit` / `sys:menu:delete` |
| `PermissionController`（`/permission`） | GET `/permission/page` ｜ GET `/permission/{id}` ｜ POST `/permission` ｜ PUT `/permission/{id}` ｜ DELETE `/permission/{id}` | `sys:permission:list` / `sys:permission:add` / `sys:permission:edit` / `sys:permission:delete` |
| `DeptController`（`/dept`） | GET `/dept/tree` ｜ GET `/dept/{id}` ｜ POST `/dept` ｜ PUT `/dept/{id}` ｜ DELETE `/dept/{id}` | `sys:dept:list` / `sys:dept:add` / `sys:dept:edit` / `sys:dept:delete` |

**冻结口径**：
1. 对外路径 = 控制器路径（REQ-ARCH-003：服务自持一级前缀，网关只转发、不 `StripPrefix`）；
2. 请求/响应体的字段名为契约一部分，迁移中**只允许新增可选字段**，不得重命名/删除；
3. HTTP 层恒 200、业务语义由 `Result.code` 承载（既有全局约定）；
4. 值对象（`Username`/`EncryptedPassword`/`RoleCode`/`PermCode`）为**领域内不变量**，其校验失败统一映射为 `ErrorCode.PARAM_ERROR`（400），不影响上述路径契约。

**值对象落地映射（T-DDD-2/T-DDD-3，Wave 5.2）**：`Username`（`^[A-Za-z0-9_.-]{1,64}$`）、`EncryptedPassword`（非空，`toString` 掩码）、`RoleCode`（`^[a-z][a-z0-9_]{1,63}$`）、`PermCode`（`^[a-z][a-z0-9]*(?::[a-z0-9]+)+$`）；四层骨架以 `package-info.java` 固化依赖规则（domain 禁 Spring/MyBatis/Web 注解）。
