# 命名规范（Naming Conventions）

> 关联需求：**REQ-ARCH-004**（统一命名规范：库 / 服务 / 包 / 配置，名字自解释）
> 定稿日期：2026-10-17（Wave 5.1g） ｜ 维护：BlueWhale
> 适用范围：MySQL 库 / 表 / 列、后端 Maven 模块与服务、Java 包与类、Spring 配置项与环境变量、Redis key、Nacos 命名空间、前端包与目录
> 落地门禁：本规范条目纳入 [code-review-checklist.md](checklists/code-review-checklist.md) **E15**，未符合者不得提交

**核心原则：词根统一 + 分层可读。** 看到任何一个库名、服务名、包名，都应能立刻回答三个问题：**属于哪一层（平台公共 / 核心服务 / 业务应用 / 运维 / 基础设施）、哪个域（auth / audit / erp …）、做什么。**

---

## 一、词根与分隔符（总则）

| 用途 | 规则 | 示例 |
|---|---|---|
| 品牌词根（库、Java 包、NPM scope） | `bluewhale`（全小写） | `bluewhale_auth`、`com.bluewhale.auth`、`@bluewhale/whale-ui` |
| 产品词根（模块、服务、NPM 包、配置、Redis 命名空间） | `whale`（全小写） | `whale-service-auth`、`whale.gateway.jwt`、`whale:auth:token:...` |
| 多词分隔（目录 / 模块 / NPM 包 / 容器） | `-`（kebab-case） | `whale-spring-boot-starter` |
| 多词分隔（库 / 表 / 列 / SQL 对象） | `_`（snake_case） | `bluewhale_auth`、`sys_user_role` |
| 多词分隔（Java 包） | `.`（全小写） | `com.bluewhale.auth.security` |
| 多词分隔（Redis key 段） | `:` | `whale:auth:token:blacklist:<hash>` |
| 多词分隔（环境变量 / 常量） | `_` + 全大写 | `WHALE_GATEWAY_JWT_SECRET` |
| 类名 | `UpperCamelCase` | `JwtAuthGlobalFilter` |
| 禁止 | 拼音、无意义缩写（`ctl`、`svc`、`tmp1`）、大小写混搭目录 | —— |

---

## 二、数据库命名

### 2.1 库名

**格式：`bluewhale_<域>`**，全小写 snake_case；`<域>` = 服务短名（服务名去掉 `whale-` 前缀）。

- 一个服务一个库（DB-per-service 自治，微服务数据私有）；
- **基础设施组件同前缀**：Nacos 使用 `bluewhale_nacos`（不叫裸 `nacos`），与业务库保持同一词根，一眼可辨归属；
- 非本平台自建库（如历史遗留 `yc-sts-db-*`）**不改名**，但须在本文件 §八 登记归属，避免误伤；
- 库字符集统一 `utf8mb4 / utf8mb4_general_ci`，连接参数统一
  `characterEncoding=utf8&connectionCollation=utf8mb4_general_ci&serverTimezone=Asia/Shanghai`（REQ-ARCH-002 换库改造时该项按目标库方言重定义）。

### 2.2 表名 / 列名 / 索引名

| 对象 | 规则 | 示例 |
|---|---|---|
| 表名 | 小写 snake_case、**单数**、带域前缀 | `sys_user`、`audit_log`、`api_log`、`outbound_log`、`frontend_log`、`behavior_log`、`erp_order` |
| 前缀语义 | `sys_` 平台内建 RBAC；`audit_` 审计事件；`*_log` 多通道观测日志（Wave 5.10）；`<业务域>_` 业务应用（如 `mu_` 音乐、`vd_` 视频、`lr_` 学习培训考试、`pty_` 党政工会、`mall_` 商城、**`fm_` 论坛**） | —— |
| 关联表 | `<主表>_<从表>`（均取单数） | `sys_user_role`、`sys_role_permission` |
| 列名 | 小写 snake_case；布尔 `is_xxx`；时间 `xxx_time`；数量 `xxx_count`；外键 `<实体>_id` | `is_default`、`create_time`、`tenant_id` |
| 必备列 | `id`（雪花，`BIGINT`）、`create_time`、`update_time`、`deleted`（逻辑删除） | 见 checklist E13 |
| 必备列例外 | **append-only 追加型表**（审计与多通道日志）**不设** `update_time` / `deleted`：写入后不可修改，按保留策略整批归档/清理 | `audit_log`、`api_log`、`outbound_log`、`frontend_log`、`behavior_log` |
| 索引 | `idx_<表简称>_<列>` / `uk_<表简称>_<列>` | `uk_sys_user_username` |
| 禁止 | 大写表名、复数表名（`users`）、无前缀裸表名（`user`） | —— |

---

## 三、后端模块与服务命名

### 3.1 顶层分组（Maven 聚合目录）

| 分组目录 | 定位 | 内容物 |
|---|---|---|
| `whale-parent` | 父 POM | 依赖版本统一、插件与质量门禁配置 |
| `whale-common` | 平台公共组件 | 工具、安全、自动配置 Starter |
| `whale-sdk` | 跨服务 SDK | 供其他服务引用的客户端/上下文/注解包 |
| `whale-services` | 核心服务 | 网关、权限、任务、流程、表单、文件、支付 |
| `whale-registry` | 注册与配置 | 配置中心客户端、注册发现辅助 |
| `whale-ops` | 运维与可观测 | 审计、监控、告警 |
| `whale-apps` | 业务应用 | ERP / MES / PDM / OA 等按行业域 |

### 3.2 命名一致性铁律

同一个服务，下列名称**必须逐字相同**，均形如 `whale-<域>`：

```
Maven 模块目录名 == artifactId == spring.application.name == Nacos 服务名 == 容器/进程名
```

- 网关 `lb://whale-service-auth` 中的 `whale-service-auth` 即 Nacos 服务名，改名即断链 —— 该铁律是 `lb://` 路由可用的前提；
- `<域>` 统一取自 §八 域词表，新增服务须先在此登记域词，再创建模块。

### 3.3 组件类型后缀

| 后缀 | 含义 |
|---|---|
| （无后缀）`whale-<域>` | 可独立部署的服务/应用 |
| `-sdk` | 跨服务引用的 SDK（不部署） |
| `-starter` | Spring Boot 自动配置 Starter |
| `-tools` | 纯工具库 |
| `-parent` | 父 POM |

### 3.4 端口分段（避免冲突，已用端口为现状）

| 区段 | 用途 | 现状占用 |
|---|---|---|
| `8080` | API 网关（唯一入口） | whale-service-gateway |
| `8081–8099` | 核心服务 | auth 8081、job 8082、workflow 8083、form 8084、file 8085、pay 8086（8087 随 `whale-i18n` 删除释放，Wave 5.50）、agent 8090、im 8091、ai 8092（Python，仅内网）、archive 8093、message 8094、**doc 8095（Wave 5.74 M1 骨架）**、**music 8096（Wave 5.75 M0 骨架）**、**scene 8097（Wave 5.77 M1/M2 2D 已落地）**、**video 8098（Wave 5.77 M0–M5 已落地）** |
| `9001–9099` | 业务应用 | erp 9001、mes 9002、pdm 9003、oa 9004、pm 9005、contract 9006、**crm 9007（Wave 5.77 门禁骨架）**、**lms 9008（Wave 5.87 学习培训考试）**、**party 9009（Wave 5.88 党政工会）**、**mall 9010（Wave 5.94 商城）**、**forum 9011（Wave 6.43 员工论坛；8081–8099 已满，例外借用本段，见 service-catalog §七）** |
| `9101–9199` | 运维服务 | audit 9101、monitor 9102、alert 9103 |
| `9201–9299` | 注册/配置类辅助服务 | config 9201 |
| `8848/8849/9848/9849` | Nacos 基础设施（非本平台服务） | Nacos 3.2.4 |

> 新增服务须先在本文登记端口，再写入 `application.yml`。

### 3.5 启动类与配置类

- 启动类：`Whale<域首字母大写>Application`（`WhaleAuthApplication`、`WhaleGatewayApplication`），位于 `com.bluewhale.<域>` 根包；
- 自动配置类：`<能力>AutoConfiguration`（`AuditLogAutoConfiguration`），配 `META-INF/spring/...imports`。

---

## 四、Java 包命名

**格式：`com.bluewhale.<域>[.<层>][.<能力>]`**，全小写；`groupId` 恒为 `com.bluewhale`。

| 维度 | 规则 | 示例 |
|---|---|---|
| 服务包 | `com.bluewhale.<域>`（域取服务短名） | `com.bluewhale.auth`、`com.bluewhale.gateway` |
| 经典三层层名（过渡期，现状） | `controller` / `service` / `service.impl` / `mapper` / `entity` / `dto` / `vo` / `config` / `security` / `jwt` | `com.bluewhale.auth.controller` |
| DDD 层名（目标，REQ-ARCH-001 迁移后） | `interfaces` / `application` / `domain` / `infrastructure` | `com.bluewhale.auth.domain` |
| 公共能力 | `com.bluewhale.common.<能力>` | `common.audit.aop`、`common.lock.annotation`、`common.core.result` |
| 平台公共组件独立包 | `com.bluewhale.tools` / `com.bluewhale.security` | `com.bluewhale.tools.utils` |
| Starter 装配类 | 能力模块 `config` 包 | `TenantContextAutoConfiguration`、`WhaleLogAutoConfiguration` |
| **SDK 模块包** | `com.bluewhale.<域>.sdk`（**以 `.sdk` 结尾**，与主服务包区分） | `com.bluewhale.auth.sdk`、`com.bluewhale.i18n.sdk`（目标，见偏差 N-04） |

类名后缀词表：`*Controller`、`*Service`、`*ServiceImpl`、`*Mapper`、`*Request`、`*Response`、`*VO`、`*Entity/PO`、`*Properties`、`*Filter`、`*Interceptor`、`*Handler`、`*Aspect`、`*AutoConfiguration`、`*Application`。

> 同一限界上下文内**不得混用**经典三层与 DDD 四层；迁移期以「一个聚合整体切换」为粒度（见 [ddd-whale-auth.md](design/ddd-whale-auth.md)）。

---

## 五、配置项与环境变量

| 对象 | 规则 | 示例 |
|---|---|---|
| Spring 配置前缀 | `whale.<域>.<能力>` | `whale.gateway.jwt.white-list`、`whale.auth.jwt.secret` |
| 环境变量（服务私有） | `WHALE_<域>_<键>` 全大写 | `WHALE_GATEWAY_JWT_SECRET` |
| 环境变量（基础设施通用） | `<组件>_<键>` 全大写 | `MYSQL_USER`、`MYSQL_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`NACOS_ADDR`、`NACOS_NAMESPACE` |
| 默认值策略 | 开发可给 `dev` 默认值，生产敏感项必须无默认并强制校验（REQ-SEC-005） | `secret: ${JWT_SECRET:...占位}` |
| Nacos namespace | `whale-<env>`（env ∈ dev / test / prod）**规划中**，现状 `public` | —— |
| Nacos group / dataId | group `WHALE_<域-UPPER>`、dataId `whale-<域>-<env>.yml` **规划中** | —— |

---

## 六、Redis key 命名

**格式：`whale:<域>:<用途>:<标识>`**（全小写，冒号分段，第一段恒为 `whale` 命名空间）。

| 用途 | 规范 key | 现状 |
|---|---|---|
| 令牌黑名单 | `whale:auth:token:blacklist:<sha256摘要>` | 实为 `auth:token:blacklist:<摘要>`（**偏差 N-02**） |
| 国际化缓存 | `whale:i18n:<locale>:<code>` | 已符合（`WhaleMessageSource.buildCacheKey`） |
| 分布式锁 | `whale:<域>:lock:<业务键>` | 待接入 |

- key 无过期时间者须显式说明理由；黑名单 key 的 TTL = 令牌剩余有效期。

---

## 七、前端命名

| 对象 | 规则 | 示例 / 现状 |
|---|---|---|
| 共享包 / 组件库 | `@bluewhale/whale-<域>` | `@bluewhale/whale-ui`、`@bluewhale/whale-common-tools`、`@bluewhale/whale-log-sdk`（Wave B 新建） ✔ |
| 应用包名 | `whale-<域>`（不加 scope） | `whale-platform`、`whale-portal`、`whale-admin`、`whale-ops` ✔ |
| 目录名 | 与包名一致（去掉 scope） | `whale-frontend/admin` → 包名 `whale-admin`（**偏差 N-03**） |
| 微前端分组 | `whale-frontend/platform-apps/`（子应用）、`shared-packages/`（共享包）、`elemental/`（组件库）、`ops/`（运维） | —— |

---

## 八、域词表与全量映射（速查表）

> 「看名字就知道属于哪一部分」的落地依据：新增库/服务/包前先在此登记域词。
> **端口 ↔ 服务 ↔ 组件 ↔ 库 ↔ 对外前缀**的详细清单（含第三方组件端口与偏差登记 C-01~C-08）见 [service-catalog.md](service-catalog.md)（REQ-ARCH-005）。

| 分组 | 模块 / 服务 | Nacos 服务名 | 数据库 | Java 包根 | 端口 |
|---|---|---|---|---|---|
| parent | whale-parent | — | — | — | — |
| common | whale-tools | — | — | `com.bluewhale.tools` | — |
| common | whale-security | — | — | `com.bluewhale.security` | — |
| common | whale-spring-boot-starter | — | — | `com.bluewhale.starter.autoconfigure` + `com.bluewhale.common.*` | — |
| sdk | whale-auth-sdk | — | — | `com.bluewhale.auth.sdk` | — |
| sdk | whale-i18n-sdk | — | — | `com.bluewhale.i18n.sdk`（目标，现为 `com.bluewhale.i18n`） | — |
| services | whale-service-gateway | whale-service-gateway | 无 | `com.bluewhale.gateway` | 8080 |
| services | whale-service-auth | whale-service-auth | `bluewhale_auth` ✔ | `com.bluewhale.auth` | 8081 |
| services | whale-job | whale-job | `bluewhale_job`（规划） | `com.bluewhale.job` | 8082 |
| services | whale-workflow | whale-workflow | `bluewhale_workflow`（规划） | `com.bluewhale.workflow` | 8083 |
| services | whale-form | whale-form | `bluewhale_form`（规划） | `com.bluewhale.form` | 8084 |
| services | whale-service-file | whale-file | `bluewhale_file`（规划） | `com.bluewhale.file` | 8085 |
| services | whale-pay | whale-pay | `bluewhale_pay`（规划） | `com.bluewhale.pay` | 8086 |
| services | whale-message | whale-message | `bluewhale_message` ✔ | `com.bluewhale.message` | 8094 |
| services | **whale-doc**（Wave 5.74 M1 骨架） | `whale-doc` | `bluewhale_doc` | `com.bluewhale.doc` | 8095 |
| services | **whale-music**（Wave 5.75 M1 已落地） | `whale-music` | `bluewhale_music`（DDL 就绪，待执行） | `com.bluewhale.music` | 8096 |
| services | **whale-scene**（Wave 5.77 M1/M2 2D 已落地） | `whale-scene` | `bluewhale_scene` | `com.bluewhale.scene` | 8097 |
| services | **whale-video**（Wave 5.77 M0–M5 已落地） | `whale-video` | `bluewhale_video`（Flyway `V1`–`V4` 就绪，待建库） | `com.bluewhale.video` | 8098 |
| services | ~~whale-i18n~~（已删除，Wave 5.50 下沉 SDK） | — | 各服务库内自建 `sys_i18n_message` | — | — |
| services | **whale-assistant**（Wave 6.01 M0） | `whale-assistant` | `bluewhale_assistant` | `com.bluewhale.assistant` | 8087 |
| services | **whale-iot**（Wave 6.02） | `whale-iot` | `bluewhale_iot` | `com.bluewhale.iot` | 8088 |
| services | **whale-twin**（Wave 6.03） | `whale-twin` | `bluewhale_twin` | `com.bluewhale.twin` | 8089 |
| services | **whale-agent**（Wave 5.47） | `whale-agent` | `bluewhale_agent` | `com.bluewhale.agent` | 8090 |
| services | **whale-im**（Wave 5.52） | `whale-im` | `bluewhale_im` | `com.bluewhale.im` | 8091 |
| services | **whale-archive**（Wave 5.53） | `whale-archive` | `bluewhale_archive` | `com.bluewhale.archive` | 8093 |
| services | **whale-mail**（Wave 5.89） | `whale-mail` | `bluewhale_mail` | `com.bluewhale.mail` | 8099 |
| services | **whale-map**（Wave 5.91） | `whale-map` | `bluewhale_map` | `com.bluewhale.map` | 8100 |
| registry | ~~whale-config~~（**冻结**，F-05 结案：不部署/不路由） | — | 无 | `com.bluewhale.config` | 9201 |
| ops | whale-audit | whale-audit | `bluewhale_audit` ✔ | `com.bluewhale.audit` | 9101 |
| ops | whale-monitor | whale-monitor | 无（只读聚合 Nacos 实例，不落库） | `com.bluewhale.monitor` | 9102 |
| ops | whale-alert | whale-alert | `bluewhale_alert`（规划） | `com.bluewhale.alert` | 9103 |
| apps | whale-erp | whale-erp | `bluewhale_erp`（规划） | `com.bluewhale.erp` | 9001 |
| apps | whale-mes | whale-mes | `bluewhale_mes`（规划） | `com.bluewhale.mes` | 9002 |
| apps | whale-pdm | whale-pdm | `bluewhale_pdm`（规划） | `com.bluewhale.pdm` | 9003 |
| apps | whale-oa | whale-oa | `bluewhale_oa`（规划） | `com.bluewhale.oa` | 9004 |
| apps | whale-pm | whale-pm | `bluewhale_pm`（DDL 就绪，待建库） | `com.bluewhale.pm` | 9005 |
| apps | **whale-contract**（Wave 5.61 M1） | `whale-contract` | `bluewhale_contract` | `com.bluewhale.contract` | 9006 |
| apps | **whale-crm**（Wave 5.77 门禁骨架） | `whale-crm` | `bluewhale_crm` | `com.bluewhale.crm` | 9007 |
| apps | **whale-lms**（Wave 5.87 学习培训考试） | `whale-lms` | `bluewhale_lms` | `com.bluewhale.lms` | 9008 |
| apps | **whale-party**（Wave 5.88 党政工会；原计划并入 OA，改独立） | `whale-party` | `bluewhale_party` | `com.bluewhale.party` | 9009 |
| apps | **whale-mall**（Wave 5.94 商城；一套内核兼容零售 / 经销订货 / 员工福利） | `whale-mall` | `bluewhale_mall` | `com.bluewhale.mall` | 9010 |
| services | **whale-forum**（Wave 6.43 员工论坛「鲸议台」；表前缀 **`fm_`**，端口 9011 为例外登记——8081–8100 已满、8101–8199 归前端子应用段） | `whale-forum` | `bluewhale_forum` | `com.bluewhale.forum` | 9011 |
| infra | Nacos 3.2.4 | — | **`bluewhale_nacos`**（本波次整改，原 `nacos`） | — | 8848 / 8849（9848 / 9849） |
| infra | Redis / MySQL | — | 不建业务库 | — | 6379 / 3306 |

**非本平台库（登记不改名）**：`yc-sts-db-auth`、`yc-sts-db-flow`（外部项目遗留，与 BlueWhale 无关，禁止在未确认归属前改名或删除）。

---

## 九、存量偏差与整改登记

| 编号 | 偏差 | 现状 | 规范要求 | 处置 |
|---|---|---|---|---|
| **N-01** | Nacos 库名缺少统一前缀 | `nacos` | `bluewhale_nacos` | ✅ **已整改（Wave 5.1g）**：停 Nacos → 表迁至 `bluewhale_nacos` → 改 `conf/application.properties` → 重启验证 |
| **N-02** | Redis 黑名单 key 缺 `whale` 命名空间 | `auth:token:blacklist:<摘要>` | `whale:auth:token:blacklist:<摘要>` | ⏳ 待办：需同步改 `whale-security` 常量与 auth 侧、网关侧及单测 |
| **N-03** | 前端目录名与包名不一致 | `whale-frontend/admin`（包名 `whale-admin`） | 目录 `whale-admin` | ⏳ 待办（低优先，涉及 turbo / 构建脚本路径） |
| **N-04** | SDK 包名后缀不统一 | `whale-i18n-sdk` → `com.bluewhale.i18n` | `com.bluewhale.i18n.sdk` | ⏳ 待办：需评估引用方改动量后统一 |

> 偏差项须在 [devlog](devlog/) 与需求台账留痕；整改完成后再回填本表状态。

---

## 十、落地方式

1. **新增即合规**：新增库 / 服务 / 模块 / 包，先在本文件 §八 登记域词与端口，再落代码（REQ-DOC-002 文档先行）；
2. **检查门禁**：纳入 [code-review-checklist.md](checklists/code-review-checklist.md) **E15**，每次变更按项自检并留报告（REQ-QA-002）；
3. **构建门禁**：包名一致性可由 Checkstyle 的 `PackageName` / `OuterTypeFilename` 规则兜底（见 `whale-parent` 检查配置）；
4. **变更控制**：`lb://whale-<域>` 与库名属**契约级命名**，改动须同步更新路由、Nacos 服务名、数据源 URL 与本文档映射表，并在 devlog 记录影响面。
