# docs/api — 前后端契约

本目录存放**跨端唯一真相源**：OpenAPI 3.1 契约文件。

```
docs/api/openapi.json
```

它由 `scripts/export-openapi.sh` 从**运行中的应用**导出，不是手写的。原因与取舍见 `docs/decisions/0006-接口契约的生成与消费.md`。

---

## 一、当前接口清单

契约版本 `0.1.0`，标题「BlueWhale 服务端 API」，共 **68 个接口**（**44 条路径** / **101 个数据模型**）。

> 上面这一句里的三个数、下面这张按模块的汇总表、以及本节逐域列出的**每一条** `方法 + 路径`，
> 都由 `scripts/verify-api-contract.mjs` 钉在 `docs/api/openapi.json` 上（零前置，CI 的 `guards` job 里跑，
> 见 `docs/decisions/0040`）：**改了清单要同一次提交改对**，改了这一节的**版式**
> （`## 一、` 的标题措辞、`### 域名（模块名）` 的写法、表格列顺序）还要**同改那个脚本** ——
> 它按结构解析，解析不出来就**非零退出**（宁可假红、不可假绿）。
>
> 它们是「活文档不写会变的计数」（`docs/decisions/0039`）的**唯一有据例外**，不是漏网之鱼：
> 那条规则的后续约束写的是"**有权威源就钉住它**"，而不是"删掉它"。模型那栏只核**总数**
> （100 = `components.schemas` 的条目数）、**不核名字** —— 理由见 `0040` 第 5 条。

| 域       | 模块                | 接口数 | 说明                                        |
| -------- | ------------------- | ------ | ------------------------------------------- |
| 日志     | `sts-server-log`    | 5      | 客户端日志上报 / 查询 / 聚合                |
| 行为事件 | `sts-server-track`  | 4      | 客户端行为事件上报 / 查询 / 聚合            |
| 权限     | `sts-server-auth`   | 55     | 认证 5 + 租户 5 + 用户 9 + 资源 6 + 角色 11 + 用户组 8 + 组织 11（详见下表） |
| 审计     | `sts-server-audit`  | 3      | **只读**：分页 / 详情 / 按动作聚合          |
| 系统     | `sts-app-server`    | 1      | 健康检查                                    |

### 日志域（`sts-server-log`）

| 方法   | 路径                               | 说明                                                |
| ------ | ---------------------------------- | --------------------------------------------------- |
| `POST` | `/api/logs/client`                 | 批量上报客户端日志，单批最多 500 条                 |
| `GET`  | `/api/logs/client`                 | 分页查询，支持级别 / 命名空间 / 关键词 / 时间窗过滤 |
| `GET`  | `/api/logs/client/{id}`            | 查询单条日志详情                                    |
| `GET`  | `/api/logs/client/trace/{traceId}` | 按链路标识取整条链路，按时间升序                    |
| `GET`  | `/api/logs/client/stats`           | 按级别聚合统计                                      |

### 行为事件域（`sts-server-track`）

| 方法   | 路径                       | 说明                                                                            |
| ------ | -------------------------- | ------------------------------------------------------------------------------- |
| `POST` | `/api/events/client`       | 批量上报行为事件，单批最多 500 条；逐条校验事件名，返回 `accepted` / `rejected` |
| `GET`  | `/api/events/client`       | 分页查询，支持事件名 / 用户 / 设备 / 会话 / 应用 / 时间窗过滤                   |
| `GET`  | `/api/events/client/{id}`  | 查询单条事件详情                                                                |
| `GET`  | `/api/events/client/stats` | 按**事件名**分组聚合                                                            |

> **聚合接口刻意不接受 `eventName` 过滤参数。** 它本身就是"按事件名分组"，再传一个 `eventName` 去过滤是自相矛盾的；要看单个事件名的量，用分页查询的 `total`。

> **排序与时间窗一律用服务端接收时间**（`serverTime`）。客户端上报的 `clientTime` 也存下来，但只用于展示与归因参考 —— 用户会改系统时间。

> **一个易踩的改名**：前端 `EventEnvelope` 里的字段叫 `clientTime`，契约里叫 `timestamp`（与日志域的 `ClientLogItem` 对齐）。改名发生在接线层（`platform-event-console/src/events.ts` 的 `toReportItem`）。**字段名写错不会有任何报错** —— 请求照样 200、事件照样落库，只有客户端时间这一列静默为空。前后端字段名不一致是这类"静默丢字段"的高发区。

### 系统域（`sts-app-server`）

| 方法  | 路径          | 说明                           |
| ----- | ------------- | ------------------------------ |
| `GET` | `/api/health` | 服务健康检查，含数据库探活结果 |

### 权限域（`sts-server-auth`）

**认证**（`/api/auth/*`）—— 唯一的公开入口是 `login` / `refresh` / `logout`；其余需带 `Authorization: Bearer <access>`。

| 方法   | 路径                 | 所需权限 | 说明 |
| ------ | -------------------- | -------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/api/auth/login`    | 公开     | 登录，签发 access + refresh。**刻意不区分"账号不存在"与"口令错误"** |
| `POST` | `/api/auth/refresh`  | 公开     | 刷新令牌。**轮换 + 重放检测**：已作废的 jti 再次使用 → 撤销该用户全部刷新令牌 |
| `POST` | `/api/auth/logout`   | 公开     | 登出（幂等）。不传令牌则撤销该用户全部刷新令牌 |
| `GET`  | `/api/auth/me`       | 仅需登录 | 当前登录者信息，**含其全部权限标识**。前端由基座单点取用后经全局状态下发，子应用**不得自行调用**（见 `docs/decisions/0018`） |
| `PUT`  | `/api/auth/password` | 仅需登录 | 自助改密（需当前口令）。成功后撤销该用户全部刷新令牌 —— 需重新登录 |

**租户**

| 方法     | 路径                     | 所需权限             |
| -------- | ------------------------ | -------------------- |
| `GET`    | `/api/auth/tenants`      | `auth:tenant:read`   |
| `POST`   | `/api/auth/tenants`      | `auth:tenant:create` |
| `GET`    | `/api/auth/tenants/{id}` | `auth:tenant:read`   |
| `PUT`    | `/api/auth/tenants/{id}` | `auth:tenant:update` |
| `DELETE` | `/api/auth/tenants/{id}` | `auth:tenant:delete` |

**用户**

| 方法     | 路径                            | 所需权限           | 说明                               |
| -------- | ------------------------------- | ------------------ | ---------------------------------- |
| `GET`    | `/api/auth/users`               | `auth:user:read`   | 分页查询，受租户隔离               |
| `POST`   | `/api/auth/users`               | `auth:user:create` | 新建，租户默认取当前登录者         |
| `GET`    | `/api/auth/users/{id}`          | `auth:user:read`   |                                    |
| `PUT`    | `/api/auth/users/{id}`          | `auth:user:update` |                                    |
| `DELETE` | `/api/auth/users/{id}`          | `auth:user:delete` | **不能删自己**                     |
| `POST`   | `/api/auth/users/{id}/password` | `auth:user:update` | 重置口令，**置为需改密并解锁账号** |
| `GET`    | `/api/auth/users/{id}/roles`    | `auth:user:read`   | **反查角色来源**（直接授予 + 经用户组授予）。**停用的角色 / 停用的组 / 已过期的授权照原样返回** —— 它们正是「为什么没有权限」的答案 |
| `GET`    | `/api/auth/users/{id}/data-scope` | `auth:user:read`  | **反查数据范围**：合并该用户全部生效角色的 `data_scope`（**取最宽**的一档）。`precise=true` 表示**这一档已经能被翻译成一组组织**（`DEPT` 系列在该用户有**有效主岗**时成立），此时一并下发 `primaryOrgId` / `primaryOrgName` / `primaryOrgFullName` 与 `visibleOrgIds`；`precise=false` 表示算不出（没有归属 / 归属已过期 / 主岗组织已停用，或 `CUSTOM` 的 `filter_json` 未实现），此时已接入的查询保守地按「仅本人」处理。**注意 `precise` 说的是"能求值"，不是"已经有查询按它过滤"**（当前消费方只有本接口与反查，审计可见性**刻意不接**，见 `docs/decisions/0030` 第 8~10 条） |
| `GET`    | `/api/auth/users/{id}/organizations` | `auth:user:read` | **反查组织归属**：这个人在哪些组织下，以及哪一条是主岗。带 `orgStatus` 与 `effectiveNow` —— 「我明明是主岗、为什么数据范围还是仅本人」的答案往往就在这两列上。**不过滤停用 / 过期**（与 `/roles` 反查同一条：那正是"为什么没生效"的答案） |

**资源目录**（权限资源：菜单 / 页面 / 按钮 / 接口 / 数据）

| 方法     | 路径                       | 所需权限               | 说明                                                                      |
| -------- | -------------------------- | ---------------------- | ------------------------------------------------------------------------- |
| `GET`    | `/api/auth/resources`      | `auth:resource:read`   | 分页平铺列表。按 `keyword` / `resourceType` / `parentCode` / `isEnabled` 过滤 |
| `GET`    | `/api/auth/resources/tree` | `auth:resource:read`   | 按 `parentCode` 组装的树，全量返回。**孤儿与成环节点按根返回，不丢弃**    |
| `GET`    | `/api/auth/resources/{id}` | `auth:resource:read`   |                                                                           |
| `POST`   | `/api/auth/resources`      | `auth:resource:create` | 标识须为「域:资源:动作」三段小写。**若该标识曾被删除，则恢复原记录而非新建** |
| `PUT`    | `/api/auth/resources/{id}` | `auth:resource:update` | **资源标识不可修改**；父资源不能是自身或自身的下级（`40913`）             |
| `DELETE` | `/api/auth/resources/{id}` | `auth:resource:delete` | 逻辑删除。有子资源（`40913`）或**已被角色引用**（`40915`）时拒绝          |

> 这张表里存的**就是权限码**（`auth:user:create`），不是资源标识（`auth:user`）。
> 两者形状相近、混用不报错 —— 见 `docs/decisions/0021`。
>
> **权限标识的写法与取值**是跨端约定，见 `sts-server-auth` 的 `AuthPermissions` 与 ADR-0016；`/api/auth/me` 返回的 `permissions` 与这里逐字一致。

**角色**（角色本身，以及**把资源授给角色**）

| 方法     | 路径                                        | 所需权限           | 说明                                                                                     |
| -------- | ------------------------------------------- | ------------------ | ---------------------------------------------------------------------------------------- |
| `GET`    | `/api/auth/roles`                           | `auth:role:read`   | 分页列表。按 `keyword`（编码 / 名称） / `status` / `category` 过滤，按当前租户限定         |
| `GET`    | `/api/auth/roles/{id}`                      | `auth:role:read`   |                                                                                          |
| `POST`   | `/api/auth/roles`                           | `auth:role:create` | **若编码曾被已删除的角色占用，则恢复原记录**（且复活出来的是空角色）                       |
| `PUT`    | `/api/auth/roles/{id}`                      | `auth:role:update` | **角色编码与所属租户不可修改**；系统内置角色不可停用、不可改分类                            |
| `DELETE` | `/api/auth/roles/{id}`                      | `auth:role:delete` | 逻辑删除。系统内置（`40917`）/ 有子角色（`40913`）/ **有成员（`40916`）** 时拒绝             |
| `GET`    | `/api/auth/roles/{id}/resources`            | `auth:role:read`   | 该角色已授予的资源，含 `effect` / `priority` / 生效区间 / **`effectiveNow`（派生）**        |
| `POST`   | `/api/auth/roles/{id}/resources`            | `auth:role:update` | 授予。按 (角色, 资源) **upsert**：重复调用改效果而不是堆行；`effect` 取 `ALLOW` / `DENY`     |
| `DELETE` | `/api/auth/roles/{id}/resources/{resourceId}` | `auth:role:update` | 撤销。**从未授予过返回 `40407`**，不与撤销成功混淆                                          |

**角色成员关系**（把角色授给用户 / 用户组）

| 方法     | 路径                                                      | 所需权限           | 说明                                                                                       |
| -------- | --------------------------------------------------------- | ------------------ | ------------------------------------------------------------------------------------------ |
| `GET`    | `/api/auth/roles/{id}/members`                            | `auth:role:read`   | **分页**。按 `memberType`（`USER` / `GROUP`）过滤；**不做按名搜索**（名字在另一张表，先解析再分页会让 `total` 失真） |
| `POST`   | `/api/auth/roles/{id}/members`                            | `auth:role:update` | 授予。按 (角色, 成员类型, 成员主键) **upsert**：重复调用改时间窗而不是堆行；`memberType` **必填**、成员必须存在且**与角色同租户** |
| `DELETE` | `/api/auth/roles/{id}/members/{memberType}/{memberId}`    | `auth:role:update` | 解除。**从未授过返回 `40408`**；两边路径段都不能少                                                   |

> **授权挂在角色下**（`/roles/{id}/resources`）而不是单开平级资源：授权关系是角色的内部边，
> 没有脱离角色独立存在的意义，也没有独立生命周期（不存在"无角色的授权"）。
> **成员关系同理**（`/roles/{id}/members`）—— 不存在"没有角色的成员关系"。
>
> **`DENY` 是"排除"的表达方式**，判定按资源聚合、**一条命中即拒绝**，
> 因此它在**多个角色之间**才成立（角色 A 允许 + 角色 B 拒绝 ⇒ 拒绝）。
> 这条语义是 ADR-0022 才真正实现的 —— 见 `docs/decisions/0022`。
>
> **撤销成员必须带 `memberType`**：`member_id` 是多态列，只在它所指的那张表里有意义 ——
> 用户与用户组是两个独立的自增序列、会撞号，只用 `memberId` 会**撤错对象**，
> 而"撤错了"表现为权限凭空消失、没有报错也没有痕迹。见 `docs/decisions/0023` 第 2 条。
>
> **`memberType` 必填、不给默认值**：默认 `USER` 会把"想授用户组但漏写了类型"
> 变成"悄悄授给了一个恰好同号的用户"，而请求里没有任何一处写错过（同第 3 条）。
>
> **`effectiveNow` 只表达关系自身的时态**，不把角色状态、成员状态压进去：
> 一段成员关系是否真的在产生权限由三个独立开关共同决定，
> 压成一个布尔会让"到底哪个开关关着"变成不可见。
>
> **用户组的管理接口已于同批落地**（见下表），因此 `memberType = GROUP` 的成员现在
> 可以经接口配置 —— 不再需要种子或直接写库。
> **嵌套用户组在判定里只跳一层**，见 `docs/decisions/0023`「发现但未处理」第 1 条
> 与 `docs/decisions/0024` 第 3 条。
> **资源目录**（ADR-0021）、**角色管理**（ADR-0022）、**角色成员关系**（ADR-0023）、
> **用户组管理与反查**（ADR-0024）均已落地 —— 配置侧就此闭环。

**用户组**（用户组本身，以及**把用户加进组 / 移出组**）

| 方法     | 路径                                                            | 所需权限                 | 说明                                                                                     |
| -------- | --------------------------------------------------------------- | ------------------------ | ---------------------------------------------------------------------------------------- |
| `GET`    | `/api/auth/user-groups`                                         | `auth:user_group:read`   | 分页列表。按 `keyword`（编码 / 名称） / `category` / `enabled` / `parentId` 过滤          |
| `GET`    | `/api/auth/user-groups/{id}`                                    | `auth:user_group:read`   |                                                                                          |
| `POST`   | `/api/auth/user-groups`                                         | `auth:user_group:create` | **若编码曾被已删除的用户组占用，则恢复原记录**（且复活出来的是**空组**：无成员、无角色）  |
| `PUT`    | `/api/auth/user-groups/{id}`                                    | `auth:user_group:update` | **编码与所属租户不可修改**；`isEnabled` 是**真开关**，停用后该组立刻不再产生权限          |
| `DELETE` | `/api/auth/user-groups/{id}`                                    | `auth:user_group:delete` | 逻辑删除。有成员（`40918`）/ 有子组（`40913`）时拒绝；**该组自己持有的角色会被一并撤销**   |
| `GET`    | `/api/auth/user-groups/{id}/members`                            | `auth:user_group:read`   | **分页**。可按 `memberType` 过滤；成员名跨表解析，因此不提供按名搜索                       |
| `POST`   | `/api/auth/user-groups/{id}/members`                            | `auth:user_group:update` | 授予。`memberType` **当前只接受 `USER`**（传 `GROUP` 返回 `40001`）；按 (组, 成员类型, 成员主键) **upsert** |
| `DELETE` | `/api/auth/user-groups/{id}/members/{memberType}/{memberId}`    | `auth:user_group:update` | 解除。**本来就不在组里返回 `40409`**；两边路径段都不能少                                    |

> **成员关系挂在用户组下**，与角色的两条关系边同一条理由：成员关系是用户组的**内部边**，
> 不存在"没有用户组的成员关系"。
>
> **路径写 `user-groups`（连字符），权限码写 `auth:user_group:*`（下划线）—— 两处不同形是刻意的**：
> URL 给人看、标识给程序与后台配置用；标识的字符集被 `AuthTaxonomyTest` 钉死为 `[a-z][a-z0-9_]*`，
> 放宽它会让"用标识当键"的地方处处需要转义。这是全仓唯一一处两处不同形。
>
> **用户组只支持静态组**：`filterJson` / `orgScopeJson` 两列**连入参都不接受**，
> `category` 传 `DYNAMIC` 返回 `40001` —— 动态成员规则求值未实现，
> 接受它最坏的后果是调用方**以为它生效了**。
>
> **`memberType` 当前只接受 `USER`**：组的嵌套在判定链路里只跳一层，
> 接受它会得到一条"成员列表里看得见、判定里完全不参与"的关系（一个假开关）。
> 补嵌套需要 `WITH RECURSIVE`，三套方言写法不一致、达梦未验证，见 `docs/decisions/0024` 第 3 条。
>
> **`isEnabled` 是真开关**：判定 SQL 连表检查组的可用性（`docs/decisions/0024` 第 4 条 ——
> 此前它是个摆设，判定从不读它）。
>
> **组成员关系与角色成员关系形状对齐，但不共用 VO**：取值域不同
> （角色成员允许 `USER` / `GROUP`，组成员当前只允许 `USER`）、名字解析来源不同。

### 组织域（`sts-server-auth`）

**组织树 + 成员归属 + 主岗**，是 `DEPT` / `DEPT_AND_CHILD` 数据范围的**取值来源**
（见 `docs/decisions/0030`）。

| 方法     | 路径                                                          | 所需权限           | 说明                                                                                                                                           |
| -------- | ------------------------------------------------------------- | ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET`    | `/api/auth/orgs`                                              | `auth:org:read`    | 分页平铺列表。按 `keyword`（编码 / 名称 / 全称） / `status` / `orgType` / `parentId`（**直接下级**）过滤，按当前租户限定                        |
| `GET`    | `/api/auth/orgs/tree`                                         | `auth:org:read`    | 一次返回当前租户的整棵森林（**含已停用节点**，由界面置灰）；传 `rootId` 只看一棵子树。**父不在结果集里的节点按根返回**（孤儿）                    |
| `GET`    | `/api/auth/orgs/{id}`                                         | `auth:org:read`    | 详情。含三个派生列（`orgLevel` / `codePath` / `fullName`）与 `hasChild`                                                                        |
| `POST`   | `/api/auth/orgs`                                              | `auth:org:create`  | 新增。**若编码曾被已删除的组织占用则恢复原记录**，且复活出来的是**空部门**（名下的用户归属一并清空）                                            |
| `PUT`    | `/api/auth/orgs/{id}`                                         | `auth:org:update`  | 修改资料。**编码与所属租户不可修改**；改名会重写整棵子树的 `fullName`；**带与原值不同的 `parentId` 返回 `40001`**（移动走下面那条）              |
| `PUT`    | `/api/auth/orgs/{id}/parent`                                  | `auth:org:update`  | **移动节点**（传空 body 表示提升为顶级）。重写整棵子树的三个派生列 ⇒ **子树上每个人的可见范围同时改变**；挂到自己或自己的后代下返回 `40913`      |
| `DELETE` | `/api/auth/orgs/{id}`                                         | `auth:org:delete`  | 逻辑删除。有下级（`40913`）/ **有成员归属（`40919`）** 时拒绝；**没有级联，也不提供"连人一起删"**                                                |
| `GET`    | `/api/auth/orgs/{id}/members`                                 | `auth:org:read`    | **分页**。可按 `primaryOnly` 只看主岗；成员名跨表解析，因此不提供按名搜索                                                                       |
| `POST`   | `/api/auth/orgs/{id}/members`                                 | `auth:org:update`  | 把用户挂到组织下。同一 (组织, 用户) 只保留一条有效归属。不传 `isPrimaryPosition` 时：此人没有别的主岗则自动设为主岗，**已有主岗则保持不变**       |
| `PUT`    | `/api/auth/orgs/{id}/members/{userId}/primary`                | `auth:org:update`  | **把某人的组织归属设为主岗** —— 不改变任何计数，却**直接改变这个人的 `DEPT` 数据范围**（故有独立审计动作）。会把该用户别处主岗全部降级；无归属返回 `40410` |
| `DELETE` | `/api/auth/orgs/{id}/members/{userId}`                        | `auth:org:update`  | 移出组织。**本来就不在该组织下返回 `40410`**；移除主岗**不会自动把主岗转给别人**（转给谁是人事决定）                                            |

> **成员挂在组织下**（`/orgs/{id}/members`）而不是单开平级资源：归属是组织的**内部边**，
> 不存在"没有组织的组织归属"。与用户组那处有一处**形状差别**：路径里**没有 `memberType`** ——
> `bw_auth_rel_org_user` 只装用户，层级由 `parentId` 表达，没有多态列就不在路径上假装有。
>
> **`orgLevel` / `codePath` / `fullName` 是派生列，一律不接受入参。**
> 三者都是 `parentId` 与各级名称的确定函数，而 `DEPT_AND_CHILD` 正是按 `codePath` 前缀求子树 ——
> 让调用方传路径，就会出现"挂在这棵树下、路径却指向另一棵"的节点，其整棵子树**静默地**
> 算不进任何人的范围。它们出现在出参里是为了**解释数据范围**（排查"这个节点的范围不对"时
> 第一个要看的就是它在第几层、路径是什么）。
>
> **`orgType`（`COMPANY` / `DEPT` / `TEAM` …）/ `relationType` / `positionName` 是"存但不用"的
> 描述性字段。** 与用户组拒绝 `filterJson` 不矛盾：那些被拒绝是因为**看起来会改变行为**，
> 而这三个是一句描述、界面也不承诺它会做什么。
>
> **路径写 `orgs`（短形）、权限码写 `auth:org:*`** —— 与 `user-groups` / `auth:user_group:*`
> 那处"两处不同形"是同一个约定的一部分：URL 给人看，标识给程序与后台配置用。

### 审计域（`sts-server-audit`）

**这一域只有读接口 —— 刻意的。** 审计记录由后端拦截器在写接口的 `afterCompletion` 阶段自动产生，
调用方**没有任何路径**往审计表里写东西（没有写接口、`AuditLogService` 没有写方法、
`common-api` 的 `AuditApi` 也没有写方法）。不可篡改不靠权限保护，靠**接口不存在**。
见 `docs/decisions/0019`。

| 方法  | 路径                        | 权限               | 说明                                                                     |
| ----- | --------------------------- | ------------------ | ------------------------------------------------------------------------ |
| `GET` | `/api/audit/logs`           | `auth:audit:read`  | 分页查询。按 `action` / `resource` / `targetId` / `operatorId` / `operatorName` / `result` 与时间窗过滤；默认窗口 7 天，按服务端时间 + id 倒序 |
| `GET` | `/api/audit/logs/{id}`      | `auth:audit:read`  | 单条详情                                                                 |
| `GET` | `/api/audit/logs/stats`     | `auth:audit:read`  | **按动作**分组聚合（`resource` / `operatorId` / 时间窗可过滤），每项含总数与失败数。**刻意不接受 `action` 过滤** —— 它本身就是按动作分组 |

> `POST /api/audit/logs` 返回 **`40500`（请求方法不被支持）**，不是 404 也不是 500：
> 让"用错了 HTTP 方法"这件事说清责任方，比让它落进兜底的"系统内部错误"更有用。
>
> **审计没有客户端时间戳**（日志与事件都有双时间戳）：它不由客户端产生，因此只有一个
> 来自服务端的 `serverTime`。**匿名请求不产生审计**（没有操作人可归责，且公网扫描会灌满表），
> 但**已认证而无权限的请求必须产生**（有明确操作人，且那正是最该留痕的一类）。

### 关键数据模型

| 模型                                      | 说明                                                                                                                                                                                                                                          |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ClientLogItem`                           | 单条上报日志（`level` / `namespace` / `timestamp` / `message` / `data` / `context` / `traceId` / `sessionId` / `route` / `release`）                                                                                                          |
| `ClientLogReportRequest`                  | 批量上报请求体：`app` / `release` / `sessionId` / `logs[]`                                                                                                                                                                                    |
| `ClientLogReportResultVO`                 | 上报结果：`accepted` / `rejected`                                                                                                                                                                                                             |
| `ClientLogVO`                             | 服务端返回的日志记录（含服务端生成的 `id` / `serverTime` / `ip`）                                                                                                                                                                             |
| `ClientLogStatsVO`                        | 聚合结果：`total` / `levels[]` / `startTime` / `endTime`                                                                                                                                                                                      |
| `PageResultClientLogVO`                   | 分页结果：`records` / `total` / `page` / `size` / `pages`                                                                                                                                                                                     |
| `ClientEventItem`                         | 单条上报事件（`eventId` / `eventName` / `userId` / `deviceId` / `sessionId` / `route` / `release` / `props` / `propsVersion` / `timestamp`）                                                                                                  |
| `ClientEventReportRequest`                | 批量上报请求体：`app`（必填） / `release` / `sessionId` / `events[]`（必填）                                                                                                                                                                  |
| `ClientEventReportResultVO`               | 上报结果：`accepted` / `rejected`                                                                                                                                                                                                             |
| `ClientEventVO`                           | 服务端返回的事件记录（含服务端生成的 `id` / `serverTime` / `ip`）                                                                                                                                                                             |
| `ClientEventStatsVO`                      | 聚合结果：`total` / `names[]`（`ClientEventNameCount`） / `startTime` / `endTime`                                                                                                                                                             |
| `PageResultClientEventVO`                 | 分页结果，结构同日志域                                                                                                                                                                                                                        |
| `HealthVO`                                | 健康检查：`status` / `application` / `dependencies`                                                                                                                                                                                           |
| `LoginRequest`                            | 登录入参：`tenantCode` / `username` / `password`（**三者均必填**）                                                                                                                                                                            |
| `LoginVO`                                 | 登录结果：`accessToken` / `refreshToken` / `tokenType` / `expiresIn` / `userId` / `username` / `displayName` / `tenantId` / `tenantCode` / `roles[]` / `permissions[]` / `mustChangePassword`（**登录与刷新共用**：前端基座走的就是这条路，只挂在 `/me` 上的话它永远到不了用户面前，见 `0044`） |
| `RefreshRequest`                          | 刷新入参：`refreshToken`（必填）                                                                                                                                                                                                              |
| `MeVO`                                    | 当前登录者：`userId` / `username` / `displayName` / `tenantId` / `tenantCode` / `roles[]` / `permissions[]` / `mustChangePassword`                                                                                                                                   |
| `TenantSaveRequest`                       | 租户新建/更新入参：`tenantCode`\* / `tenantName`\* / `parentId` / `description` / `status` / `allowRegister` / `allowAdd`（**`tenantCode` 创建后不可改**）                                                                                    |
| `TenantVO`                                | 租户视图：`id` / `tenantCode` / `tenantName` / `parentId` / `tenantLevel` / `status` / `description` / `allowRegister` / `allowAdd` / `createTime` / `updateTime` / `hasChild`                                                                |
| `UserSaveRequest`                         | 用户新建/更新入参：`tenantId` / `username`\* / `displayName` / `email` / `phone` / `password` / `status`                                                                                                                                      |
| `PasswordResetRequest`                    | 重置口令入参：`password`\*（重置后置为"需改密"并解锁账号）                                                                                                                                                                                    |
| `UserVO`                                  | 用户视图：`id` / `tenantId` / `username` / `displayName` / `email` / `phone` / `status` / `failedAttempts` / `lockedUntil` / `lockReason` / `lockLevel` / `mustChangePassword` / `lastLoginAt` / `mfaEnabled` / `avatarUrl` / `createTime` / `updateTime` —— **不含任何口令字段，连哈希也不返回**。锁定三项是给运维看的：`lockLevel` 决定退避时长（第几次被锁），`lockReason` 说明为什么锁；`mustChangePassword` 则回答管理员重置之后最关心的下一件事："他改了没有" |
| `ResourceSaveRequest`                     | 资源新建/更新入参：`resourceCode`\*（**须为三段小写**，创建后不可改） / `resourceName`\* / `resourceType`\* / `resourceCategory` / `parentCode` / `requestUrl` / `requestMethod` / `icon` / `sortNo` / `isEnabled` / `isWhitelist` / `isDeprecated` / `resourceVersion` / `description`。**没有 `tenantId`**：资源是平台级的 |
| `ResourceVO`                              | 资源视图：`id` / `tenantId`（当前恒为 null） / `resourceCode` / `resourceName` / `resourceType` / **`requestScoped`（由 `resourceType` 派生，不在入参里）** / `resourceCategory` / `parentCode` / `requestUrl` / `requestMethod` / `icon` / `sortNo` / `isEnabled` / `isWhitelist` / `isDeprecated` / `resourceVersion` / `description` / `createTime` / `updateTime` |
| `ResourceTreeNode`                        | 树节点：`id` / `resourceCode` / `resourceName` / `resourceType` / `requestScoped` / `parentCode` / `icon` / `sortNo` / `isEnabled` / `children[]`。字段刻意比 `ResourceVO` 少 —— 树会一次返回全量节点，明细走详情接口 |
| `PageResultResourceVO`                    | 分页结果，结构同日志域（`records` / `total` / `page` / `size` / `pages`）                                                                                                                                                                     |
| `RoleSaveRequest`                         | 角色新建/更新入参：`roleCode`\*（**创建后不可改**） / `roleName`\* / `tenantId` / `parentId` / `roleLevel` / `category` / `dataScope` / `filterJson` / `inheritStrategy` / `isDefault` / `sortNo` / `status` / `description`。**有判定语义的字段严格校验**（`status` / `dataScope` / `isDefault`），**只存不用的宽松接受**（`category` / `inheritStrategy` / `filterJson`） |
| `RoleVO`                                  | 角色视图：`id` / `tenantId` / `roleCode` / `roleName` / `parentId` / `roleLevel` / `category` / `isDefault` / `inheritStrategy` / `dataScope` / `filterJson` / `sortNo` / `status` / `description` / `createTime` / `updateTime`。`dataScope` 等字段标了"**当前只存不用**"——数据权限求值未实现 |
| `RoleResourceGrantRequest`                | 授权入参：`resourceId`\*（**主键，不是资源标识**） / `effect`（`ALLOW` / `DENY`，缺省 `ALLOW`） / `priority` / `effectiveFrom` / `effectiveTo`（毫秒；区间写反返回 `40001`） |
| `RoleResourceVO`                          | 已授资源：`grantId` / `resourceId` / `resourceCode` / `resourceName` / `resourceType` / **`requestScoped`（派生）** / `isEnabled` / `effect` / `priority` / `effectiveFrom` / `effectiveTo` / **`effectiveNow`（派生：此刻是否落在生效区间内）** / `createTime` |
| `RoleMemberBindRequest`                   | 成员关系入参：`memberType`\*（`USER` / `GROUP`，**必填不给默认值**） / `memberId`\*（**主键，不是用户名**） / `effectiveFrom` / `effectiveTo`（毫秒；区间写反返回 `40001`） |
| `RoleMemberVO`                            | 成员关系：`grantId` / `memberType` / `memberTypeLabel` / `memberId` / `memberName`（用户取 `displayName`、为空回退 `username`；用户组取 `groupName`；**成员已不存在时为 `null`**） / `effectiveFrom` / `effectiveTo` / **`effectiveNow`（派生，只表达时态）** / `createTime` |
| `PageResultRoleVO` / `PageResultResourceVO` / `PageResultRoleMemberVO` | 分页结果，结构同日志域（`records` / `total` / `page` / `size` / `pages`）                                                                                                                                                                     |
| `UserRoleVO`                              | **反查结果的一行**（一个"来源"）：`grantId` / `source`（`DIRECT` / `GROUP`） / `sourceLabel` / `roleId` / `roleCode` / `roleName` / `roleStatus` / `groupId` / `groupName` / `groupEnabled` / `effectiveFrom` / `effectiveTo` / `effectiveNow` / `groupMemberEffectiveFrom` / `groupMemberEffectiveTo` / `groupMemberEffectiveNow` / `createTime`。**两组时态字段**：经组授予是两条边（角色-成员、组-成员），各有一段生效区间；`source = DIRECT` 时第二组为 `null`（那条边**不存在**，而非"长期有效"）。**停用 / 过期照原样返回** |
| `UserDataScopeVO`                         | **数据范围求值结果**：`scopes`（生效档位去重、由宽到窄） / `scope`（**合并后的最宽档**） / `scopeLabel` / `roleCodes`（这些档位来自哪些角色） / `allData`（是否全部数据） / `precise`（**这一档能否被精确求值**） / `note`。**`precise` 就是把"已配置"与"已生效"分开的那个字段**：`ALL` / `SELF` 为 `true`（自足，不需要别的数据），`DEPT` / `DEPT_AND_CHILD` / `CUSTOM` 为 `false`（需组织归属或 `filter_json` 解析）。**无任何档位时 `scope` 为 `null`，而不是 `ALL`** —— "没配数据范围"与"配了全部数据"是两件事 |
| `UserGroupSaveRequest`                    | 用户组新建/更新入参：`groupCode`\*（`^[A-Za-z][A-Za-z0-9_]{0,63}$`，**创建后不可改**） / `groupName`\* / `parentId` / `category` / `isDefault` / `isEnabled` / `sortNo` / `description`。**没有 `tenantId`**（取当前登录者）**没有 `filterJson` / `orgScopeJson`**（动态组未实现，刻意不接受） |
| `UserGroupQuery`                          | 用户组查询入参：`page` / `size` + `tenantId` / `keyword` / `category` / `enabled`（`Integer` 而非 `Boolean`：列是 `TINYINT` 可为 NULL，布尔会多一层"null 是不过滤还是查未设置"的歧义） / `parentId` |
| `UserGroupVO`                             | 用户组视图：`id` / `tenantId` / `groupCode` / `groupName` / `parentId`（**只用于列表分组展示，不产生权限**） / `category`（当前恒为 `STATIC`） / `isDefault`（**只存不用**） / `isEnabled`（**真开关**：停用的组不再产生权限） / `sortNo` / `description` / `createTime` / `updateTime`。**不返回 `filterJson` / `orgScopeJson`** |
| `UserGroupMemberBindRequest`              | 组成员关系入参：`memberType`\*（**当前只接受 `USER`**，传 `GROUP` 返回 `40001`） / `memberId`\*（**主键，不是用户名**） / `effectiveFrom` / `effectiveTo`（毫秒；区间写反返回 `40001`） |
| `UserGroupMemberVO`                       | 组成员关系：`grantId` / `memberType` / `memberTypeLabel` / `memberId` / `memberName`（取 `displayName`、为空回退 `username`；**成员已不存在时为 `null`**） / `effectiveFrom` / `effectiveTo` / **`effectiveNow`（派生，只表达时态）** / `createTime`。结构与 `RoleMemberVO` 近似但**刻意不共用**（取值域与名字解析来源不同） |
| `UserGroupMemberQuery`                    | 组成员查询入参：`page` / `size` + `memberType`。**不做按名搜索**（名字在另一张表，先解析再分页会让 `total` 失真） |
| `PageResultUserGroupVO` / `PageResultUserGroupMemberVO` | 分页结果，结构同日志域（`records` / `total` / `page` / `size` / `pages`）                                                                                                                                                                     |
| `PageResultTenantVO` / `PageResultUserVO` | 分页结果，结构同日志域（`records` / `total` / `page` / `size` / `pages`）                                                                                                                                                                     |
| `AuditResult`                             | 枚举 `SUCCESS` / `FAILURE`（线上取值即大写枚举名）                                                                                                                                                                                            |
| `AuditLogVO`                              | 审计记录：`id` / `action` / `resource` / `targetId` / `operatorId` / `operatorName` / `tenantId` / `result` / `errorCode` / `errorMessage` / `httpMethod` / `requestUri` / `ip` / `userAgent` / `traceId` / `durationMs` / `serverTime`（**无客户端时间、无 `update*` / `deleted`**） |
| `AuditLogQuery`                           | 分页查询入参：`page` / `size` + `action` / `resource` / `targetId` / `operatorId` / `operatorName` / `result` / `startTime` / `endTime`（`*Time` 显式为 `int64`，避免被推平成 string）                                                          |
| `AuditActionCount`                        | 单动作聚合项：`action` / `actionCount` / `failureCount`                                                                                                                                                                                       |
| `AuditStatsVO`                            | 聚合结果：`total` / `failureTotal` / `startTime` / `endTime` / `actions[]`（`AuditActionCount`）                                                                                                                                              |
| `PageResultAuditLogVO`                    | 分页结果，结构同日志域                                                                                                                                                                                                                        |
| `OrganizationVO`                          | 组织：`id` / `tenantId` / `orgCode` / `orgName` / `fullName` / `parentId` / **`orgLevel`** / **`codePath`**（形如 `/1/5/`，`DEPT_AND_CHILD` 的求值口径）/ `hasChild` / `orgType` / `managerId` / `managerName`（冗余列）/ `sortNo` / `status` / `description` / 联系方式 |
| `OrganizationTreeNode`                    | 树节点：比 `OrganizationVO` 少一大截（树是**用来选层级、看归属**的），保留 `orgLevel` / `codePath` —— 排查数据范围时最常问的两个问题在树上就能直接答                                                  |
| `OrganizationSaveRequest`                 | 保存入参。**不含** `orgLevel` / `codePath` / `fullName` / `managerName`（派生或服务端解析）；`orgCode` 在修改路径上仍是 `@NotBlank`（不修改但要求回传）                                            |
| `OrganizationMoveRequest`                 | 移动入参，只有一个 `parentId`，**可空**（不传或传 `null` 都表示提升为顶级）。请求体必填，传 `{}` 即可                                                                |
| `OrganizationQuery` / `OrgMemberQuery`    | 查询入参。后者只多一个 `primaryOnly` —— **主岗不是"可有可无的额外信息"，它是 `DEPT` 档位唯一的取值来源**                                                            |
| `OrgMemberVO`                             | 组织成员关系：`grantId` / `memberId` / `memberName`（用户已删除时为 `null` —— 关系表无外键，这种情况应当被看见）/ `isPrimaryPosition` + **布尔形态 `primaryPosition`**（供界面直接绑定）/ 岗位 / 时态 / `effectiveNow` |
| `OrgMemberBindRequest`                    | 归属入参。**没有 `memberType`**（该表只装用户）；`effectiveFrom` / `effectiveTo` 是毫秒时间戳                                                                            |
| `UserOrgVO`                               | 反查用：**从用户这一侧看** —— `orgId` / `orgCode` / `orgName` / `fullName` / **`orgStatus`** / 主岗标记 / 时态。不复用 `OrgMemberVO`（那个类回答的是"这个组织里有哪些人"，一个组织字段都没有） |
| `RVoid`                                   | 无数据的成功响应（`data` 为空），用于删除与重置口令这类"只关心成败"的接口                                                                                                                                                                     |
| `R*` 前缀                                 | 统一响应包装 `R<T>`：`code` / `message` / `data` / `timestamp`                                                                                                                                                                                |

> **业务失败同样返回 HTTP 200。** 契约的 `description` 里写明了这一点：结果只由 `code` 表达，调用方统一判断 `code === 0`。传输层失败（401/403/404/5xx）由框架按标准返回，不套统一包装。

---

## 二、契约如何更新（硬性流程）

**后端接口有任何增删改，都必须走完这三步，并与代码在同一次提交里完成。**

```bash
# 1. 构建后端（导出脚本需要可运行的 jar）
cd server && mvn -DskipTests install

# 2. 从运行中的应用导出契约（默认端口 18080，可自动等待启动）
cd .. && bash scripts/export-openapi.sh

# 3. 重新生成前端类型
cd web && pnpm --filter common-api gen:api
```

产物：

| 文件                                                | 是否入库 | 说明                   |
| --------------------------------------------------- | -------- | ---------------------- |
| `docs/api/openapi.json`                             | ✅ 入库  | 跨端唯一真相源         |
| `web/packages/common-api/src/generated/schema.d.ts` | ✅ 入库  | 前端类型，**禁止手改** |

生成物入库的目的有两个：前端构建不依赖后端可运行；契约变更在 code review 里显式出现为一个 diff。

**这两步的产物有没有跟上它各自的上游，由 CI 逐字节核对** —— 契约 ↔ 代码（`server` job
重跑第 2 步再与入库版本比对，见 `docs/decisions/0042`）· 生成物 ↔ 契约
（`web` job 重跑第 3 步再比对，见 `docs/decisions/0041`）。漏跑第 2 或第 3 步**都会红**，
不必靠人记得。

---

## 三、为什么导出脚本用 18080 端口

8080 上很可能正跑着开发用的实例。端口冲突时脚本会**连上另一个服务并导出一份错误的契约，而且没有任何报错** —— 这是最难发现的一类错误。因此脚本默认使用 `OPENAPI_PORT`（默认 `18080`），并要求显式覆盖：

```bash
OPENAPI_PORT=18081 bash scripts/export-openapi.sh
```

---

## 四、契约里"任意值"字段的处理

日志的 `data` / `context` 与事件的 `props` 在后端声明为 `Object`，OpenAPI 的单层 schema 表达不了"任意 JSON"，生成器只能给出保守形态——而且两代生成链给出的形态**都不理想**：

| 生成链                                 | 对"任意值"的渲染        | 问题                                                                                            |
| -------------------------------------- | ----------------------- | ----------------------------------------------------------------------------------------------- |
| springdoc 2.x（OpenAPI 3.0）           | `Record<string, never>` | 语义是"不允许有任何字段的对象"，`{ orderId: '…' }` 编译期就被拒                                 |
| springdoc 3.x（OpenAPI 3.1，**当前**） | `unknown`               | 过宽：函数、Symbol 也能通过编译，而它们会让 `JSON.stringify` 抛错，一条脏数据就能让整批上报失败 |

因此这一缺口分两处收紧：**类型本体**（`JsonValue` / `JsonArray` / `JsonObject`）与 `sanitize` 函数放在 `web/packages/common-tools/src/json`，`common-api` 只做转出并按字段名替换（日志用 `WithJsonPayload` 换 `data` / `context`，事件用 `WithJsonProps` 换 `props`），其余字段继续完全由契约生成。详见 ADR-0006 第 4 条与 ADR-0008 第 5 条。

> 升到 OpenAPI 3.1 还有一个连带修复：分页结果 `records` 的元素与导出的 `ClientLogVO`
> 曾不是同一个类型（前者未经收紧）。这个裂缝在 3.0 时代被 `Record<string, never>` 的
> 可赋值性掩盖，3.1 后立刻变成编译错误——已用 `WithJsonRecords` 修复，并有类型断言钉住。
>
> **事件域踩了同一个坑**：分页结果的 `records` 元素、上报请求体的 `events` 数组元素
> 都未经收紧。**新增任何带"任意 JSON 字段"的域时，这三处（本体 / 分页元素 / 请求体数组元素）
> 必须一并收紧** —— 只改第一处会被类型系统放过。

---

## 五、待补充

- ~~**组织管理接口尚未落地**~~ → **已于 2026-10-13 落地**（`bw_auth_organization` / `bw_auth_rel_org_user`，
  表、实体与 Mapper 早就就绪，见 ADR-0016 末尾的"未覆盖清单"）。它与用户组**同形**
  （同一套父子 + 成员关系的形态），但**多了一条边**：`is_primary_position` 直接决定一个人的数据范围。
  用户组与反查已于上一批落地：**用户组 8 条路径** + **反查 1 条路径**（ADR-0024）。
  全仓已落地的权限域切片：**资源目录**（ADR-0021，6 条路径）、**角色管理与角色资源授权**（ADR-0022，8 条路径）、
  **角色成员关系**（ADR-0023，3 条路径）、**用户组管理与反查**（ADR-0024，9 条路径）、
  **组织树与成员归属**（ADR-0030，**8 条路径 / 12 个接口**：组织树 CRUD + 移动 + 成员 + 主岗，
  外加 `/users/{id}/organizations` 反查）。
  本批把 `0025` 留下的最后一处"只存不用"清掉了 —— `DEPT` / `DEPT_AND_CHILD` 从
  "`precise` 恒为 false" 变成**可精确求值**（有有效主岗时下发 `visibleOrgIds`）；
  同批修掉一处**半更新状态**（移动只写派生列、没落 `parent_id`）。
  **前端管理页已于同批落地**：「组织」成为 `platform-admin` 的第六个标签页
  （树 + 编辑 + 移动 + 成员 + 设主岗），与后端同批交付 —— 只读的组织接口没有价值，
  写入路径必须与读取同批落地。
- **动态用户组求值**：`filter_json` / `org_scope_json` 两列只存不用，且**刻意不在任何接口的入参 / 出参里** ——
  动态成员规则求值未实现，接受它会得到一个"永远算不出成员"的壳。触发条件：真的需要动态组时。
- **数据范围求值完成了"求值 + 下发"**（ADR-0025 / **ADR-0030 补齐了组织侧**）：`GET /api/auth/users/{id}/data-scope`
  合并该用户全部生效角色的 `data_scope`（**取最宽**），并用 `precise` 说出"这一档能不能被精确求值"。
  已接入的消费方**只有一个**：**审计查询**（`ALL` 放行整个租户，其余档位保守地按「仅本人」处理）。
  `DEPT` / `DEPT_AND_CHILD` **现在有了确切口径**（主岗所在组织 / 其启用子树），
  `visibleOrgIds` 也已下发 —— 但**它还不是过滤条件的输入**：把 `DEPT` 接进审计可见性会是一次
  **静默放宽**（`bw_audit_log` 没有组织维度，只能退化成 `operator_id IN (该部门的用户)`），
  这是**刻意的"不做"**（ADR-0030 第 10 条）。
  **`CUSTOM` 仍需 `filter_json` 的表达式解析**；在解析实现之前，这一档在查询上退化为「仅本人」。
  ⇒ 角色表单因此**不提供 `CUSTOM` 选项**（它要 `filterJson`，而界面既无处填写、本仓也不解析 ——
  摆出来就是"选了也配不了、配了也不生效"，与菜单权限那次对三维环境页面码的判断同源）。
  后端**保留**该枚举值：存量行照原样存、求值照原样给 `precise = false`，
  只是界面不再把它当成一个可配的档位。
  另：本仓**没有业务数据表**（16 张全是 `bw_auth_*` 加三条管道），所以"用 `data_scope` 过滤业务数据"
  在当前仓里没有落点 —— 它的价值最终要在业务系统兑现。
- **给三张关系表加唯一索引**（`(role_id, resource_id)` / `(role_id, member_type, member_id)` / `(user_group_id, member_type, member_id)`）：
  有了 `DuplicateKeyException` → `409` 兜底才"有资格"加（否则并发撞键会落到 `50000`），但那是独立的一次决定。
- **日志域与事件域的查询入口已补上鉴权**（ADR-0026）：`GET /api/logs/client*` 与 `GET /api/events/client*`
  共 7 个入口要求 `log:client:read` / `event:client:read`（**与 `auth:audit:read` 刻意不合并** ——
  三种读者：合规追责 / 排障 / 产品运营）；两个 `POST` 上报入口**刻意保持公开**（登录页出错时也必须能上报），
  因此**接口数与路径数未变**，本次契约变更只有 `@Operation` 描述文案。
  查询结果**只含当前登录租户**的记录，未登录时上报的记录 `tenant_id` 为 NULL、**对任何租户都不可见**；
  详情跨租户返回 `404`（与"不存在"同码）。这两条管道的**前端 401 此前是漏接**：
  宿主恒下发 `auth` 桥，两个控制台只是没读 —— 现已经 `common-api` 的 `createConsoleAuth` 接上。
- 前端的域接线进度：**认证 / 会话**（`src/auth.ts`）、**权限域管理**（`src/access.ts`，已补齐到 20 条路径）、**审计**（`src/audit.ts`，只读）**均已接入**。
  **数据范围反查**（`GET /api/auth/users/{id}/data-scope`）也已接线：用户列表的「数据范围」按钮打开反查对话框，
  它要展示的正是"配了但暂时不生效"这个状态（`precise=false` 加上 `visibleOrgIds` 的 `null` 与 `[]` **分开说**）。
  `platform-admin` 现有**六个标签页**：用户（含「角色来源」反查）、租户、资源（树 / 列表两种视图）、角色（含资源授权与成员）、用户组（含组成员）、组织（树 + 编辑 + 移动 + 成员 + 设主岗）。
  `AccessPermissions` 随之补上了 `auth:resource:*` / `auth:role:*` / `auth:user_group:*` 共 12 项
  （该文件的规则是"只列前端真的用到的"；角色成员与用户组成员的**授予 / 撤销复用 `auth:role:update` / `auth:user_group:update`**，服务端另有独立的审计动作把它记清楚）。
  **菜单权限的两步已同做**（ADR-0031）：种子按 `AuthMenus` 建出 `MENU` / `PAGE` 行，
  基座 `BasicLayout` 按权限过滤导航、`RequireMenu` 同时拦住 URL 直达 —— 只做前者就是交付假开关。
- 契约尚未按域拆分为多文件；接口量变大后再考虑 `openapi/` 目录方案
