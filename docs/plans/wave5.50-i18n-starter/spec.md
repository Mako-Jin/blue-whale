# Wave 5.50 国际化下沉：i18n Starter 自治化改造

## 1. 背景与目标

用户诉求（2026-10-23）：

> 国际化做成一个 jar 包，启动时获取 DB 信息、创建国际化表；**每个服务自己管理自己的国际化文件**，而不是一个服务集中管理、再由管理员操作 DB。

参考实现 `D:\workspace\SystemTerminator\sts-common\sts-common-messages` 的骨架：jar + Spring Boot 自动配置 + `DatabaseMessageSource`（DB 优先）+ `classpath*:i18n/*/messages.properties` 模块扫描。参考实现的建表 SQL 已存在，但 JDBC 查询实现为空壳、也没有启动建表逻辑，因此本项目需要把理念补完整，而不是照搬代码。

目标：

1. `whale-i18n-sdk` 成为**自包含 starter**：依赖即接入，启动时在**各服务自己的数据源**中幂等创建 `sys_i18n_message`。
2. 每个服务的国际化文案跟随服务自身：`src/main/resources/i18n/<模块>/messages{,_zh_CN,_en_US}.properties` 打进各自 jar，starter 自动扫描所有 jar 的 `i18n/**/messages.properties` 合并为回退源。
3. 每个服务可**选择性**开启内置本地文案管理接口（`/i18n/local/message`，路径可配），直接维护自己库里的表，无需集中服务与 DB 管理员。
4. 删除集中式 `whale-i18n` 服务及其网关路由、前端占位入口、专用库。

## 2. 已确认决策（用户回答）

| 决策 | 结论 |
| --- | --- |
| 集中服务处置 | **删除** `whale-i18n`（8087 / `bluewhale_i18n` / `/i18n/**` 路由），能力全部下沉到 SDK |
| 本地管理接口默认状态 | **默认关闭**（`whale.i18n.admin.enabled=false`），按服务显式开启 |
| 本轮接入范围 | **试点 `whale-auth` + `whale-file`**，其余服务后续按模板跟进 |

## 3. 当前现状（已核对源码）

- `whale-i18n-sdk`：[WhaleMessageSource.java](../../../backend/whale-sdk/whale-i18n-sdk/src/main/java/com/bluewhale/i18n/WhaleMessageSource.java) 已实现 DB > 属性文件 > 默认值 + Redis 缓存（含空值哨兵、Redis 不可用降级）。
- 自动配置：[WhaleI18nAutoConfiguration.java](../../../backend/whale-sdk/whale-i18n-sdk/src/main/java/com/bluewhale/i18n/WhaleI18nAutoConfiguration.java) 只注册单一 basename `classpath:i18n/messages`，**不扫描模块目录**，**不建表**。
- 实体/Mapper：[SysI18nMessage.java](../../../backend/whale-sdk/whale-i18n-sdk/src/main/java/com/bluewhale/i18n/entity/SysI18nMessage.java) 对应 `sys_i18n_message`（`id/message_key/locale/message_content/module/remark/create_time/update_time/deleted`，唯一键 `uk_key_locale`）。
- 表结构只在 [SDK init.sql](../../../backend/whale-sdk/whale-i18n-sdk/src/main/resources/db/init.sql) 与 [whale-i18n init.sql](../../../backend/whale-services/whale-i18n/src/main/resources/db/init.sql) 中以 `DROP + CREATE` 手工执行，**无启动自动建表**。
- 集中服务：`whale-i18n`（8087）提供 `/i18n/message` 5 个 CRUD 端点，复用 SDK 实体/Mapper；网关 [application.yml](../../../backend/whale-services/whale-gateway/src/main/resources/application.yml#L61-L64) `Path=/i18n/**`；权限种子 `sys:i18n:*` id 60–63 在 [whale-auth init.sql](../../../backend/whale-services/whale-auth/src/main/resources/db/init.sql#L466-L473)。
- 依赖面：目前仅 `whale-i18n` 服务依赖 SDK，其余业务服务未接入。
- 前端：`/i18n-demo` 只是占位页（[menu.ts](../../../frontend/platform-apps/whale-platform/src/router/menu.ts#L21)、[routes.tsx](../../../frontend/platform-apps/whale-platform/src/router/routes.tsx#L40-L46)）。
- `whale-auth`、`whale-file` 均已有独立数据源 + MyBatis-Plus + `whale-spring-boot-starter`（后者传递 `spring-boot-starter-web`、validation、mybatis-plus、`whale-auth-sdk`），满足 starter 接入条件。

## 4. 目标架构

```
每个服务（whale-auth / whale-file / ...）
├─ pom 依赖 whale-i18n-sdk（optional 依赖由 starter 自身声明）
├─ src/main/resources/i18n/<模块>/messages.properties
│                            messages_zh_CN.properties
│                            messages_en_US.properties
├─ 自己的数据库：启动时自动 CREATE TABLE IF NOT EXISTS sys_i18n_message
└─ 运行时文案解析：Redis → 本服务 DB → 所有 jar 的 i18n 模块文件 → 默认值/code

whale-i18n-sdk（jar）
├─ I18nModuleScanner         扫描 classpath*:i18n/**/messages.properties → basenames
├─ I18nSchemaInitializer     启动幂等建表（whale.i18n.auto-init=true）
├─ WhaleMessageSource        三级回退（不变）
├─ I18nLocalMessageService   本地 CRUD（复用实体/Mapper/缓存清理）
└─ I18nLocalMessageController /i18n/local/message（默认不装配，路径可配）
```

## 5. 详细设计

### 5.1 配置项（`WhaleI18nProperties`，前缀 `whale.i18n`）

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 总开关 |
| `basename` | `classpath:i18n/messages` | 全局兜底文案（保留原行为） |
| `module-scan-enabled` | `true` | 是否扫描各 jar 的 `i18n/**/messages.properties` |
| `module-scan-pattern` | `classpath*:i18n/**/messages.properties` | 扫描模式 |
| `auto-init` | `true` | 启动时幂等建表 |
| `ddl-location` | `classpath:db/i18n/mysql/sys_i18n_message.sql` | 建表脚本位置 |
| `cache-ttl-seconds` | `3600` | 既有 |
| `cache-prefix` | `whale:i18n` | 既有 |
| `admin.enabled` | `false` | 是否装配本地 CRUD 接口 |
| `admin.path` | `/i18n/local/message` | 本地 CRUD 路径前缀（服务可改成 `/auth/i18n/message` 等） |

### 5.2 模块文案扫描

新增 `com.bluewhale.i18n.support.I18nModuleScanner`：

- 使用 `PathMatchingResourcePatternResolver` 按 `moduleScanPattern` 取全部 `messages.properties`。
- 从 `resource.getURL().toString()` 中截取 `i18n/` 起始到最后 `messages` 结束的片段，统一加 `classpath:` 前缀作为 basename；`LinkedHashSet` 去重并排序，最后追加 `properties.getBasename()`。
- 扫描异常只告警不阻断启动；启动日志输出 `[whale-i18n] 扫描到 N 个模块文案源`，DEBUG 输出清单。
- `WhaleI18nAutoConfiguration#i18nPropertiesMessageSource` 改为 `setBasenames(scan + default)`，`WhaleMessageSource` 构造与回退逻辑不变。

### 5.3 启动自动建表

新增 `com.bluewhale.i18n.support.I18nSchemaInitializer implements ApplicationRunner`：

- 条件：`whale.i18n.enabled=true`、`whale.i18n.auto-init=true`、存在 `DataSource`、类路径存在 `JdbcTemplate`。
- 读取 `ddl-location` 文本，通过 `JdbcTemplate.execute` 执行；脚本仅一条 `CREATE TABLE IF NOT EXISTS`（不删除数据）。
- 新增资源 [sys_i18n_message.sql](../../../backend/whale-sdk/whale-i18n-sdk/src/main/resources/db/i18n/mysql/sys_i18n_message.sql)，字段与实体严格一致，含 `uk_key_locale(message_key, locale)`、`idx_locale(locale)`。
- 失败处理：捕获异常后再次尝试 `DatabaseMetaData.getTables` 判断表是否已存在；已存在则 INFO 跳过，否则 WARN 记录并**不阻断服务启动**（原因：建表失败不应放大为服务不可用，首次查询会自然回退属性文件）。
- 多实例并发启动：`CREATE TABLE IF NOT EXISTS` 幂等，竞态异常按上一条处理。

### 5.4 本地文案管理接口（默认关闭）

新增 `com.bluewhale.i18n.admin` 包：

- `I18nLocalMessageService` / `I18nLocalMessageServiceImpl`：从 `whale-i18n` 服务迁移 `page/getDetail/create/update/delete` 逻辑与唯一键校验，写操作后调用 `I18nCacheEvictHelper#evict`。
- `I18nLocalMessageController`：5 个端点，路径通过 `${whale.i18n.admin.path}` 注入；沿用 `Result`/`PageResult` 响应体与 `@RequirePermission("sys:i18n:list|add|edit|delete")`、写操作 `@AuditLog`。
- 装配条件（嵌套 `@Configuration`）：`admin.enabled=true` + 存在 `SysI18nMessageMapper` + 类路径存在 `RestController` / `Result` / `RequirePermission`；缺失时仅不装配并 INFO 提示，不影响主链路。
- DTO/VO 从 `whale-i18n` 服务迁移到 SDK 的 `admin.dto` / `admin.vo`，校验规则保持不变（`messageKey`、`locale`、`messageContent` 必填；`messageContent` 长度上限与 DDL 对齐）。
- SDK `pom.xml` 增加 **provided/optional** 依赖：`whale-spring-boot-starter`（Result/PageResult/AuditLog）与 `whale-auth-sdk`（RequirePermission）；不改变下游传递依赖。

### 5.5 删除集中式 whale-i18n

- 删除目录 `backend/whale-services/whale-i18n`。
- [whale-services/pom.xml](../../../backend/whale-services/pom.xml) 移除 `<module>whale-i18n</module>`。
- 网关 [application.yml](../../../backend/whale-services/whale-gateway/src/main/resources/application.yml#L61-L64) 移除 `whale-i18n` 路由。
- [infrastructure/docker/mysql/init/01-init-databases.sql](../../../infrastructure/docker/mysql/init/01-init-databases.sql#L44-L47) 移除 `bluewhale_i18n` 建库。
- [whale-auth init.sql](../../../backend/whale-services/whale-auth/src/main/resources/db/init.sql#L466-L473) 保留 `sys:i18n:*` 权限码（本地管理接口复用），路径更新为 `/auth/i18n/message/**`；注释改为「服务内置国际化文案管理」。
- 前端：移除 `/i18n-demo` 菜单与占位路由（`menu.ts`、`routes.tsx`、`MainLayout.tsx` 若有引用一并移除）。
- 文档：更新 `docs/service-catalog.md`、`docs/architecture.md`、`docs/plans/roadmap.md`、`docs/plans/todo-inventory-2026-10-23.md`；历史 devlog / `docs/whale-auth/originals` 不改。

### 5.6 试点接入（auth + file）

`whale-auth`：

- `pom.xml` 增加 `whale-i18n-sdk` 依赖。
- `application.yml`：

```yaml
whale:
  i18n:
    enabled: true
    auto-init: true
    module-scan-enabled: true
    admin:
      enabled: true
      path: /auth/i18n/message
```

- 新增 `src/main/resources/i18n/auth/messages.properties`、`messages_zh_CN.properties`、`messages_en_US.properties`（先放 3–5 条真实登录/权限文案，供回退与冒烟验证）。

`whale-file`：

- `pom.xml` 增加 `whale-i18n-sdk` 依赖。
- `application.yml`：`whale.i18n.enabled=true`、`auto-init=true`、`admin.enabled=false`。
- 新增 `src/main/resources/i18n/file/messages{,_zh_CN,_en_US}.properties`（3–5 条文件上传/下载/存储插件文案）。

SDK 自带的 `i18n/messages*.properties` 保留为全局兜底；不改文件名。

## 6. 兼容性与影响面

- 现有 `WhaleMessageSource` 三级回退、Redis key 格式、`I18nCacheEvictHelper` 全部不变。
- 现有 `sys_i18n_message` 字段与唯一键不变，存量 `bluewhale_i18n` 数据不再使用（服务删除）。
- 未接入 SDK 的服务不受影响；接入服务多出一张本地表和可选的 5 个端点。
- 自动建表只做 `CREATE TABLE IF NOT EXISTS`，不 DROP、不写种子数据。

## 7. 验证方案

1. **单测**
   - `I18nModuleScannerTest`：用测试资源构造两个模块目录，断言 basename 去重、排序、默认 basename 追加；扫描不到时仅返回默认。
   - `I18nSchemaInitializerTest`：Mockito 模拟 `JdbcTemplate`，断言读取 DDL 并执行；执行抛异常且表不存在时只告警；表已存在时跳过。
   - `I18nLocalMessageServiceImplTest`：从原 whale-i18n 测试迁移，覆盖分页/唯一键/更新后缓存清理。
   - 既有 `WhaleMessageSourceTest`、`I18nCacheEvictHelperTest` 保持全绿。
2. **构建**
   - `$env:JAVA_HOME='D:\tools\installer\jdk\java21'`；
   - `mvn -o -pl whale-sdk/whale-i18n-sdk -am test`；
   - `mvn -o -pl whale-services/whale-auth,whale-services/whale-file -am package`；
   - Checkstyle 0 违规（Javadoc、`@author`、行宽 ≤150、无行尾空格）。
3. **实机**
   - 启动 auth：日志出现建表成功/已存在；`bluewhale_auth.sys_i18n_message` 表存在且结构正确。
   - `POST /auth/login` 正常；`GET /auth/i18n/message/page`（带 `X-Permission-Codes` 或直连测试）返回 `code=0`。
   - 启动 file：`bluewhale_file.sys_i18n_message` 自动建立；`/file/i18n/message/**` 未装配（404），证明默认关闭生效。
   - 通过临时 JUnit/Actuator 或直连调用验证文案解析顺序：DB 覆盖 > jar 内模块文件 > code。
   - 网关 `/i18n/**` 返回 404（路由已删除），`/auth/**`、`/file/**` 不受影响。
4. **文档**
   - 追加 `docs/devlog/2026-10-23.md` §二十，记录范围、变更点、验证结果；
   - 更新 `roadmap.md` 2.4 小节与 `service-catalog.md` 端口/路由表；
   - 提交并推送 Gitee origin；GitHub 镜像按既有策略尝试一次，失败跳过。

## 8. 风险与边界

- **自动建表权限**：服务 DB 账号需有 `CREATE` 权限；无权限时按 5.3 降级为 WARN，文案仍走 properties。
- **扫描成本**：`classpath*:` 扫描在 fat jar 下为一次性启动开销；通过 `module-scan-enabled=false` 可关闭。
- **重复 key**：多个模块定义同一 key 时按 basename 排序先命中者优先；文档约定模块前缀 `模块名.` 避免冲突。
- **不在本轮范围**：前端国际化管理界面、各服务文案全量迁移、语言协商（`Accept-Language` 解析器）、文案变更热推送。

## 9. 审批项

请确认本 spec 后进入实施；实施按 [tasks.md](tasks.md) 顺序执行，完成后统一提交与推送。
