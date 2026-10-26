# whale-auth → whale-service-auth 多模块拆分（DDD 四层 + 运行时改名）

- 状态：待评审（Spec Mode，需用户批准后执行）
- 日期：2026-10-26
- 关联：REQ-ARCH-001 / T-DDD-1~12（[ddd-whale-auth.md](../../design/ddd-whale-auth.md)）、[naming-conventions.md](../../naming-conventions.md)
- 影响服务：whale-auth（权限中心，端口 8081）、whale-gateway、whale-im、CI、运维脚本、监控

## 1. 目标

1. 与远程仓库同步（已完成：`git pull --ff-only` 快进 `ea3d1f4..226c612`）。
2. 把 `backend/whale-services/whale-auth` 单模块拆为 Maven 父子多模块，落到用户已建骨架
   `backend/whale-services/whale-service-auth`（5 个空 pom 已存在）。
3. 迁移完成后删除旧 `backend/whale-services/whale-auth`。
4. 运行时服务名、可部署 jar、网关路由、Feign 默认值、CI、脚本、监控统一改为 `whale-service-auth`。
5. 顶层聚合目录保持 `whale-services`（不使用 `whale-server`）。

## 2. 已确认决策（用户选择）

| 决策点 | 结论 | 理由 |
| --- | --- | --- |
| 顶层聚合目录 | **保留 `whale-services`** | 与 `whale-apps`（应用层）形成「服务层 vs 应用层」清晰分层；改名波及 CI/脚本/Dockerfile/文档，收益低 |
| auth 拆分策略 | **纯净 DDD·含重构** | 允许修改代码打破包依赖环：删除 VO 死重载、security 改依赖仓储端口 |
| 运行时命名 | **全量改名 `whale-service-auth`** | Nacos 服务名、jar 名、`lb://`、CI matrix、启动脚本、监控 job 全改 |

## 3. 现状基线

- 旧模块：`backend/whale-services/whale-auth`，artifactId `whale-auth`，parent `whale-parent`，`finalName=${project.artifactId}`。
- 源码 261 个受管文件（main 211 + test 49 + pom），包根 `com.bluewhale.auth`。
- 已 DDD 化的包：`domain`（纯）、`application`（依赖 domain + vo）、`infrastructure`（依赖 domain/application/entity/mapper/security）。
- 经典残留包：`controller`、`dto`、`service`（仅 `AuthService`）、`security`、`jwt`、`config`、`vo`、`entity`、`mapper`。
- 资源：`application.yml`、`db/*.sql`（init + migration-5.34~5.64）、`i18n/auth/messages*.properties`；测试资源 `src/test/resources/application.yml`。
- 启动类 `WhaleAuthApplication`：`@SpringBootApplication` + `@ConfigurationPropertiesScan` + `@EnableDiscoveryClient` + `@EnableFeignClients`，包根 `com.bluewhale.auth` 可跨模块扫描。
- 依赖来源确认：`com.bluewhale.common.*` 由 `whale-spring-boot-starter` 提供；`io.jsonwebtoken(jjwt)` 与 `com.bluewhale.security.token.*` 由 `whale-security` 提供。

### 3.1 依赖环分析结论（重构依据）

1. `application → vo`（10 处）与 `vo → entity.SysXxx`（6 处）构成环。各 VO 的 `from(SysXxx)` 重载**全仓库无调用者**（AppService 一律传领域聚合，如 `UserVO.from(requireUser(id))`），删除后 `vo` 只依赖 `domain`，环消失。
2. `security → application.role.RoleAppService`（`UserDetailsServiceImpl`、`PermissionInterceptor`）造成 infrastructure → application 的语义倒挂。`RoleAppService.listRoleCodesByUserId/listPermissionCodesByUserId` 只是 `RoleRepository` 既有方法的纯组合，可下沉为仓储方法，`security` 直接依赖仓储端口。
3. `domain` 包不依赖任何其他 auth 包（已 grep 验证），`infrastructure` 不依赖 `service/controller/dto/config/jwt/vo`（已 grep 验证），`application` 只额外依赖 `vo`。

## 4. 目标结构与依赖图

```
backend/whale-services/
├── pom.xml                              # modules: whale-service-auth（替换 whale-auth）
└── whale-service-auth/                  # 聚合 POM（packaging=pom）
    ├── pom.xml
    ├── whale-service-auth-domain/       # domain.**（纯）
    ├── whale-service-auth-application/  # application.** + vo.**
    ├── whale-service-auth-infrastructure/ # infrastructure.** + entity + mapper + jwt + security + config
    ├── whale-service-auth-interfaces/   # controller.** + dto.** + service.**（AuthService）
    └── whale-service-auth-starter/      # WhaleAuthApplication + 资源 + 全部测试 + 可部署 jar
```

依赖方向（严格单向，无环）：

```
interfaces ──> application ──> domain
     │              │            ▲
     └──> infrastructure ─────────┘   （infrastructure 同时依赖 application 与 domain）
starter ──> interfaces + infrastructure + application + domain
```

Java 包名、类名、REST 路径、出入参、数据库 `bluewhale_auth`、Redis 键 `whale:auth:*` 均**不变**；`whale-auth-sdk` 模块名**不变**。

### 4.1 各模块 pom 依赖（artifactId 全部 `whale-service-auth-<layer>`，version `${project.version}`）

| 模块 | 依赖 |
| --- | --- |
| domain | `whale-spring-boot-starter`、lombok |
| application | domain、`whale-spring-boot-starter`、`whale-security`、`spring-boot-starter-data-redis`、lombok |
| infrastructure | application、domain、`whale-spring-boot-starter`、`whale-security`、`whale-auth-sdk`、`spring-boot-starter-web`、`spring-boot-starter-security`、`spring-boot-starter-data-redis`、`mybatis-plus-spring-boot4-starter`、`redisson-spring-boot-starter`、`spring-boot-starter-amqp`、lombok |
| interfaces | application、domain、infrastructure、`whale-spring-boot-starter`、`whale-auth-sdk`、`spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-security`、`springdoc-openapi-starter-webmvc-ui`、lombok |
| starter | 上述 4 个兄弟模块 + **旧 pom 全量依赖清单**（web/validation/actuator/springdoc/prometheus/security/data-redis/cloud-bootstrap/openfeign/nacos-discovery/nacos-config/mybatis-plus/mysql(runtime)/redisson/amqp/whale-spring-boot-starter/whale-tools/whale-security/whale-auth-sdk/whale-i18n-sdk/lombok/starter-test）；`spring-boot-maven-plugin` + `finalName=whale-service-auth` |

starter 保留旧 pom 全量依赖是为了保证运行时装配与旧模块逐项等价（Maven 去重，重复声明无副作用）。

## 5. 阶段 1：重构（在旧模块内先行完成并编译验证）

### 5.1 删除 VO 死重载（6 个文件）

| 文件 | 删除内容 |
| --- | --- |
| `vo/UserVO.java` | `import com.bluewhale.auth.entity.SysUser;` + `public static UserVO from(SysUser user)` |
| `vo/RoleVO.java` | `import ...entity.SysRole;` + `from(SysRole role)` |
| `vo/MenuVO.java` | `import ...entity.SysMenu;` + `from(SysMenu menu)` |
| `vo/DeptVO.java` | `import ...entity.SysDept;` + `from(SysDept dept)` |
| `vo/PermissionVO.java` | `import ...entity.SysPermission;` + `from(SysPermission permission)` |
| `vo/UserAttrVO.java` | `import ...entity.SysUserPermission;` + `from(SysUserPermission entity)` |

保留 `from(领域聚合)` 重载。改后 `vo` 仅依赖 `domain`。

### 5.2 RoleRepository 新增两个组合方法

`domain/role/repository/RoleRepository.java` 增加：

```java
List<String> listRoleCodesByUserId(Long userId);
List<String> listPermissionCodesByUserId(Long userId);
```

`infrastructure/persistence/repository/RoleRepositoryImpl.java` 用既有方法组合实现（逻辑与 `RoleAppService` 现实现逐行一致）：

```java
@Override
public List<String> listRoleCodesByUserId(Long userId) {
    List<Long> roleIds = this.listRoleIdsByUserId(userId);
    if (roleIds.isEmpty()) {
        return List.of();
    }
    return this.listEnabledByIds(roleIds).stream()
            .map(role -> role.getRoleCode().value())
            .toList();
}

@Override
public List<String> listPermissionCodesByUserId(Long userId) {
    List<Long> roleIds = this.listRoleIdsByUserId(userId);
    if (roleIds.isEmpty()) {
        return List.of();
    }
    List<Long> permissionIds = this.listPermissionIdsByRoleIds(roleIds);
    if (permissionIds.isEmpty()) {
        return List.of();
    }
    return this.listEnabledPermissionCodesByIds(permissionIds);
}
```

`application/role/RoleAppService.java` 的两个同名方法改为委托 `roleRepository`，避免双实现漂移。

### 5.3 security 改依赖仓储端口

| 文件 | 变更 |
| --- | --- |
| `security/UserDetailsServiceImpl.java` | `RoleAppService` → `RoleRepository`（import、字段、两处调用 `loadUserByUsername` L43、`loadUserById` L72） |
| `security/PermissionInterceptor.java` | `RoleAppService` → `RoleRepository`（import L3、字段 L42、`preHandle` L76 调用） |

### 5.4 阶段 1 验证

```powershell
mvn -q -pl whale-services/whale-auth -am -DskipTests compile
mvn -q -pl whale-services/whale-auth -am test
```

两条均 BUILD SUCCESS 后进入阶段 2（此时包依赖已无环；若通过一次性完成阶段 2，可只跑一次最终验证）。

## 6. 阶段 2：拆分落地

使用 `git mv` 保留历史；**不修改任何 `package`/`import` 语句**（包名不变）。

| 目标模块 | 迁入内容（`src/main/java/com/bluewhale/auth/` 相对路径） |
| --- | --- |
| `-domain` | `domain/**`（含 `domain/model/vo`、`domain/*/aggregate`、`.../entity`、`.../repository`、`domain/event`、所有 `package-info.java`） |
| `-application` | `application/**` + `vo/**` |
| `-infrastructure` | `infrastructure/**`、`entity/**`、`mapper/**`、`jwt/**`、`security/**`、`config/**` |
| `-interfaces` | `controller/**`、`dto/**`、`service/**`、`interfaces/package-info.java`（已存在的空包标记） |
| `-starter` | `WhaleAuthApplication.java` + `src/main/resources/**`（application.yml、db、i18n）+ `src/test/**`（全部 49 个测试与 `src/test/resources/application.yml`） |

pom 落地要求：

1. `whale-service-auth/pom.xml`：parent `whale-parent`（`../../whale-parent/pom.xml`），`artifactId=whale-service-auth`，`packaging=pom`，`modules` 5 项，`name=Whale Service Auth`。
2. 5 个子 pom：parent `whale-service-auth`（`../pom.xml`），artifactId 与目录名一致，依赖按 §4.1。
3. `backend/whale-services/pom.xml` 中 `<module>whale-auth</module>` → `<module>whale-service-auth</module>`。
4. starter pom 的 `finalName` = `whale-service-auth`，仅 starter 挂 `spring-boot-maven-plugin`。

## 7. 阶段 3：运行时改名清单

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | `backend/whale-services/whale-auth/src/main/resources/application.yml` L13 | `spring.application.name: whale-auth` → `whale-service-auth`（`datasource` 库仍 `bluewhale_auth`） |
| 2 | `whale-gateway/src/main/resources/application.yml` L37/38/43/44/195 | route id `whale-auth`、`whale-auth-tenant` → `whale-service-auth`、`whale-service-auth-tenant`；`lb://whale-auth` → `lb://whale-service-auth`；`apply-to-routes` 列表同步 |
| 3 | gateway `config/CircuitBreakerProperties.java` L43 | 默认集合 `whale-auth` → `whale-service-auth` |
| 4 | gateway `filter/PermissionHeaderGlobalFilter.java` L59 | `AUTH_ME_URI = "lb://whale-service-auth/auth/me"` |
| 5 | gateway 测试 `CircuitBreakerPropertiesTest` L34/46/58、`CircuitBreakerGlobalFilterTest` L40 | 断言与样本值同步 |
| 6 | `whale-im` `UserDirectoryClient.java` L81 | `@Value("${whale.im.auth.service-name:whale-service-auth}")` |
| 7 | `whale-im/src/main/resources/application.yml` L59 | `service-name: ${WHALE_IM_AUTH_SERVICE_NAME:whale-service-auth}` |
| 8 | `infrastructure/prometheus/prometheus-docker.yml` L27、`deploy/monitoring/prometheus.yml` L28 | `job_name: whale-service-auth`（及 targets 如含服务名标签） |
| 9 | `.github/workflows/docker-deploy.yml` L40 | `{ service: whale-service-auth, module: whale-services/whale-service-auth, port: 8081 }` |
| 10 | `scripts/dev/whale-hold.ps1` L25、`whale-stack.ps1` L56、`whale-start-all.ps1` L27、`whale-stack534.ps1` L56 | jar 路径 → `whale-service-auth/whale-service-auth-starter/target/whale-service-auth.jar` |
| 11 | `scripts/smoke/wave5.35-gateway.ps1` L31/44/57 | `lb://whale-auth` → `lb://whale-service-auth` |
| 12 | `deploy/backend/sample/Dockerfile` | 注释 + `ARG MODULE_PATH` 默认值 → `whale-services/whale-service-auth` |
| 13 | `docs/naming-conventions.md` §八 services 行、`docs/**/service-catalog*.md`（如存在） | 服务名/模块路径/端口行同步 |
| 14 | 源码 Javadoc 中作为**本服务名**出现的 `whale-auth` | 仅改 gateway / whale-security / whale-common / whale-audit / whale-im / whale-agent / whale-archive 等处的服务引用注释 |

**明确不改**：Java 包 `com.bluewhale.auth`、数据库 `bluewhale_auth`、Redis 键前缀 `whale:auth:`（域命名空间）、Maven 模块 `whale-auth-sdk`、其他模块测试中作为任意样本字符串的 `"whale-auth"` 字面量。

## 8. 阶段 4：收尾

1. 删除旧目录 `backend/whale-services/whale-auth`（`git rm -r`），确认 `backend/whale-services/pom.xml` 不再引用。
2. 全仓检查残留功能性引用：`git grep -n "whale-services/whale-auth"`、`git grep -n "lb://whale-auth"`、`git grep -n "job_name: whale-auth"` 均应为 0。
3. 更新 living 文档：`docs/naming-conventions.md` §八、服务目录文档；`docs/design/ddd-whale-auth.md` 追加「已落地」章节指向新模块。
4. 全量构建验证。

## 9. 验证（执行门禁）

在 `backend/` 下执行（PowerShell 用 `;` 分隔，不用 `&&`）：

```powershell
mvn -q -pl whale-services/whale-service-auth -am -DskipTests compile
mvn -q -pl whale-services/whale-service-auth/whale-service-auth-starter -am test
mvn -q -DskipTests install
```

验收标准：

- [ ] 5 个模块均 BUILD SUCCESS，聚合 POM 反应堆顺序为 domain → application → infrastructure → interfaces → starter。
- [ ] `whale-service-auth-starter/target/whale-service-auth.jar` 生成。
- [ ] 原 49 个测试全部通过（含 gateway/whale-im 中改名后的测试）。
- [ ] `mvn dependency:tree -pl whale-services/whale-service-auth/whale-service-auth-domain` 不含 Spring Web / MyBatis / Redis。
- [ ] 功能性残留引用计数为 0；接口契约（6 控制器 / 35 接口路径与出入参）零变更。
- [ ] `git status` 无旧目录残留。

## 10. 风险与回滚

| 风险 | 缓解 |
| --- | --- |
| 拆分后运行时依赖缺失（jjwt、redis、redisson、amqp 等） | starter 保留旧 pom 全量依赖；先 `install` 再启动冒烟 |
| 迁移期误改包名导致大范围 import 变更 | 只做 `git mv`，禁止改 `package`；用编译验证 |
| 旧模块提前删除导致无法对比 | 阶段 4 才删除；此前保留旧模块作为对照 |
| 网关/Nacos 仍指向 `whale-auth` | §7 清单逐项核对 + 全仓 grep 门禁 |
| 测试资源路径失效 | `src/test/**` 整体迁入 starter，保持相对路径 |

回滚：阶段 1~3 未删除旧模块前，`git checkout -- .` 即可恢复；阶段 4 后用 `git revert <commit>`。

## 11. 不在本次范围

- 不重命名 Java 包 `com.bluewhale.auth`、SDK 模块 `whale-auth-sdk`、数据库与 Redis 键。
- 不修改任何 REST 契约、业务逻辑（除 §5 明确的重构）。
- 不新增功能、不做性能优化、不调整端口（仍 8081）。
