# 权限服务包结构收敛方案（保持四层模块）

## 0. 文档信息

| 项 | 内容 |
|---|---|
| 状态 | Phase 1–4 已执行（2026-11-10）；Phase 5 待评审 |
| 范围 | `whale-backend/whale-services/whale-service-auth` |
| 决策 | **保持 4 个分层 Maven 模块，按技术维度分层；不拆分垂直切片模块** |
| 目标 | 消除模块内"双轨包"，让分层规则真正可执行 |
| 关联 | [ddd-whale-auth.md](ddd-whale-auth.md) §5 目标结构、[backend.md](../backend.md) 模块结构、[naming-conventions.md](../naming-conventions.md) 包命名 |

### 执行记录

| 阶段 | 结果 | 移动文件 | 验证 |
|---|---|---|---|
| Phase 1 | `controller/dto/web → interfaces.*` | 33 | `test-compile` 通过 |
| Phase 2 | `vo → application.vo` | 16 | `test-compile` 通过 |
| Phase 3a | `entity/mapper → infrastructure.persistence.*` | 25 | `test-compile` 通过 |
| Phase 3b | `security/jwt/config/bootstrap → infrastructure.*` | 23 | `test-compile` 通过 |
| Phase 4 | 5 条 ArchUnit 规则（含根包白名单） | 0 | `mvn test` 全绿 |

执行中发现的额外问题：`infrastructure/security` 包中原已存在 `PasswordHasherAdapter`，
首次移动产生 `security/security` 嵌套目录，已扁平化并由 Checkstyle 的 `PackageDeclaration`
规则拦截。后续服务迁移时须先探测目标包是否已存在。

> 根包白名单需排除 `com.bluewhale.auth.sdk..`：该包属于独立的 `whale-auth-sdk` 构件，
> 不在本服务分层范围内。

## 1. 决策

### 1.1 目标结构

```
whale-service-auth/                 # 服务根
├── whale-service-auth-domain/          # 模块 1：领域层
├── whale-service-auth-application/     # 模块 2：应用层
├── whale-service-auth-infrastructure/  # 模块 3：基础设施层
├── whale-service-auth-interfaces/      # 模块 4：接口层
├── whale-service-auth-starter/         # 装配与启动（聚合四层）
└── whale-service-auth-architecture-test/  # ArchUnit 守护（仅测试）
```

- **模块轴 = 技术维度**：`domain / application / infrastructure / interfaces` 四个 Maven 模块，数量固定，不随聚合增长。
- **层内分包 = 业务子域**：`domain.user`、`application.user`、`interfaces.controller` 等。子域只以**包**表达，**不升格为模块**。
- 同一聚合在各层使用**同名子包**，例如 `domain.user` / `application.user` / `infrastructure.persistence.repository`（类名带 `User` 前缀）。这样 IDE 中键入 `User` 即可同时检索到 `User`、`UserAppService`、`UserRepositoryImpl`、`UserController`，在不增加模块的前提下获得"聚合完整视图"。

### 1.2 明确不做

| 不做 | 原因 |
|---|---|
| 不合并成单模块 | 保留编译期模块边界；多数服务已是单模块，本服务改动面最大，合并收益不足以抵消一次性风险 |
| 不按聚合拆 6 个 Maven 模块 | 6 个聚合同属一个 IAM 限界上下文，拆模块会产生 `Session→User/Role/Menu/Permission/Tenant`、`Role→Permission` 的跨模块依赖，需要额外内部 API 模块，复杂度更高 |
| 不改运行行为 | 纯包移动，不动 URL、DB、Redis Key、MQ、Cookie、配置项 |
| 不调整聚合边界 | 架构收敛与领域建模分开评审 |

### 1.3 为什么现在必须做

[ddd-whale-auth.md](ddd-whale-auth.md) §5 早已把 `infrastructure/persistence/`（含 PO 与 mapper）定为目标，但代码只**新增**了 `com.bluewhale.auth.infrastructure.*`，**没有搬迁**旧的 `config/entity/jwt/mapper/security`。结果同一模块内两套包方案并存，且 ArchUnit 只覆盖新包名，旧包等于规则盲区。

## 2. 现状基线

### 2.1 模块规模

| 模块 | Java 文件数 | 说明 |
|---|---|---|
| `-domain` | 49 | 包结构已达标（仅 `com.bluewhale.auth.domain..`） |
| `-application` | 75 | 其中 16 个在 `com.bluewhale.auth.vo`，与 `application` 并列 |
| `-infrastructure` | 86 | 其中 46 个在旧根包，40 个在 `infrastructure.*` |
| `-interfaces` | 34 | 其中 33 个在旧根包，1 个 `interfaces/package-info.java` |
| `-starter` | 68 | main 3 + test 65 |
| `-architecture-test` | 1 | ArchUnit 规则 |
| **合计** | **313** | 预计移动 **95** 个生产文件 |

### 2.2 双轨包清单

| 模块 | 旧包（根级） | 文件数 | 目标包 |
|---|---|---|---|
| infrastructure | `com.bluewhale.auth.entity` | 13 | `…infrastructure.persistence.entity` |
| infrastructure | `com.bluewhale.auth.mapper` | 12 | `…infrastructure.persistence.mapper` |
| infrastructure | `com.bluewhale.auth.security` | 14 | `…infrastructure.security` |
| infrastructure | `com.bluewhale.auth.jwt` | 1 | `…infrastructure.jwt` |
| infrastructure | `com.bluewhale.auth.config` | 6 | `…infrastructure.config` |
| application | `com.bluewhale.auth.vo` | 16 | `…application.vo` |
| interfaces | `com.bluewhale.auth.controller` | 10 | `…interfaces.controller` |
| interfaces | `com.bluewhale.auth.dto` | 22 | `…interfaces.dto` |
| interfaces | `com.bluewhale.auth.web` | 1 | `…interfaces.web` |
| starter | `com.bluewhale.auth.bootstrap` | 2 | `…infrastructure.bootstrap` |

### 2.3 依赖事实（已扫描确认）

| 检查项 | 结果 | 对方案的影响 |
|---|---|---|
| `domain` 是否 import 旧根包 | 无 | 领域层不动 |
| `application` 是否 import `…entity/mapper/security/jwt/config` | 无 | 应用层只需改自身 `vo` |
| `interfaces` 是否 import `…security/jwt/config` | 无 | 控制器只依赖应用层与自身 `dto` |
| 旧包引用是否越出本服务 | 无（97 个文件全部在 auth 内） | 移动不产生跨服务破坏 |
| `@MapperScan` / `@Mapper` | 均未使用 | 依赖 MyBatis-Plus 对启动类所在包 `com.bluewhale.auth` 的自动扫描；新包仍在该根下，扫描范围不变 |
| `@SpringBootApplication` | `com.bluewhale.auth.WhaleAuthApplication` | 组件扫描覆盖全部子包，包移动安全 |
| `@ConfigurationPropertiesScan` | 无 `basePackages`，默认启动类所在包 | 配置类移到 `infrastructure.config` 后仍被扫描 |
| ArchUnit 扫描范围 | `com.bluewhale.auth` + 排除测试类 | 迁移后规则自动覆盖新包 |

### 2.4 现有 ArchUnit 的盲区

当前三条规则只禁止 `com.bluewhale.auth.infrastructure..`、`controller..`、`dto..`、`vo..` 与若干第三方包，
**没有禁止** `com.bluewhale.auth.entity..`、`mapper..`、`security..`、`jwt..`、`config..`、`web..`。

因此"分层守住了"目前是**巧合**（恰好没人写违规 import），不是**保证**。

## 3. 目标包结构

### 3.1 包树

```
com.bluewhale.auth
├── WhaleAuthApplication                 # 唯一允许留在根包的类
├── domain
│   ├── user/{aggregate,entity,repository,event}
│   ├── role/{aggregate,entity,repository,event}
│   ├── menu/{aggregate,repository,event}
│   ├── permission/{aggregate,repository,event}
│   ├── dept/{aggregate,repository,event}
│   ├── tenant/{aggregate,repository,event}
│   ├── model/vo
│   └── event
├── application
│   ├── auth          # SessionAppService、端口
│   ├── user          # UserAppService、UserDirectoryAppService、UserLifecycleAppService、password/*
│   ├── userpermission
│   ├── role / menu / permission / dept / tenant
│   └── vo            # 16 个视图对象
├── infrastructure
│   ├── persistence
│   │   ├── entity      # Sys* 持久化对象
│   │   ├── mapper      # MyBatis-Plus Mapper
│   │   ├── assembler   # 领域对象 ↔ PO
│   │   └── repository  # 仓储与查询端口实现
│   ├── adapter/{auth,user,tenant,password}
│   ├── security        # JWT 过滤器、权限拦截器、Redis 存储、UserDetails
│   ├── jwt             # JwtTokenProvider
│   ├── config          # SecurityConfig、*Properties、启动校验
│   ├── event           # 领域事件监听
│   ├── cache           # 权限缓存、广播、重对齐任务
│   ├── mail / password # 邮箱开通、口令哈希
│   └── bootstrap       # 管理员口令初始化（原 starter bootstrap）
└── interfaces
    ├── controller
    ├── dto
    └── web
```

### 3.2 各包职责与边界

| 包 | 只放什么 | 禁止出现 |
|---|---|---|
| `domain.*` | 聚合、实体、值对象、领域事件、仓储接口、领域服务 | Spring / MyBatis / Jakarta 注解，任何外层类型 |
| `application.*` | AppService、命令/查询、出站端口、VO | 仓储实现、Mapper、PO、Controller、`@Transactional` 之外的持久化细节 |
| `infrastructure.persistence.{entity,mapper}` | PO 与 Mapper | 领域对象、业务规则 |
| `infrastructure.persistence.assembler` | 领域 ↔ PO 转换 | 业务判断 |
| `infrastructure.persistence.repository` | 仓储接口实现 | 聚合状态变更编排 |
| `infrastructure.security/jwt/config` | 认证鉴权技术实现 | 业务规则、用例编排 |
| `interfaces.controller/dto/web` | HTTP 适配、请求校验、Cookie | 仓储、PO、领域对象 |

### 3.3 依赖矩阵

| from ↓ / to → | domain | application | infrastructure | interfaces |
|---|---|---|---|---|
| **domain** | — | ✗ | ✗ | ✗ |
| **application** | ✓ | — | ✗ | ✗ |
| **infrastructure** | ✓ | ✓ | — | ✗ |
| **interfaces** | ✗ | ✓ | ✗ | — |

`starter` 依赖四层且只做装配；`architecture-test` 以 `test` 作用域依赖四层。

### 3.4 领域仓储与应用端口

Repository 来自 Evans DDD，是聚合持久化的唯一入口，**必选**；Ports & Adapters 来自六边形架构，
用于倒置技术依赖，**可选**。两者不是同一类东西，混用是"层级乱"的主要来源。

判定规则：

```
1. 以聚合根 ID 为入口、读写聚合状态        → 领域仓储  domain.<sub>.repository
2. 纯技术能力、业务语义为零、第三方或 IO    → 应用端口  application.<sub>.port
3. 跨聚合只读、直接产出 UI 视图            → 查询端口  application.<sub>.port（返回 VO/record）
4. 查询端口只读：只声明查询方法，禁止出现写方法；方法必须有返回值，禁止 void
```

规则 4 是防止"查询端口长成第二个仓储"的关键：一旦出现 `markNoticed`、`save` 这类方法，说明它已经
在承担聚合状态变更，必须退回领域仓储，而不是继续堆在 `*Query` 上。

命名铁律（由 ArchUnit 固化，不靠人工 review）：

| 后缀 | 只允许出现在 |
|---|---|
| `*Repository` | `domain` |
| `*Port` / `*Gateway` | `application` |
| `*Query` | `application`（且只读） |
| `*Adapter` | `infrastructure` |

规则必须写成"**任何以该后缀命名的类都只能落在某个包**"，而不是"某个包里的类不能以该后缀结尾"——
后者只约束了一个方向，挡不住 infrastructure 里新造一个 `*Port`。

```java
@ArchTest
static final ArchRule repository_belongs_to_domain = classes()
        .that().haveSimpleNameEndingWith("Repository")
        .should().resideInAPackage("com.bluewhale.auth.domain..");

@ArchTest
static final ArchRule port_belongs_to_application = classes()
        .that().haveSimpleNameEndingWith("Port")
        .should().resideInAPackage("com.bluewhale.auth.application..");

@ArchTest
static final ArchRule query_belongs_to_application = classes()
        .that().haveSimpleNameEndingWith("Query")
        .should().resideInAPackage("com.bluewhale.auth.application..");

@ArchTest
static final ArchRule adapter_belongs_to_infrastructure = classes()
        .that().haveSimpleNameEndingWith("Adapter")
        .should().resideInAPackage("com.bluewhale.auth.infrastructure..");

@ArchTest
static final ArchRule query_ports_are_read_only = methods()
        .that().areDeclaredInClassesThat().resideInAPackage("com.bluewhale.auth.application..")
        .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Query")
        .should().haveNameNotMatching(
                "^(save|create|update|delete|remove|insert|upsert|mark|publish|send|write|revoke|reset|bind|assign).*$")
        .andShould().notHaveRawReturnType(void.class);
```

> `query_ports_are_read_only` 依赖方法命名与返回类型，属于可机械校验的近似约束；
> "实现里有没有偷偷写库"仍需由 `*QueryImpl` 只注入只读 Mapper 方法这一约定配合 review。
> 该规则在 Phase 5c 启用——存量 `PasswordExpiryQuery.markNoticed` 会先触发失败，这是预期行为。

### 3.5 现有 11 个端口的处置（标准 DDD 判定，无"可选"）

| 端口 | 性质 | 处置 | 归属 |
|---|---|---|---|
| `TokenIssuer` | 技术能力（JWT） | 保留应用端口 | `application.auth.port` |
| `TokenBlacklist` | 技术能力（Redis） | 保留应用端口 | `application.auth.port` |
| `RefreshTokenRotationStore` | 技术能力（Redis） | 保留应用端口 | `application.auth.port` |
| `UserAccessRevocationPort` | 技术能力（Redis） | 保留应用端口 | `application.auth.port` |
| `CredentialAuthenticator` | 技术能力（Spring Security） | 保留应用端口 | `application.auth.port` |
| `PasswordHasher` | 技术能力（BCrypt） | 保留应用端口 | `application.user.port` |
| `UserSessionQuery` | 跨聚合只读投影 | 保留查询端口，只读 | `application.auth.port` |
| `CurrentUserProvider` | 运行时上下文 | **删除**：降级为 `interfaces.web.CurrentUserResolver`；用例签名显式接收 `operatorId` | `interfaces.web` + 用例入参 |
| `TenantStatusSyncPort` | 缓存同步 | **删除**：并入 `TenantRepository`，由仓储实现在 `save` 后同步网关缓存 | `domain.tenant.repository` |
| `PasswordExpiryQuery` | 查询 + 写聚合状态混合 | **拆分**：`findCandidates` 保留为查询端口；`markNoticed` 删除，改经 `UserRepository` 加载聚合 | `application.user.port` + `domain.user.repository` |
| `TenantPasswordPolicyRepository` | 仓储（且领域概念错放在 application） | **迁回 domain**：`TenantPasswordPolicy` → `domain.tenant.aggregate`，仓储 → `domain.tenant.repository` | `domain.tenant` |

收敛结果：**6 个技术端口 + 2 个查询端口 + 领域仓储**，不引入 CQRS 读写分离。

两条硬约束：

- `CurrentUserProvider` 删除后，`SessionAppService.me/switchTenant/listTenants` 与
  `PasswordAppService.changeOwnPassword` 全部改为显式 `operatorId` 入参，由控制器用
  `CurrentUserResolver.requireUserId()` 解析后传入。应用层不再有任何"隐式当前用户"。
- `TenantStatusSyncPort` 删除后，租户状态/续期的缓存同步由 `TenantRepository` 实现负责，
  应用层不再显式调用同步；缓存写入失败不得影响聚合保存结果。

**不能砍到零**：`TokenIssuer`、`TokenBlacklist`、`RefreshTokenRotationStore`、`CredentialAuthenticator`、
`PasswordHasher` 必然依赖 JWT 库、Redis、Spring Security、BCrypt，放进 domain 就是硬依赖。

### 3.6 PasswordHistory 的归属判定

**结论：`PasswordHistory` 是 User 聚合内的子实体，不是独立聚合。**

| 判定依据 | 事实 |
|---|---|
| 有独立生命周期吗 | 没有。用户删除即消失，改密即追加/裁剪 |
| 有独立访问路径吗 | 没有。唯一入口是"改密" |
| 数据量 | 上限 5 条（`history-size`），随用户加载成本可忽略 |
| 不变量归属 | "最近 N 次不可复用"只在 User 改密时成立，是 User 的不变量 |
| 现状问题 | 历史通过独立 `PasswordHistoryRepository` 访问，复用校验写在应用层 `PasswordPolicyService`，领域规则泄漏到应用层 |

目标模型：

```
User（聚合根）
├── password / passwordUpdatedTime / passwordNoticeTime / mustChangePassword
└── List<PasswordHistoryEntry>（子实体：encodedPassword、changedAt）
    ├── changePassword(EncodedPassword newPwd, boolean matchesHistory, LocalDateTime at)
    │     ① matchesHistory == true → DomainException("不得复用最近 N 次口令")
    │     ② 替换密文、刷新 passwordUpdatedTime
    │     ③ 追加历史并裁剪到策略上限
    └── markPasswordExpiryNoticed(LocalDateTime at)
```

实现约束：

1. **明文不进入领域**。应用层用 `PasswordHasher.matches` 对历史密文逐条比对，把布尔事实交给聚合，
   聚合据此拒绝。BCrypt 仍留在基础设施，领域只存密文。
2. 删除 `PasswordHistoryRepository`；`PasswordPolicyService.ensureNotReused/recordHistory` 一并删除。
3. `UserRepository` 增加 `findByIdWithPasswordHistory(tenantId, userId)`，仅供改密用例使用，
   避免登录链路为每条查询加载历史。
4. `password_notice_time` 纳入 `User` 聚合；到期提醒任务按用户执行"加载 → `markPasswordExpiryNoticed` →
   `save`"的短事务，**禁止**再用全表 `updateById` 绕过聚合。
5. 表结构不变：`sys_password_history`、`sys_user.password_notice_time` 均保留，只改访问路径。

## 4. 迁移映射

| # | 旧包 | 新包 | 文件数 | 同步修改 |
|---|---|---|---|---|
| M1 | `…auth.controller` | `…auth.interfaces.controller` | 10 | 10 个 `*ControllerTest` 包声明 |
| M2 | `…auth.dto` | `…auth.interfaces.dto` | 22 | 控制器 import |
| M3 | `…auth.web` | `…auth.interfaces.web` | 1 | 控制器 import |
| M4 | `…auth.vo` | `…auth.application.vo` | 16 | 全部控制器 import、相关测试 |
| M5 | `…auth.entity` | `…auth.infrastructure.persistence.entity` | 13 | 20 个仓储/装配器、10 个测试 |
| M6 | `…auth.mapper` | `…auth.infrastructure.persistence.mapper` | 12 | 仓储实现、Mapper 自引用 |
| M7 | `…auth.security` | `…auth.infrastructure.security` | 14 | `JwtAuthenticationFilter`、`SecurityConfig`、缓存实现、测试 |
| M8 | `…auth.jwt` | `…auth.infrastructure.jwt` | 1 | `JwtTokenIssuerAdapter`、`JwtAuthenticationFilter`、测试 |
| M9 | `…auth.config` | `…auth.infrastructure.config` | 6 | `SecurityConfig` 引用、启动类 |
| M10 | `…auth.bootstrap`（starter） | `…auth.infrastructure.bootstrap` | 2 | 启动类扫描、测试 |

> 纯机械移动：只改 `package`/`import`，不改任何方法体。建议使用 IDE 的 Move/Rename 重构，禁止正则批量替换。

## 5. 分阶段实施

每个阶段独立提交、独立验证、可单独回滚。阶段之间不交叉。

### Phase 0：建立守护基线（不改生产代码）

1. 在 `AuthArchitectureTest` 中补充**新旧两套包名**的依赖规则（见 §6.2）。
2. 暂不启用"根包白名单"规则（当前必然失败）。
3. 运行架构测试，确认基线通过。

验收：

```powershell
mvnw.cmd -f whale-backend/pom.xml -pl whale-services/whale-service-auth/whale-service-auth-architecture-test test
```

### Phase 1：接口层收敛（M1–M3，33 个文件）

1. 移动 `controller/dto/web`。
2. 删除或改写 `com.bluewhale.auth.interfaces` 下的 `package-info.java`（保留为层说明）。
3. 同步 starter 中 `com.bluewhale.auth.controller` 测试包（10 个）。

验收：`compile` + 控制器测试 + 架构测试。此阶段 `vo` 仍在旧包，允许。

### Phase 2：应用层 VO 收敛（M4，16 个文件）

1. `vo → application.vo`。
2. 同步全部控制器 import 与相关测试。

验收：`compile` + 应用层/控制器测试 + 架构测试。

### Phase 3：基础设施层收敛（M5–M10，48 个文件）

建议拆成两个提交：

- **3a 持久化**：`entity` + `mapper → persistence.{entity,mapper}`，同步 20 个仓储/装配器与 10 个测试。
- **3b 技术组件**：`security` + `jwt` + `config` + `bootstrap → infrastructure.*`，同步 `SecurityConfig`、启动类与测试。

验收：`compile` + 全量单测 + 架构测试 + 启动冒烟（见 §8）。

### Phase 4：收紧规则

1. 删除 ArchUnit 中旧包名的临时豁免。
2. 启用"根包白名单"规则，仅保留 `com.bluewhale.auth`（启动类）。
3. 全量回归。

验收：架构测试在**无豁免**状态下通过。

### Phase 5：仓储与端口归位（领域建模重构，必须三段提交）

Phase 5 **不是包移动**，会改动方法签名与聚合模型，禁止与 Phase 1–4 合并。三段顺序执行：5a → 5b → 5c。

#### Phase 5a：端口归位（不改口令模型）

1. 删除 `TenantStatusSyncPort`：`TenantAppService`、`TenantProvisioningAppService` 去掉显式调用，
   缓存同步收敛到 `TenantRepository` 实现。
2. 删除 `CurrentUserProvider`：新增 `interfaces.web.CurrentUserResolver`；
   `SessionAppService.me/switchTenant/listTenants`、`PasswordAppService.changeOwnPassword`
   改为显式 `operatorId` 入参。
3. `TenantPasswordPolicy` + `TenantPasswordPolicyRepository` 迁入 `domain.tenant`。

**5a DoD**

- [ ] 应用层不再出现 `CurrentUserProvider`、`TenantStatusSyncPort`
- [ ] 应用层用例无"隐式当前用户"，控制器负责解析 `operatorId`
- [ ] `mvnw.cmd -f whale-backend/pom.xml -pl whale-services/whale-service-auth -am test` 全绿
- [ ] 冒烟：登录 → `/auth/me` → 切换租户 → 租户停用后网关按新状态拒绝

#### Phase 5b：PasswordHistory 建模（单独联调，不与 5a 合并）

1. `User` 增加 `List<PasswordHistoryEntry>` 与 `passwordNoticeTime`，新增
   `changePassword(newPwd, matchesHistory, at)`、`markPasswordExpiryNoticed(at)`。
2. 删除 `PasswordHistoryRepository`；删除 `PasswordPolicyService.ensureNotReused/recordHistory`。
3. `UserRepository` 增加 `findByIdWithPasswordHistory`，仅供改密用例。
4. 口令到期提醒任务改为"按用户加载聚合 → `markPasswordExpiryNoticed` → `save`"的短事务。

**5b DoD（独立联调）**

- [ ] 改密成功：`sys_password_history` 正确追加并按 `history-size` 裁剪
- [ ] 旧口令错误、新口令强度不足、最近 N 次复用三种失败路径均被拒绝
- [ ] 跨租户（切换租户后）改密仍按全局 `userId` 加载成功
- [ ] 到期提醒：`password_notice_time` 落库，同一口令周期内不重复提醒，改密后新周期恢复
- [ ] 提醒任务按用户短事务，无全表 `updateById`，无长事务
- [ ] 登录、刷新、登出链路行为不变

#### Phase 5c：规则收紧

1. 启用 §3.4 的 `repository_belongs_to_domain`、`port_belongs_to_application`、
   `query_belongs_to_application`、`adapter_belongs_to_infrastructure`、`query_ports_are_read_only`。
2. 删除过渡期豁免，全量回归。

**5c DoD**

- [ ] 五条命名/只读规则在**无豁免**状态下通过
- [ ] 人工注入一条违规（如 application 下新建 `*Repository`）验证规则确实失败
- [ ] 全量单测 + 架构测试 + 启动冒烟通过

## 6. ArchUnit 规则

### 6.1 保留与调整

| 现有规则 | 处理 |
|---|---|
| `domain_must_not_depend_on_frameworks_or_outer_layers` | 保留，包名改为新结构，并加入 `interfaces..` |
| `application_must_not_depend_on_infrastructure_or_within_interfaces` | 保留，加入 `interfaces..`、`web..` |
| `interfaces_must_not_depend_on_infrastructure_or_domain` | 保留，加入 `web..`、`persistence..` |
| `distributed_lock_fail_fast_false_only_on_cache_mutex` | 不变 |

### 6.2 目标规则（Phase 0 写入，Phase 4 去掉旧包名）

```java
@ArchTest
static final ArchRule domain_must_not_depend_outward = noClasses()
        .that().resideInAPackage("com.bluewhale.auth.domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
                "com.bluewhale.auth.application..",
                "com.bluewhale.auth.infrastructure..",
                "com.bluewhale.auth.interfaces..",
                "org.springframework..",
                "com.baomidou..",
                "org.apache.ibatis..",
                "jakarta..");

@ArchTest
static final ArchRule application_must_not_depend_outward = noClasses()
        .that().resideInAPackage("com.bluewhale.auth.application..")
        .should().dependOnClassesThat().resideInAnyPackage(
                "com.bluewhale.auth.infrastructure..",
                "com.bluewhale.auth.interfaces..",
                "com.baomidou..",
                "org.apache.ibatis..",
                "org.springframework.data.redis..",
                "org.springframework.security..");

@ArchTest
static final ArchRule interfaces_must_not_depend_inward_or_persistence = noClasses()
        .that().resideInAPackage("com.bluewhale.auth.interfaces..")
        .should().dependOnClassesThat().resideInAnyPackage(
                "com.bluewhale.auth.domain..",
                "com.bluewhale.auth.infrastructure..",
                "com.baomidou..",
                "org.apache.ibatis..");
```

### 6.3 根包白名单（Phase 4 启用）

```java
@ArchTest
static final ArchRule only_four_layers_at_root = classes()
        .that().resideInAPackage("com.bluewhale.auth..")
        .and().resideOutsideOfPackage("com.bluewhale.auth")
        .should().resideInAnyPackage(
                "com.bluewhale.auth.domain..",
                "com.bluewhale.auth.application..",
                "com.bluewhale.auth.infrastructure..",
                "com.bluewhale.auth.interfaces..");
```

配合 `@AnalyzeClasses(packages = "com.bluewhale.auth", importOptions = DoNotIncludeTests.class)`，
该规则可防止任何人在根包下新建 `entity/`、`mapper/`、`util/` 等"第五层"。

## 7. 风险与对策

| 风险 | 等级 | 对策 |
|---|---|---|
| Mapper 扫描失效 | 中 | 现无 `@MapperScan`/`@Mapper`，依赖启动类包自动扫描；移动后新包仍在 `com.bluewhale.auth` 下。Phase 3a 后必须做启动冒烟并调用一个 Mapper 端点 |
| 配置类未被扫描 | 低 | `@ConfigurationPropertiesScan` 无 `basePackages`，覆盖整棵子树；冒烟验证 |
| 测试包声明未同步 | 中 | 测试包名必须与生产包一致；每阶段以 `test` 全绿为准 |
| IDE 重构遗漏 | 中 | 用 IDE Move/Rename，禁止脚本替换；以 `compile` 报错清单收口 |
| 并行开发冲突 | 中 | 每阶段单独 PR，先合 Phase 1/2（低冲突），再合 Phase 3 |
| 误改行为 | 低 | 只允许 `package`/`import` 变更；diff 审计时排除方法体改动 |
| 规则"假通过" | 高 | Phase 4 必须在无旧包名豁免下通过，并人工抽查一条违规验证规则会失败 |
| 仓储与端口混用 | 高 | 按 §3.4 四条规则判定，命名后缀与查询只读由 ArchUnit 强制；Phase 5a/5b 归位 4 处存量错放 |

## 8. 验收清单（DoD）

以下为 **Phase 1–4（包收敛）** 的验收项；Phase 5 使用 §5 中 5a/5b/5c 各自的 DoD。

- [ ] `com.bluewhale.auth` 根包下只剩 `WhaleAuthApplication`
- [ ] 四个层包之外无生产类
- [ ] ArchUnit 在无旧包名豁免下通过
- [ ] `mvnw.cmd -f whale-backend/pom.xml -pl whale-services/whale-service-auth -am clean test` 全绿
- [ ] 服务可启动，`GET /actuator/health` 返回 `UP`
- [ ] `POST /auth/login` + `GET /auth/me` 冒烟通过
- [ ] 一个依赖 PO/Mapper 的接口（如 `/user/page`）调用成功
- [ ] `git diff` 中只有 `package`/`import` 变更，无方法体改动
- [ ] 更新 [README.md](../../whale-backend/whale-services/whale-service-auth/README.md) 目录结构章节

**Phase 5 专项验收（不与上面合并）**

- [ ] 应用层不存在隐式当前用户，用例签名显式接收 `operatorId`
- [ ] 应用层不存在 `*Repository`；`*Port` / `*Query` 只存在于 application
- [ ] `PasswordHistory` 只经 User 聚合访问，`PasswordHistoryRepository` 已删除
- [ ] 到期提醒写操作经 `UserRepository`，无全表 `updateById` 绕过聚合

## 9. 推广到其余服务

### 9.1 统一骨架

```
com.bluewhale.<service>
├── <Service>Application
├── domain.<subdomain>
├── application.<subdomain>
├── infrastructure.{persistence,adapter,security,config,...}
└── interfaces.{controller,dto,web}
```

规则：**层是模块（或顶级包），子域是层内包**。子域永远不升级为模块，除非出现真正的第二个限界上下文。

### 9.2 单服务检查表

| 检查 | 不通过的表现 |
|---|---|
| 根包下是否只有启动类 | 出现 `entity/mapper/util/common/config` 等根级包 |
| 是否有两套包方案并存 | 同时存在 `com.x.security` 与 `com.x.infrastructure.security` |
| ArchUnit 是否覆盖全部层包 | 规则里只写了 `infrastructure..` |
| PO/Mapper 是否只在 `infrastructure.persistence` | 出现在 domain/application |
| application 是否 import 第三方持久化/安全框架 | `com.baomidou`、`spring-data-redis` |
| controller 是否 import 仓储或 PO | 直接 new/注入 `*RepositoryImpl`、`Sys*` |

### 9.3 分批建议

1. 第一批：auth（本方案）+ 一个结构相近的服务，验证模板可复制。
2. 第二批：已分层的服务，只做包收敛，不动模块。
3. 第三批：单模块服务，只补 ArchUnit 与根包白名单，不拆模块。
4. 每批完成后在 `docs/backend.md` 更新一次骨架说明，避免各服务各自演化。

## 10. 实施前置条件

按 [docs/README.md](../README.md) 文档约定：

1. 在 `requirements/register.md` 登记 REQ 编号与验收标准。
2. 本文档定稿（移除"待评审"）。
3. 每个 Phase 完成后在 `docs/devlog/` 当日文件记录需求/完成/待做/已修正项。
4. 变更按 `checklists/code-review-checklist.md` 自检，并在 `reviews/` 留存报告。
