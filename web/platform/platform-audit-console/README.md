# platform-audit-console — 审计日志台

审计日志管道（`docs/decisions/0019`）的可运行证明：**记录不是前端上报的，是后端拦截器
在别人的写请求上自动产生的**，前端对这一域**只有读**。

---

## 一、它和另外两个控制台刻意长得不一样

`platform-log-console` 与 `platform-event-console` 都是**左右分栏**：一边"客户端视角"、
一边"服务端视角"。那样做的理由是"只看一边无法判断上报成没成"。

审计**没有"两边"**：没有任何一条路径让前端把审计记录送出去。硬套分栏只会让人以为
这里也能上报。所以本页是自上而下的三块：

| 区块       | 回答的问题                                     |
| ---------- | ---------------------------------------------- |
| 审计流水   | 谁在何时对什么做了什么、成没成                 |
| 按动作聚合 | 这段时间哪类操作最多、失败集中在哪             |
| 演练区     | 真实写一次，再看它有没有被记下来（因果链闭环） |

**形态跟着管道的产生方式走**，这比"三个控制台长得统一"重要。

---

## 二、跑起来

```bash
# 1. 后端（默认 8080，h2 内存库）
cd server && mvn -DskipTests install
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar

# 2. 前端（独立打开）
cd web && pnpm install
pnpm --filter platform-audit-console dev    # http://127.0.0.1:3600

#    或经基座打开（会带上登录页与导航）
pnpm --filter platform-portal dev           # http://127.0.0.1:3000 → 导航「审计日志」
```

开局之前先**造一点审计数据**：去「权限管理」建一个用户或租户，回来就有了。
更直接的办法是点本页的「跑一次演练」。

想看"越权也被记下来"，用只读账号登录：

| 身份              | 权限                                                           | 演练区点下去会怎样                    |
| ----------------- | -------------------------------------------------------------- | ------------------------------------- |
| `admin`           | 全部（含 `auth:audit:read`、`auth:tenant:create` / `:delete`） | 两条 `SUCCESS` 记录落地               |
| `auditor`（只读） | `auth:user:read` / `auth:tenant:read` / `auth:audit:read`      | **两条 `FAILURE`（40301）**，仍然留痕 |

> 种子账号只在 h2 profile 打开；用 MySQL 时要显式开
> `--bluewhale.auth.seed-admin.enabled=true --bluewhale.auth.seed-auditor.enabled=true`。

---

## 三、本页只发三个 GET

| 用途       | 接口                        |
| ---------- | --------------------------- |
| 流水       | `GET /api/audit/logs`       |
| 详情       | `GET /api/audit/logs/{id}`  |
| 按动作聚合 | `GET /api/audit/logs/stats` |

**没有第四个**。审计不可篡改不是靠权限码保护的（有权限的人照样能改），而是靠
**接口根本不存在** —— 往 `/api/audit/logs` 发 `POST` 会得到 `40500`（方法不被支持）。
`common-api/src/audit.ts` 的 `AuditApi` 里因此一个写方法都没有，
`packages/common-api/tests/audit.test.ts` 用快照式断言把这件事钉死。

---

## 四、演练区：按钮**刻意不按权限显隐**

权限管理台的按钮是"没权限就不渲染"（`PermissionButton`）。这里反过来：
按钮永远在，点下去该被拒就被拒。

理由是审计要看的东西恰好被"隐藏按钮"这个动作抹掉了 ——
**"有人尝试做一件他没资格做的事"本身就是最值得留下的一条记录**。
所以本区不是 `PermissionButton`，而是普通按钮 + 让服务端来拒绝。

演练的每一步都显示在页面上的步骤列表里，包括"等待审计记录"的那几百毫秒：

1. `POST /api/auth/tenants` 建一个 `drill*` 租户（时间戳的 36 进制，不重复）；
2. 轮询 `GET /api/audit/logs?action=auth.tenant.create&targetId=<id>`，直到出现；
3. `DELETE /api/auth/tenants/{id}`；
4. 再轮询 `action=auth.tenant.delete`。

> **为什么是"等"而不是"查一次"**：审计写在请求的收尾阶段（`afterCompletion`），
> 与写操作的响应没有"先落库再返回"的强保证。等待本身也是要展示的信息 ——
> 它说明审计是**随后落库**，而不是前端补写。轮询 300ms 一轮、最多 5 秒。

---

## 五、数据从哪来

- **时间窗是共享上下文**：流水与聚合用**同一个**窗口，界面上把起止时间显式印出来。
  没有它，"只有 3 条"分不清是没数据还是窗口太小。窗口右端每次渲染重算，锚在"现在"。
- **聚合刻意不接受 `action` 过滤**：它本身就是按动作分组的，带了那个条件只会永远返回一条。
  想筛某个动作，点聚合里的动作名 → 跳去筛选流水（这条路径比让人手抄动作名好）。
- **权限集合不自己算**：被基座加载时读 `HostGlobalState.permissions`，独立运行时读本应用会话。
  **本应用不调用 `/api/auth/me`**（`docs/decisions/0018` 第 3 条）。

---

## 六、它同时是一个 qiankun 子应用

| 关注点   | 做法                                                                                                |
| -------- | --------------------------------------------------------------------------------------------------- |
| 应用名   | `platform-audit-console`，必须与基座 `src/micro/apps.ts` 的注册名**逐字一致**（不一致只卡"加载中"） |
| 路由     | 基座 `/audit/*`，`basename` 由基座下发                                                              |
| `base`   | dev `/`（带前缀会让热更新静默失效），build `/audit/`                                                |
| 端口     | `3600` + `strictPort` + `cors`                                                                      |
| 挂载顺序 | `bindHost` → `connectPermissionSource()` → `render()`                                               |
| 卸载顺序 | `destroy()`（含断开权限订阅）→ `unbindHost()`                                                       |

> **`bindHost` 必须在 `connectPermissionSource()` 之前**：权限 store 在 `connect` 时**读一次**身份，
> 而"用宿主下发的身份还是自己的会话"这个判据是"**有没有宿主桥**"。顺序反了会把它误判成独立运行，
> 然后自己建一份会话 —— 两份会话并存，刷新各自失效。
> （`0034` 之前这里还夹着一步 `initializeAuth()`；那个函数已随两段式初始化一起取消，
> 但**顺序约束没有消失，只是换了位置**。）

> **权限订阅必须在 `mount` 里建立、`unmount` 里断开**：子应用模块在多次挂载之间是复用的，
> 而基座桥在 `unbindHost` 时会清空全部订阅者，顶层订阅在第二次挂载后就再也收不到推送。

> **三块区域按菜单码过滤（`docs/decisions/0031`）**：流水 / 聚合 / 演练各自有一条菜单码
> （`ui:audit_flow:view` / `ui:audit_stats:view` / `ui:audit_drill:view`）。
> 这里过滤的是**整块区域出不出现**，与第四节"演练区按钮刻意不显隐"并不矛盾 ——
> 两条规则管的是两件事：菜单码决定"看不看得到这个区域"，显隐策略决定"区域里的按钮露不露"。
> 三块都不可见时查询**不发**（`permission.ready && canRead && (showFlow || showStats || showDrill)`）——
> 有条件、没显示，就是"一条看不见的管道仍在发请求"。
> 注意 `auth:audit:read` 与菜单码是**两道独立的门**：只有菜单码没有读权限时，
> 提示的是"缺读权限"而不是"没有可见区块"。

---

## 七、目录结构

```
src/
├── api.ts                    # 接口客户端（把认证端口接到传输内核的鉴权钩子上）
├── errors.ts                 # 异常 → 文案；按业务码分流（40100 重登录 / 40301 无权限）
├── format.ts                 # 时间（到秒）/ 耗时 / 结果标签
├── permission-codes.ts       # 权限标识（转出 common-api 的 AuditPermissions）
├── auth/                     # 与 platform-admin 逐字同构：认证接线（createConsoleAuth）+ 权限快照 + hooks
├── audit/
│   ├── constants.ts          # 时间窗、每页条数、结果下拉
│   ├── use-audit-queries.ts  # 流水 + 聚合共用一个时间窗，一处刷新
│   └── DrillPanel.tsx        # 演练区（本页唯一会改数据的地方，改的不是审计表）
├── components/Dialog.tsx     # 极简对话框（详情用）
├── styles/                   # 设计令牌 + 全局样式（**令牌本应用一份，不跨应用引用**）
├── App.tsx                   # 顶栏 + 说明条 + 流水 + 聚合 + 演练区
└── main.tsx                  # 独立运行分支 + qiankun 生命周期
```

---

## 八、设计取舍

- **没有"新增一条审计"的入口**：见第三节。这是本应用最重要的一处"缺什么"。
- **详情用对话框而不是展开行**：审计一行有 16 个字段，展开行会把表格撑得找不到北；
  对话框能一次把 `traceId` / `userAgent` 这些只在排查时才看的东西放全。
- **失败行整行铺淡红**：结果列的一个标签在十几行里扫过去是看不见的，
  而"哪些操作被拒绝了"是审计最常翻的一条线。聚合条里失败部分也叠成红色。
- **主色用冷紫而不是蓝**：两个管理型子应用常被并排打开，长得一样会让人分不清在看哪个。
- **与 `platform-admin` 的 `auth/` 接线各持一份**：两个 `platform-*` 之间不得互相引用
  （六条铁律之⑤）。这层代码在两个应用里逐字相同，是**有意留着的重复** ——
  现在下沉到 `packages/` 要同时改 `platform-admin` 的既有测试与端到端脚本，
  那份风险与"审计管道"这件事无关。触发条件：出现第三个管理型子应用。
- **契约标识一律从 `common-api` 转出**：`PERM.READ` 来自 `AuditPermissions`，
  演练区判定用的 `auth.tenant.create` 来自 `AuditActions`。
  前端拼一个 `auth:audit:raed` 不会报错，只会让界面永远显示"无权查看"。
