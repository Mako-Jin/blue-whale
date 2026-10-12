# docs — 文档与契约中心

代码回答"**是什么**"，本目录回答"**为什么**"与"**怎么约定**"。

---

## 一、目录结构

| 目录            | 内容                                   | 何时必须更新                                   |
| --------------- | -------------------------------------- | ---------------------------------------------- |
| `architecture/` | 系统架构、分层图、部署拓扑、关键时序   | 组件划分、分层关系或数据流发生变化时           |
| `api/`          | OpenAPI 契约文件（**跨端唯一真相源**） | 后端接口有任何增删改时（与代码**同一次提交**） |
| `decisions/`    | 架构决策记录（ADR）                    | 做出不可逆或影响面大的技术决策时               |

---

## 二、文档同步规范（硬性要求）

**文档与代码必须同一次提交。** 不允许"先写代码，文档回头补"——回头就是永不。

每次改动，按下表自查必须同步哪些文档：

| 改动类型                                                         | 必须同步的文档                                                                                                                                                             |
| ---------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 新增 / 删除前端应用                                              | 根 `README.md` 结构图 · `web/README.md` 目录结构                                                                                                                           |
| 修改前端规范工具链（ESLint/Prettier/Stylelint…）                 | `web/README.md` 规范章节 · `packages/common-config/README.md`                                                                                                              |
| 新增 / 删除公共包                                                | `web/README.md` · 根 `README.md` · 该包的 `README.md`                                                                                                                      |
| 新增 / 修改公共包对外 API                                        | 该包的 `README.md`                                                                                                                                                         |
| 新增日志上报维度或上报通道                                       | `packages/common-tools/README.md`（`LogRecord` 章节）                                                                                                                      |
| 修改请求传输策略（错误分类、超时、重试、鉴权钩子）               | `packages/common-tools/README.md`（HTTP 章节） · `decisions/0007-前端请求分层与错误模型.md`                                                                                |
| 修改客户端日志上报通道（批量、退避、缓冲上限）                   | `decisions/0004-前端日志上报架构.md` · `packages/common-tools/README.md`（批量通道章节）                                                                                   |
| 新增 / 修改行为事件（埋点）与事件上报                            | `decisions/0011-行为事件管道与前端埋点内核.md` · `packages/common-tools/README.md`（事件内核章节） · `platform/platform-event-console/README.md`                           |
| 新增 / 修改审计日志管道、`@Audited` 标注或审计查询接口            | `decisions/0019-审计日志管道与后端自动产生.md` · `server/README.md`（审计域硬规则表） · `platform/platform-audit-console/README.md`                                        |
| 给写接口补审计（标 `@Audited` + 常量）                            | `decisions/0019` 的后续约束 · `sts-server-auth` 的 `AuthAuditActions` / `AuthResources`                                                                                    |
| 新增 `GlobalExceptionHandler` 的处理器                            | `decisions/0019` 第 5 条（必须调 `ResponseOutcome.markFailure`，否则该类失败会被记成成功）                                                                                   |
| 新增 / 调整 Web 拦截器或其顺序                                    | `decisions/0019` 第 4 条 · `sts-common-web` 的 `WebInterceptorOrder`（**禁用裸数字**）                                                                                       |
| 调整身份拼接（deviceId / userId）、脱敏与屏蔽规则                | `decisions/0011` 第 4 条 · `platform/platform-event-console/README.md`                                                                                                     |
| 新增 / 删除 qiankun 子应用（微前端）                             | `decisions/0012-微前端接入与子应用改造.md` · `web/README.md` · 该子应用的 `README.md`                                                                                      |
| 调整主应用的挂载方式、沙箱策略、全局状态下发或子应用 entry       | `decisions/0012` 第 1 / 2 / 6 / 9 条 · `platform/platform-portal/README.md`                                                                                                |
| 新增 / 调整前端登录态、令牌存取、刷新与权限判定                  | `decisions/0018-权限在微前端中的归属.md` · `packages/common-tools/README.md`（http / micro 章节） · `packages/common-api/README.md` · `platform/platform-portal/README.md` |
| 新增 / 调整权限管理界面（`platform-admin`）                      | `decisions/0018` · `web/README.md` · 该应用的 `README.md`                                                                                                                  |
| 修改三维应用 / WebGL 场景与渲染生命周期                          | `decisions/0013-三维环境应用与WebGL渲染生命周期.md` · `platform/platform-environment/README.md`                                                                            |
| 新增 / 删除三维场景，或改场景注册表、清单契约与切换管线          | `decisions/0014-多场景架构与切换管线.md` · `platform/platform-environment/README.md`                                                                                       |
| 新增 / 修改可操控场景（主人公、相机跟随、右键设点、小地图）      | `decisions/0015-场景交互与主人公操控.md` · `platform/platform-environment/README.md`                                                                                       |
| 新增 / 修改大气与天气（天穹、日月、云、雾与背景联动、环境贴图）  | `decisions/0017-大气与天气的现实映射.md` · `platform/platform-environment/README.md`                                                                                       |
| 在子应用里订阅 / 回写基座全局状态                                | `packages/common-tools/README.md`（micro 章节） · `decisions/0013` 第 7 条                                                                                                 |
| 修改跨应用共享的 Vite 配置片段                                   | `packages/common-config/README.md` · `decisions/0012` 第 10 条                                                                                                             |
| 调整客户端日志的校验 / 截断 / 存储策略                           | `server/README.md`（日志域硬规则表）                                                                                                                                       |
| 修改契约的生成或消费流程（导出脚本、生成器、类型修正）           | `api/README.md` · `decisions/0006-接口契约的生成与消费.md`                                                                                                                 |
| 调整配置分层或新增配置层                                         | `decisions/0003-配置分域与自研边界.md`                                                                                                                                     |
| 新增 / 删除第三方依赖                                            | `web/pnpm-workspace.yaml` 的 `catalog:` · `CONTRIBUTING.md` 依赖治理                                                                                                       |
| 升级后端框架大版本（Spring Boot / JDK 基线）                     | `decisions/0008-Spring-Boot-4升级与JDK21门禁.md` · `server/README.md` 版本一览                                                                                             |
| 新增 / 删除后端模块                                              | `server/README.md` 模块表 · 根 `README.md` 结构图 · `architecture/后端分层与依赖方向.md` 第六节进度表                                                                      |
| 新增 / 调整权限域（身份、访问控制、认证、鉴权）                  | `decisions/0016-权限域服务设计与参照实现的取舍.md` · `server/README.md`（权限域硬规则表）                                                                                  |
| 新增 / 修改口令强度策略、登录锁定与退避、口令防重用               | `decisions/0020-口令与登录安全加固.md` · `server/README.md`（权限域硬规则表） · 强度提示语只能来自 `PasswordPolicy.describe()`                                              |
| 新增"设置口令"的入口（建号 / 重置 / 自助改密）                    | `decisions/0020` 的后续约束（强度 → 防重用 → 写历史，三件缺一不可）                                                                                                         |
| 新增"因操作失败而要留下的记录"                                    | `decisions/0020` 第 5 条（`REQUIRES_NEW` 独立 Bean，否则会被自己的异常回滚掉）                                                                                             |
| 新增 / 修改权限资源目录（菜单 / 页面 / 按钮 / 接口资源）或权限码登记的约束         | `decisions/0021-资源目录接口与权限码登记约束.md` · `server/README.md`（权限域硬规则表） · `api/README.md`（资源域）                                                                 |
| 往 `bw_auth_resource` 加行，或改权限码的形状约束                  | `decisions/0021` 的后续约束 · `AuthTaxonomyTest`（标识形状） · `server/README.md`（权限域硬规则表）                                                                         |
| 新增 / 修改角色管理、"把资源授给角色"的接口                       | `decisions/0022-角色管理与角色资源授权.md` · `server/README.md`（权限域硬规则表） · `api/README.md`（角色域）                                                              |
| 新增 / 修改"把角色授给成员（用户 / 用户组）"的接口，或成员列表的形态 | `decisions/0023-角色成员关系.md` · `server/README.md`（权限域硬规则表） · `api/README.md`（角色域）                                                                        |
| 改判定取数（`AuthorizationMapper`）或授权语义（DENY / 时态 / 角色状态） | `decisions/0022` 第 3~5 条 · `decisions/0023` 第 12 条 · `GrantEffect` / `RelRoleResourceEntity` 的类注释 · **必须落到真实接口的准入结果上做断言**（断言 `/me` 返回了什么是证明不了判定的） |
| 新增用户组 / 组织的接口，或给用户组做嵌套                           | `decisions/0023`「发现但未处理」第 1 条 —— **嵌套组在判定里只跳一层，必须先解决再开放该能力**                                                                              |
| 调整后端分层或依赖方向                                           | `decisions/0005-后端模块划分与依赖约束.md` · `architecture/后端分层与依赖方向.md` · `server/README.md`                                                                     |
| 修改统一响应结构或错误码                                         | `server/README.md` 错误码表 · `docs/api/openapi.json`                                                                                                                      |
| 变更数据源、切换 / 新增数据库 profile                            | `server/README.md` 第六节 · `decisions/0009`（H2 / MySQL）· `decisions/0010`（达梦）                                                                                       |
| 新增 / 修改表结构（迁移脚本）                                    | `server/README.md` 第六节 · 迁移脚本放在表结构所属模块的 `db/migration/<vendor>/`                                                                                          |
| 新增一条建表迁移脚本（**版本号在全仓唯一**，不是各模块从 V1 起） | `decisions/0009` 的「补充」章节 · `server/README.md` 第六节                                                                                                                |
| 新增落库字段（**MySQL 与达梦的保留字都要查，结论不可互推**）     | `decisions/0009` 第 3 条 · `decisions/0010` · `server/README.md` 第六节                                                                                                    |
| 新增一种数据库厂商的支持                                         | `decisions/0010-达梦DM8支持与Flyway的边界.md` · `server/README.md` 第六节与版本一览                                                                                        |
| 新增 / 修改后端接口                                              | `docs/api/openapi.json`（用 `scripts/export-openapi.sh` 导出）→ `pnpm --filter common-api gen:api`                                                                         |
| 新增 / 删除 `scripts/` 下的脚本                                  | `scripts/README.md` 脚本表                                                                                                                                                 |
| 变更顶层目录或横向层                                             | 根 `README.md`                                                                                                                                                             |
| 技术选型 / 架构决策                                              | 新增 `docs/decisions/NNNN-*.md`                                                                                                                                            |
| 引入新的全局工程约定                                             | `CONTRIBUTING.md`                                                                                                                                                          |

---

## 三、ADR（架构决策记录）

命名格式：

```
docs/decisions/NNNN-简短标题.md
```

- `NNNN` 为四位序号，从 `0001` 递增，**只增不改**。
- 模板见 `decisions/TEMPLATE.md`。
- 结构固定三段：**背景**（为什么必须决策）→ **决策**（选了什么）→ **后果**（得到什么、付出什么）。

> 决策被推翻时，**不要删改原 ADR**，而是新写一篇说明"取代 000X"。

---

## 四、当前文档索引

| 文档                                                | 说明                                                                                                                                                                                                                                                                                                                                                                                                                  |
| --------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `decisions/0001-架构基线与技术选型.md`              | 仓库结构、前后端分离、技术栈基线                                                                                                                                                                                                                                                                                                                                                                                      |
| `decisions/0002-前端公共包与公共依赖治理.md`        | pnpm catalog、`common-*` 包划分、logger 重写决策                                                                                                                                                                                                                                                                                                                                                                      |
| `decisions/0003-配置分域与自研边界.md`              | 配置三层分域、自研边界、客户端配置五条底线                                                                                                                                                                                                                                                                                                                                                                            |
| `decisions/0004-前端日志上报架构.md`                | 采集与传输分层、`LogRecord` 契约、采样与脱敏约束                                                                                                                                                                                                                                                                                                                                                                      |
| `decisions/0005-后端模块划分与依赖约束.md`          | 五层依赖方向、groupId 分级、分包方式、统一响应契约                                                                                                                                                                                                                                                                                                                                                                    |
| `decisions/0006-接口契约的生成与消费.md`            | 契约由代码生成、生成物入库、任意值字段的类型修正                                                                                                                                                                                                                                                                                                                                                                      |
| `decisions/0007-前端请求分层与错误模型.md`          | 传输内核与契约装配的分工、六类错误、重试幂等边界                                                                                                                                                                                                                                                                                                                                                                      |
| `decisions/0008-Spring-Boot-4升级与JDK21门禁.md`    | Boot 4.1 升级的破坏性变更清单、starter 坐标对应关系、JDK 21 构建门禁                                                                                                                                                                                                                                                                                                                                                  |
| `decisions/0009-多数据源共存与Flyway迁移.md`        | H2 / MySQL 双 profile、Flyway 迁移按厂商分目录、MySQL 保留字与 MP 别名的双重陷阱                                                                                                                                                                                                                                                                                                                                      |
| `decisions/0010-达梦DM8支持与Flyway的边界.md`       | 国产库达梦 DM8 接入、Flyway 无达梦模块时的取舍、达梦方言差异与**未验证清单**                                                                                                                                                                                                                                                                                                                                          |
| `decisions/0011-行为事件管道与前端埋点内核.md`      | 行为事件是第三条独立管道、传输通道与脱敏逻辑的下沉复用、语义化 `track()` 与内部总线、事件名/双时间戳/属性版本的永久契约、卸载兜底、大数据接入预留点                                                                                                                                                                                                                                                                   |
| `decisions/0012-微前端接入与子应用改造.md`          | 主应用用 `loadMicroApp` 挂载、关 JS 沙箱的理由、`vite-plugin-qiankun` 的取舍、子应用必须保留独立运行分支、CORS 与端口锁定、全局状态下发、样式隔离不指望框架、preamble 修复、**React 组件级热更新的已知限制**                                                                                                                                                                                                          |
| `decisions/0013-三维环境应用与WebGL渲染生命周期.md` | 环境应用独立成域的理由、画布与上下文同生共死（StrictMode 双跑的坑）、`forceContextLoss` 与上下文上限、GPU 资源可断言释放、`Timer`/`PCFShadowMap` 替代、全局状态订阅的多路复用修复                                                                                                                                                                                                                                     |
| `decisions/0014-多场景架构与切换管线.md`            | 场景"实现"与"元数据"分置（ADR-0003 的应用）、注册表编译期兜底、清单元数据契约与三条护栏、切场景只换场景图不换渲染器、卡片式选择器                                                                                                                                                                                                                                                                                     |
| `decisions/0015-场景交互与主人公操控.md`            | 交互控制是场景的能力（`createControls` 可选契约）、相机"整体平移"跟随、移动算法独立成纯内核、右键打地面平面不打网格、小地图自绘 canvas 不走 state、正北朝上、地物与碰撞矩形同源、**未做寻路与移动动画**                                                                                                                                                                                                               |
| `decisions/0016-权限域服务设计与参照实现的取舍.md`  | 权限域作为 `sts-server-auth` 落地、表名与主键约定、逻辑删除统一语义、错误码段位、不引 Spring Security 与 JWT 库（JDK 自研 HS512 + PBKDF2）、`@RequiresPermission` 鉴权入口、刷新令牌轮换、种子数据默认关闭、**未做数据权限与网关校验**                                                                                                                                                                                |
| `decisions/0017-大气与天气的现实映射.md`            | 环境参数是数据、大气是**场景的可选能力**（与 `createControls` 同级）、纯内核四层（`solar`/`palette`/`sample`/`rig`）、**高度角是唯一自变量**（日出日落对拍天文数据）、雾距给系数不给绝对值、夜间主光取反成月亮、动态 IBL 的"纯天穹捕获 + 双重节流 + 每帧补检查"、时间流逝由舞台推进而界面读回、`SceneMeta.climate`、默认取景俯角必须小于垂直半视角、**未做雨雪霜与天气状态机**                                        |
| `decisions/0018-权限在微前端中的归属.md`            | 权限的三件事分开归属（会话 / 判定 / 管理层）、会话实现下沉 `packages/` 而基座只做装配（子应用要保留独立运行分支）、权限判定单点计算经全局状态下发、管理界面独立成 `platform-admin`、**不开认证子应用**（首屏零阻塞与鸡生蛋）、access 放内存 + refresh 持久化、**401 刷新必须单飞**（否则轮换 + 重放检测会把并发误判成攻击）、权限不在令牌里故必须做会话恢复、**子应用发已鉴权请求经基座下发的认证桥（不下发令牌值）** |
| `decisions/0019-审计日志管道与后端自动产生.md`      | 第三条管道落地：**不可篡改靠"接口不存在"**（无写接口 / 无写方法 / 无 `deleted` / 无 `update_*`）、显式 `@Audited` 而非拦所有非 GET、`sts-components` 层首次落地（**刻意不带 `-starter`**）、**审计拦截器必须排在鉴权之前**（Spring 的 `interceptorIndex` 语义）、`ResponseOutcome` 中立通道（`afterCompletion` 的 `exception` 恒为 null 且业务失败仍 200）、`REQUIRES_NEW` 事务、**资源 ≠ 权限码 ≠ 动作**三套标识、只记服务端时间且匿名不记、控制台形态跟着管道产生方式走 |
| `decisions/0020-口令与登录安全加固.md`              | 对参照实现的**第二次回访**：先辨真伪（`PasswordPolicy` 全仓无调用方、`PasswordHistoryEntity` 无 Repository、`CaptchaService` 是空接口、`authentication()` 返回 null），再借鉴"把策略模型接到已通的链路上" —— **口令强度策略**（不设"复杂度等级"这层间接、返回全部违反项、**强度只在策略一处表达**）、**口令防重用**（append-only 不继承 `BaseEntity`、**按条数裁剪而非时间过期**）、**指数退避锁定**（只加 `lock_level` 一列不加新表）；**并修掉一处静默缺陷：失败计数被自己的异常回滚，登录锁定从未生效** → 独立事务 `LoginAttemptRecorder`（与 `AuditRecorder` 同一模式）；端到端测试**刻意不加 `@Transactional`**，否则该缺陷会被完全掩盖 |
| `decisions/0021-资源目录接口与权限码登记约束.md`         | 资源目录（`bw_auth_resource`）的增删改查与树：**权限码创建后不可修改**、标识形状由 DTO 注解承担（与 `AuthTaxonomyTest` 同一正则）、删除的两道拒绝（有子资源 / 已被角色引用 `40915`）、**软删的行仍占唯一标识 ⇒ 重建走复活**（否则撞 `uk_bw_auth_resource_code` 落到兜底 `50000`）、树对孤儿与成环降级为平铺；并记下两处发现（`AuthorizationMapper` 注释与 SQL 不一致、计数型文档会随权限数漂）                                                                                                                                                                            |
| `decisions/0022-角色管理与角色资源授权.md`         | RBAC 判定链的**配置侧闭环**：角色 CRUD 与"把资源授给角色"（授权挂在角色下的子资源、授权是 upsert 并清理同键重复行、撤销从未授过返回 `40407`）、删除角色三道拒绝（系统内置 `40917` / 有子角色 `40913` / 有成员 `40916`，**自身授权一并撤销**）、系统内置角色不可停用或改分类（改分类是一条绕过）、软删占用 `(tenant_id, role_code)` ⇒ 复活且复活出来是空角色；**并修掉判定侧三处静默缺陷**：DENY 此前只滤"行"、跨角色失效（改按资源聚合 + `HAVING`）、漏掉 `ro.status` 使"停用角色"成假按钮、`effective_from/to` 从未进判定（时间作为入参而非 `NOW()`）；另记 `updateById` 的 `NOT_NULL` 策略清不掉字段（授权生效区间清不掉 = 重授权仍无权限） |
| `decisions/0023-角色成员关系.md`                   | RBAC 判定链**配置侧的最后一环**：把角色授给成员（用户 / 用户组），`/roles/{id}/members` 三条接口；**撤销路径必须带 `memberType`**（用户与用户组是两个独立自增序列、会撞号，只用 `memberId` 会撤错对象且表现为"权限凭空消失"）、`memberType` 必填不给默认值（默认 `USER` 会把漏填变成"悄悄授给一个同号用户"）、多态表无外键 ⇒ 应用层保证存在性、**成员必须与角色同租户**（判定两侧都限租户，跨租户的关系永远匹配不上 = 静默不可达）、授予是 upsert + 显式 `set` 清时间窗、成员列表分页但**不做按名搜索**（名字在另一张表，先解析再分页会让 `total` 失真）、`effectiveNow` 只表达时态不压入角色状态；复用 `auth:role:update` 但**审计动作分开**（`grant_member` / `revoke_member`，因为把角色授给人是**直接提权**）、复活角色时一并清空成员关系；**首次验证组链路**（用户 → 用户组 → 角色 → 资源）与**成员关系上的时态**；另记 `AuthorizationMapper` 的**嵌套组只跳一层**（注释原文声称支持嵌套，属误导） |
| `architecture/后端分层与依赖方向.md`                | 后端分层落地细则、依赖矩阵、前后端结构对照 |
| `api/README.md`                                     | 契约接口清单、更新流程、任意值字段的处理                                                                                                                                                                                                                                                                                                                                                                              |
| `../web/packages/common-config/README.md`           | 共享工程配置（ESLint / Stylelint / TypeScript / Vite）                                                                                                                                                                                                                                                                                                                                                                |
| `../web/packages/common-tools/README.md`            | 公共能力包（logger / env / http / json / report / events / micro）使用说明与修复清单                                                                                                                                                                                                                                                                                                                                  |
| `../web/packages/common-api/README.md`              | 前端契约包的使用方式与分层边界                                                                                                                                                                                                                                                                                                                                                                                        |
| `../web/platform/platform-portal/README.md`         | qiankun 主应用：挂载方式、全局状态、登录态装配、认证桥、新增子应用的步骤                                                                                                                                                                                                                                                                                                                                              |
| `../web/platform/platform-admin/README.md`          | 权限管理子应用：两种运行方式、认证端口归一、权限快照与按钮级显隐                                                                                                                                                                                                                                                                                                                                                      |
| `../web/platform/platform-audit-console/README.md`  | 审计日志控制台：**只读**（SDK 无写方法）、按动作聚合与时间窗、演练区（真实写一次 → 等记录出现）、与另两个控制台的形态差异                                                                                                                                                                                                                                                                                             |
| `../web/platform/platform-environment/README.md`    | 三维环境应用：两种运行方式一条渲染路径、`src/scene/` 分层、渲染生命周期四条硬规则                                                                                                                                                                                                                                                                                                                                     |
| `../scripts/README.md`                              | 跨端脚本清单（契约导出 / 达梦初始化 / 微前端端到端验证 / 多场景切换验证 / 主人公操控验证 / 大气验证 / 登录态与权限验证 / 审计管道验证）与硬性约束                                                                                                                                                                                                                                                                    |
| `../server/README.md`                               | 后端模块表、统一响应与错误码、日志域硬规则、构建命令                                                                                                                                                                                                                                                                                                                                                                  |

---

## 五、写作约定

1. **中文为主**，专有名词保留英文原文（如 `platform-portal`、`sts-common`）。
2. 目录结构用代码块，让读者能直接复制路径。
3. 表格优先于长段落——大多数规范本质是"对照表"。
4. 换行符统一 LF（见根目录 `.gitattributes`）。
