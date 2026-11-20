# 概览契约 v1（overview）

> 状态：**已定稿，作为 S4 接口层的唯一口径**
> 日期：2026-11-21 ｜ 关联：[三维王城总纲](../../plans/2026-11-19-3d-capital-master-plan.md) T4.1–T4.6、[API 规范](../../api.md)
> 机器可读定义：[overview.schema.json](overview.schema.json)（前端 TS 类型由它生成，**不得手改生成物**）

## 0. 这份契约解决什么

S3 的数据厅已经能画 KPI / 折线 / 柱 / 环形，但每个厅的数字来自**各服务各自的概览接口**：
`/erp/bi/cockpit`、`/mes/dashboard`、`/oa/workbench/summary`……字段名、单位、百分比口径全不一样，
前端只能靠 `dig()` 猜字段、靠「小于 1.5 就当比率」猜百分比。9 殿 + 12 坊 = 21 处要接，
每处都猜一遍必然漂移。

因此 S4 定义**一个形状**：每个服务只需实现一个 `GET /<服务前缀>/overview`，
返回一份 `OverviewSnapshot`。前端（屏风、数据厅、牌坊）只认这一个形状。

## 1. 传输与鉴权

| 项 | 约定 |
|---|---|
| 方法 | `GET`（只读，不得在概览里做写操作） |
| 路径 | `/<服务一级前缀>/overview`，由 `whale.overview.base-path` 配置；**不带版本段**（与 `/erp/bi/cockpit` 等既有只读接口一致） |
| 鉴权 | 网关注入 `X-User-Id` / `X-Permission-Codes`；服务端按 provider 声明的权限码判定，缺失返回 403 |
| 响应 | 统一 `Result`：`{ "code": 0, "message": "success", "data": OverviewSnapshot }` |
| 缓存 | 服务端 TTL 缓存（默认 30s），前端轮询间隔必须 ≥ TTL，避免穿透 |
| 幂等 | 同参数重复调用不得改变业务状态；**不得**为了「让概览有数」而落库 |

> 反例：`AnalyticsAppService#getCockpit` 在快照缺失时会 `persistCurrent` 落一条快照。
> 概览允许复用这类接口（口径一致优先），但**新增服务不得**在概览里写库。

## 2. 快照结构

```jsonc
{
  "service": "erp",                    // 服务一级前缀，与网关路由一致
  "nodeKey": "hubu-hall",              // 城内节点 key，与 city-layout.json 对齐（前端据此找厅/牌）
  "title": "户部",                     // 厅/牌的主标题
  "subtitle": "经营驾驶舱 · 2026-11",   // 副标题：口径与统计期间写在这里，不要让前端拼
  "generatedAt": "2026-11-21T00:30:00+08:00",
  "state": "live",                     // live | stale | degraded
  "kpis": [ /* Kpi[]，建议 3–6 个，按重要性排序 */ ],
  "charts": [ /* Chart[]，建议 ≤3 个，厅内位置有限 */ ],
  "alerts": [ /* Alert[]，按严重度降序 */ ],
  "links": [ /* Link[]，跳转屏风页的入口 */ ],
  "notes": ["…"]                       // 人可读的状态说明：降级原因、被裁剪的块
}
```

### 2.1 `state` 三态

| 值 | 含义 | 前端表现 |
|---|---|---|
| `live` | 本次实时取数成功 | 正常显示，牌底「刷新于 hh:mm」 |
| `stale` | 本次失败，返回的是**上一次成功**的快照 | 正常显示 + 「陈旧 · 上次 hh:mm」，不得当成实时数 |
| `degraded` | 从未成功过，或部分块不可用 | 空态/闭门态，不白屏 |

`stale` 与 `degraded` 必须同时给 `notes`，写清原因（超时 / 异常 / 无权限），
否则「牌上有数但不知道是不是真的」比空着更坏。

### 2.2 `kpis[]`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `key` | string | 是 | 厅内唯一，用于差异比对（内容不变不重绘） |
| `label` | string | 是 | 牌面文字，≤6 个汉字为佳 |
| `value` | number | 是 | 数值。**百分比传比率**（82% → `0.82`） |
| `format` | enum | 是 | `count` / `money` / `percent` / `duration` / `text` |
| `unit` | string | 否 | `元` / `件` / `秒` 等；`format` 已表达单位时留空 |
| `tone` | enum | 否 | `good` / `warn` / `bad` / `flat`，缺省 `flat` |
| `deltaPercent` | number | 否 | 环比，比率（+12% → `0.12`） |
| `hint` | string | 否 | 口径说明，≤20 字（「未关闭预警」而不是「预警」） |

**百分比只允许一种口径**：一律传 `0.82`，渲染时乘 100。
S3 前端曾在 `0.82` 与 `82` 之间猜（`formatPercent` 用「小于 1.5 就当比率」兜底），
这在「0.8% 达成率」这种真实场景下会显示成 `80%` —— 契约层统一，兜底逻辑随之上线为历史兼容。

`format` 只表达**怎么排版**，不表达业务语义：
`money` 千分位 + `¥`；`count` 千分位 + 万/亿折算；`percent` 一位小数 + `%`；
`duration` 秒数转 `12 分钟`；`text` 直接用 `hint` 之外的字符串数值（`value` 仍必填，用于排序/比对）。

### 2.3 `charts[]`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `key` | string | 是 | 唯一 |
| `title` | string | 是 | 图题 |
| `type` | enum | 是 | `line` / `bar` / `donut` |
| `format` | enum | 是 | 与 KPI 同一套，用于刻度/图例格式化 |
| `categories` | string[] | 是 | 横轴标签或环形扇区名，长度必须等于每个 series 的 values 长度 |
| `series` | Series[] | 是 | `{ "name": "营收", "values": [12.5, 13.1] }`，`donut` 只用第一条 |

`categories` 与 `values` **必须等长**，不允许空洞：缺数据用 0 并降级成 `notes`，
不要用 `null`——canvas 绘制时 null 会变成断线或 NaN，整张卡静默变白。

### 2.4 `alerts[]`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `level` | enum | 是 | `info` / `warning` / `critical` |
| `text` | string | 是 | 一句话，≤30 字 |
| `count` | integer | 否 | 同类条数（同类合并，不要把 200 条塞进数组） |
| `since` | string | 否 | 首现时间 ISO-8601 |

### 2.5 `links[]`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `key` | string | 是 | 唯一 |
| `label` | string | 是 | 入口文字 |
| `path` | string | 是 | 屏风页要取的列表接口路径（`/erp/scm/purchase-order/page`），与 2D 后台同一路径 |
| `permission` | string | 否 | 该入口额外要求的权限码；缺失时**由 provider 裁掉**，不要下发再让前端隐藏 |

> **`path` 的语义（T4.5 起）**：不另造"屏风专用接口"，直接指该列表在 2D 后台用的分页接口
> （`{code,message,data:{records,total,current,size}}` 信封）。列怎么排、详情取哪个字段属于
> 展示层的配置，在 `whale-world/src/world/screenpages.ts` 里按 `key` 登记；契约只负责
> "这殿还有哪些入口"，不负责表头。这样同一份权限码、同一套分页参数在两处复用，
> 3D 不会出现"只有屏风能看到的第二套列表口径"。

## 3. 服务端行为（starter 保证，provider 不重复实现）

1. **TTL 缓存**：同一 `service + tenantId` 在 TTL 内直接复用上次快照，不重复调 provider。
2. **超时**：provider 执行超过 `whale.overview.timeout`（默认 3s）即中断并降级，**不得**让 HTTP 线程挂死。
3. **失败降级**：provider 抛异常 → 有上次成功快照则返回它并置 `state=stale`，否则置 `degraded`；
   两种情况都补 `notes`，并计数 `overview.load{service,outcome}`。
4. **权限**：provider 声明 `permissionCode()`，缺失时直接 403（而不是返回空 KPI）。
   块级裁剪由 provider 在 `build(ctx)` 里用 `ctx.hasPermission(code)` 决定。
5. **不抛 500**：概览是"看一眼"的接口，任何内部异常都必须转成 `degraded` 快照。

## 4. 前端消费约定

1. 没权限的节点**不发请求**（沿用 S3 的 `lacksPermission` 口径），避免把网关日志刷穿。
2. `state=stale` 必须显示「陈旧」而不是伪装成实时；`degraded` 显示空态而不是 `0`。
3. 类型来自 `whale-world/scripts/gen-overview-types.mjs` 生成的 `overview.types.ts`，
   字段改动走「改 schema → 重新生成 → `--check` 进 CI」，不允许手改生成物。

## 5. 演进

- 新增字段一律**可选**，旧前端不认识就忽略；删除/改名必须升 `v2` 路径或加语义别名。
- `nodeKey` 是城与业务的接缝，**不得**随服务重构改名（改名等于城里的厅找不到数据）。
- 契约变更须同时更新：本文件、`overview.schema.json`、生成的前端类型、`OverviewContractTest`（字段往返）。
