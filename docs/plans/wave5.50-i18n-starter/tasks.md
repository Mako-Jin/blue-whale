# Wave 5.50 国际化下沉：任务清单

> 依据 [spec.md](spec.md)。按顺序执行；T1–T4 为 SDK 改造，T5 试点接入，T6 删除集中服务，T7 验证，T8 文档与提交。

## T1 SDK 配置与模块扫描

- [x] `WhaleI18nProperties` 新增 `moduleScanEnabled`、`moduleScanPattern`、`autoInit`、`ddlLocation`、`admin`（嵌套：`enabled`、`path`），保留既有字段与默认值。
- [x] 新增 `com.bluewhale.i18n.support.I18nModuleScanner`：扫描 `classpath*:i18n/**/messages.properties`，输出去重排序后的 `classpath:` basenames，末尾追加默认 basename；异常只告警。
- [x] `WhaleI18nAutoConfiguration#i18nPropertiesMessageSource` 改为使用扫描结果 `setBasenames`；启动日志输出扫描数量。
- [x] 新增测试 `I18nModuleScannerTest`。

**验收**：`mvn -o -pl whale-sdk/whale-i18n-sdk -am test` 通过；扫描测试覆盖多模块、去重、空结果三种情况。

## T2 启动自动建表

- [x] 新增 `src/main/resources/db/i18n/mysql/sys_i18n_message.sql`：单条 `CREATE TABLE IF NOT EXISTS sys_i18n_message (...)`，字段与实体一致，含 `uk_key_locale`、`idx_locale`。
- [x] 新增 `com.bluewhale.i18n.support.I18nSchemaInitializer implements ApplicationRunner`：按 `ddl-location` 读取并执行；失败时用 `DatabaseMetaData` 复核，已存在 INFO / 不存在 WARN，均不阻断启动。
- [x] 自动配置注册该 Bean：`@ConditionalOnProperty(whale.i18n.auto-init, matchIfMissing=true)` + `@ConditionalOnBean(DataSource.class)` + `@ConditionalOnClass(JdbcTemplate.class)`。
- [x] 新增测试 `I18nSchemaInitializerTest`（Mockito：执行成功、异常且表存在、异常且表不存在）。

**验收**：单测通过；本地 auth 启动日志出现建表结果；`bluewhale_auth.sys_i18n_message` 结构正确。

## T3 本地文案管理接口

- [x] 迁移 `whale-i18n` 的 DTO/VO/服务逻辑到 `com.bluewhale.i18n.admin`（`dto`、`vo`、`I18nLocalMessageService`、`impl`）。
- [x] 新增 `I18nLocalMessageController`：路径由 `whale.i18n.admin.path` 注入；5 个端点；`@RequirePermission("sys:i18n:*")`；写操作 `@AuditLog`；沿用 `Result` / `PageResult`。
- [x] 自动配置新增嵌套 `WhaleI18nAdminConfig`：`admin.enabled=true` + 存在 `SysI18nMessageMapper` + 类路径存在 `RestController`/`Result`/`RequirePermission` 才装配；缺失时 INFO 提示。
- [x] `whale-i18n-sdk/pom.xml` 增加 `whale-spring-boot-starter`、`whale-auth-sdk` 的 provided/optional 依赖。
- [x] 迁移并补充 `I18nLocalMessageServiceImplTest`（分页、唯一键冲突、编码变更清理旧缓存）。

**验收**：单测通过；`admin.enabled=false` 时无该 Controller Bean。

## T4 SDK 构建与规范

- [x] `mvn -o -pl whale-sdk/whale-i18n-sdk -am test` BUILD SUCCESS。
- [x] Checkstyle 0 违规（Javadoc、`@author`、行宽、行尾空格）。
- [x] 确认 `META-INF/spring/...AutoConfiguration.imports` 无需新增条目（仍只有一个自动配置类）。

**验收**：构建与规范检查通过。

## T5 试点接入 auth + file

- [x] `whale-auth/pom.xml`、`whale-file/pom.xml` 增加 `whale-i18n-sdk` 依赖。
- [x] `whale-auth/application.yml`：`whale.i18n.enabled=true`、`auto-init=true`、`module-scan-enabled=true`、`admin.enabled=true`、`admin.path=/auth/i18n/message`。
- [x] `whale-file/application.yml`：`whale.i18n.enabled=true`、`auto-init=true`、`admin.enabled=false`。
- [x] 新增 `whale-auth/src/main/resources/i18n/auth/messages{,_zh_CN,_en_US}.properties`（3–5 条登录/权限文案）。
- [x] 新增 `whale-file/src/main/resources/i18n/file/messages{,_zh_CN,_en_US}.properties`（3–5 条文件文案）。

**验收**：两服务构建通过；启动后各自库中自动出现 `sys_i18n_message`。

## T6 删除集中式 whale-i18n

- [x] 删除 `backend/whale-services/whale-i18n` 目录。
- [x] `backend/whale-services/pom.xml` 移除模块。
- [x] 网关 `application.yml` 移除 `whale-i18n` 路由。
- [x] `infrastructure/docker/mysql/init/01-init-databases.sql` 移除 `bluewhale_i18n`。
- [x] `whale-auth/db/init.sql` 保留 `sys:i18n:*` 权限码，路径改为 `/auth/i18n/message/**`，注释同步。
- [x] 前端移除 `/i18n-demo` 菜单与占位路由（`menu.ts`、`routes.tsx`、`MainLayout.tsx`）。

**验收**：全仓 `grep whale-i18n` 仅剩历史文档与 SDK 自身；构建不再包含该模块。

## T7 实机验证

- [x] 启动 auth 与 file（按既有环境变量模板），确认建表日志与表结构。
- [x] auth：登录正常；`/auth/i18n/message/page` 返回 `code=0`；新增英文文案后解析顺序为 DB > 文件 > code。
  - 备注：auth/file 当前无业务代码调用 `MessageSource`，实机以 CRUD 闭环（create → page → update → detail → delete → 404）取证；三级回退优先级由 SDK `WhaleMessageSourceTest` 12 例覆盖。
- [x] file：`/file/i18n/message/**` 未开放（无控制器映射，`NoResourceFoundException`；平台既有 `GlobalExceptionHandler` 将未匹配路径归一为 `code=500`）。
- [x] 网关 `/i18n/**` 返回 404；`/auth/**`、`/file/**` 正常。

**验收**：上述 4 条全部通过，记录命令与响应。

## T8 文档与提交

- [x] 追加 `docs/devlog/2026-10-23.md` §二十。
- [x] 更新 `docs/plans/roadmap.md` 2.4 小节、`docs/service-catalog.md` 端口/路由/库表、`docs/architecture.md` 与 `docs/plans/archive/todo-inventory-2026-10-23.md` 相关条目（并同步 `naming-conventions.md`、`requirements/register.md`）。
- [x] `git add` 相关文件并提交，推送 Gitee origin；GitHub 镜像尝试一次，失败跳过。（提交 `c8374d9`，Gitee `main` 与 GitHub `main` 均已同步）

**验收**：提交成功；devlog 与 roadmap 可追溯到本 spec。

