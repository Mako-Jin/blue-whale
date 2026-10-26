# checklist：验收清单

## 结构

- [ ] `backend/whale-services/whale-service-auth/pom.xml` 为 `packaging=pom`，modules 5 项齐全
- [ ] 5 个子模块目录名 == artifactId == `whale-service-auth-{domain,application,infrastructure,interfaces,starter}`
- [ ] `backend/whale-services/pom.xml` 只含 `whale-service-auth`，不含 `whale-auth`
- [ ] 旧目录 `backend/whale-services/whale-auth` 已删除
- [ ] `whale-auth-sdk` 模块未改名

## 依赖方向

- [ ] domain 不依赖 application/infrastructure/interfaces/starter
- [ ] application 只依赖 domain（+ 三方与 common）
- [ ] infrastructure 依赖 domain/application，不被 application/domain 依赖
- [ ] interfaces 依赖 application/domain/infrastructure
- [ ] starter 依赖全部兄弟模块
- [ ] `vo` 包无 `entity` 引用；`vo` 中无 `from(SysXxx)` 重载
- [ ] `security` 包无 `application` 引用（改用 `RoleRepository`）

## 契约与行为

- [ ] 6 个控制器、35 个接口路径与出入参与迁移前完全一致
- [ ] `@SpringBootApplication` 扫描根仍为 `com.bluewhale.auth`，所有 Bean 可装配
- [ ] 数据库名 `bluewhale_auth`、Redis 键 `whale:auth:*`、Java 包名未变

## 运行时改名

- [ ] Nacos 服务名 = `whale-service-auth`
- [ ] 可部署 jar = `whale-service-auth.jar`
- [ ] 网关两条路由 `whale-service-auth` / `whale-service-auth-tenant` 均指向 `lb://whale-service-auth`
- [ ] whale-im Feign 默认服务名与配置默认值已改
- [ ] CI matrix module 路径与 service 名已改
- [ ] 启动脚本 jar 路径、smoke 脚本 `lb://` 已改
- [ ] prometheus job_name 已改
- [ ] Dockerfile 默认 `MODULE_PATH` 已改
- [ ] 文档命名表已同步

## 构建与测试

- [ ] `mvn -pl whale-services/whale-service-auth -am -DskipTests compile` BUILD SUCCESS
- [ ] `mvn -pl whale-services/whale-service-auth/whale-service-auth-starter -am test` 全绿
- [ ] `mvn -DskipTests install` 全量 BUILD SUCCESS
- [ ] 残留 grep（`whale-services/whale-auth`、`lb://whale-auth`、`job_name: whale-auth`）计数为 0

## 交付

- [ ] README / 变更说明已记录服务名与模块路径变化
- [ ] 部署侧需重新注册 Nacos 服务名（运维告知）
