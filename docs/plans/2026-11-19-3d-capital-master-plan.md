# 三维王城总纲 v2 —— 3D 全量承载版

> 状态：**规划态，待评审**（S0 未启动）
> 日期：2026-11-19（v2，推翻 v1 的"跳 2D"决策）
> v1 → v2 变更：**取消"深入业务跳 2D"**。3D 王城是独立完整入口，殿内所有界面都在 3D 空间里，不跳出。
> 关联：[3D 展示场景](2026-11-18-3d-showcase-scene.md)、[服务清单](../service-catalog.md)

---

## 0. 先回答那个问题：技术困难到底在哪

### 0.1 一句话结论

**没有"技术上做不到"的硬墙。** 有四面"要花钱的墙"和一条"做了没人用"的分界线。

### 0.2 四面真实的墙（必须承认）

| # | 墙 | 本质原因 | 能否解决 | 代价 |
|---|---|---|---|---|
| 1 | **中文输入法候选框错位** | 浏览器按元素 layout 位置定位 IME 候选框；元素被 `matrix3d` 变换后，layout 位置与视觉位置不一致 | ✅ 能 | 编辑态把 input 临时"提"到屏幕层，或用 contenteditable + 自绘光标。3–5 天/次 |
| 2 | **CSS3D 与 WebGL 无法天然互相遮挡** | DOM 是合成层，永远在 canvas 之上；深度缓冲不共享 | ✅ 能 | WebGL 渲染深度遮罩（遮挡物画成黑，用 blend 抠掉 DOM 区域）。5 天 |
| 3 | **DOM 在 3D 变换下每帧重合成** | 浏览器对 3D 变换元素不做增量优化，面积越大越慢 | ⚠️ 只能控制 | 限制同屏 DOM 面积（≤2 块屏风），超出用纹理贴图。设计约束，非技术障碍 |
| 4 | **浏览器原生能力丢失** | 3D 场景里没有文档流：Ctrl+F、打印、屏幕阅读器、tab 焦点顺序全都失效 | ❌ 无解 | 只能接受。**这是唯一真正无解的**，但它不影响业务可用性 |

> 第 4 条是唯一"认输"的地方。它意味着 3D 王城**不能作为唯一入口**——无障碍与打印场景仍需 2D 后台。这正好与"3D/2D 并行"的定位一致。

### 0.3 分界线：什么时候才允许"退回平面"

不是"技术做不到"，是"**在 3D 里做，用户会骂人**"。判定标准只有一条：

> **这个操作的主要动作是"连续键入大段文字"或"在几百行数据里精确定位"吗？**

| 场景 | 判定 | 处理 |
|---|---|---|
| KPI 看板、图表、榜单 | 否 | 3D 原生（Canvas 纹理贴面） |
| 列表浏览（≤50 行）、详情页 | 否 | 3D 内屏风（CSS3D + 真实 DOM） |
| 表单填写（≤20 字段） | 否 | 3D 内屏风 |
| 单据列表（500+ 行） | 是 | 3D 内屏风 + 虚拟滚动；**仍不跳出**，只是限制同屏行数 |
| DOC 长文写作 | 是 | 殿内"静室"模式：镜头贴到屏风前，屏风占满视野（**仍在 3D 里**，只是构图变了） |
| 实时 IM 聊天 | 是 | 同上，贴脸模式 |

**关键**：连"妥协"都不跳出 3D，只是把镜头推到屏风前面，让屏风占满视野。视觉上你还是坐在殿里看一卷展开的文书。

### 0.4 为什么不能靠"跳 2D"糊弄过去

用户要的"3D 全量"不是审美洁癖，是**一致性**：点一下突然出现白色 AntD 后台，等于告诉用户"刚才那个城是假的"。一旦破窗，整个 3D 层的可信度就没了。

---

## 1. 架构：三层渲染，同一个相机

```
┌──────────────────────────────────────────────────────┐
│  WebGL 层（three.js r169）                            │
│  城池 / 建筑 / 地形 / 天空 / 水体 / NPC / 光效         │
│  ── 负责"世界是真的"                                   │
├──────────────────────────────────────────────────────┤
│  CSS3D 层（CSS3DRenderer，同一个 Camera）              │
│  殿内屏风 / 卷轴 / 书案 / 匾额 / 告示牌                │
│  上面挂真实 DOM（React 组件）                          │
│  ── 负责"界面是真的"（输入法/滚动/选中全部原生）        │
├──────────────────────────────────────────────────────┤
│  HUD 层（普通 DOM overlay，屏幕空间）                  │
│  FPS / 小地图 / 提示 / 操作按钮 / 输入法浮层            │
│  ── 负责"元信息"                                       │
└──────────────────────────────────────────────────────┘
```

### 1.1 三层分工的硬规则

| 内容 | 放哪层 | 理由 |
|---|---|---|
| 只读数据（KPI/图表/榜单） | **WebGL**（Canvas 画好贴 3D 平面） | 零 DOM 开销，透视正确，可做曲面 |
| 可交互界面（表单/列表/详情） | **CSS3D**（真实 DOM） | 输入法、滚动、复制粘贴全部原生 |
| 编辑态的文字输入 | **HUD**（临时提到屏幕层） | 绕开 IME 定位问题 |
| 视频/音频 | WebGL 视频纹理 | 性能最好，可做曲面投影 |

### 1.2 与 2D 后台的关系（并行，不互跳）

```
        ┌──────────────┐        ┌──────────────┐
        │  3D 王城      │        │  2D 后台      │
        │ (whale-world) │        │ (现有 31 应用) │
        └──────┬───────┘        └──────┬───────┘
               │                        │
               └────────┬───────────────┘
                        ▼
                  同一套后端 API
                （/erp /mes /oa /auth …）
```

- **3D 王城是独立完整应用**：不走 qiankun 路由跳转，自己持有登录态，直连网关。
- **2D 后台保持原样**：习惯表格操作的人继续用，一行代码不改。
- **数据同源**：两边调同一批接口，权限同一套。
- **不互相跳转**：3D 里没有"去 2D"的按钮，2D 里也没有"去 3D"的强制入口（只放一个可选链接）。

> 这条定位让架构反而变简单：不用处理 qiankun 冲突、不用管 history 劫持、不用做子应用适配。

---

## 2. 空间模型

```
城池（Whale Capital）
├─ 皇城（皇宫）── 9 中枢殿 + 2 配殿
│   数枢殿（总控）/ 枢密殿（权限）/ 度支殿（支付）
│   藏书殿（文件）/ 时辰殿（任务）/ 政事殿（流程）
│   考功殿（表单）/ 通政殿（消息）/ 起居殿（审计）
│   城门楼（网关，仅地标）
├─ 内城 ── 12 坊（每坊一业务服务）
│   户部(ERP) 工部(MES) 吏部(OA) 礼部(CRM) 兵部(IoT) 翰林(DOC)
│   杏坛(LMS) 市舶(MALL) 将作(PDM) 营造(PM) 盟府(合同) 宣抚(党建)
└─ 外城 ── 9 处（内容与生态）
    乐坊 影戏院 清议堂 邮驿 传信司 舆图司 观星台 智囊院 文库
```

尺度：外城 110 / 内城 58 / 皇城 30（v1 的 92/48/22 装不下 12 坊 + 9 殿）。

---

## 3. 任务清单（细分到可独立开工/验收）

> 约定：每个任务 **0.5–2 天**，必须能独立验收。
> 编号 `T{阶段}.{序号}`，`[ ]` 未开始 / `[x]` 完成。
> 依赖列写明"必须先完成什么"，没有依赖可并行。

### S0 技术验证（Spike）—— 先证伪，再动工

目标：用 6 天回答"这套方案到底能不能跑"。**S0 不通过，后面全部作废**。

| 编号 | 任务 | 产出 | 验收 | 依赖 | 工期 |
|---|---|---|---|---|---|
| T0.1 | [ ] 搭 CSS3D + WebGL 混合最小场景 | 一个立方体 + 一块 DOM 面板同屏 | 两图层对齐、相机同步无漂移 | — | 0.5d |
| T0.2 | [ ] 验证 IME：3D 变换的 input 打中文 | 测试页 + 截图记录候选框偏移量 | 能量化偏移，给出修复方案 | T0.1 | 1d |
| T0.3 | [ ] 验证遮挡：WebGL 柱 vs CSS3D 面板 | 深度遮罩 demo | 柱子能正确挡住面板文字 | T0.1 | 1.5d |
| T0.4 | [ ] 验证性能：3 块面板 + 城池 | 帧率报告（1080p/中档） | ≥30fps，否则给出降级策略 | T0.1 | 1d |
| T0.5 | [ ] 验证现有 React 页面挂入 CSS3D | 把 ERP 一个列表页挂进面板 | 样式不错乱、滚动正常 | T0.1 | 1d |
| T0.6 | [ ] 输出 S0 验证报告 + 决策 | 文档：继续 / 调整 / 放弃 | 舰长签字 | T0.1–T0.5 | 0.5d |

### S1 场景骨架扩容

| 编号 | 任务 | 产出 | 验收 | 依赖 | 工期 |
|---|---|---|---|---|---|
| T1.1 | [x] 改 geometry 到 110/58/30 | `scripts/upgrade-city-layout-v2.py` | 脚本 `--check` 通过 | T0.6 | 0.5d |
| T1.2 | [x] `nodeType` 加 `DISTRICT` | `layout.ts` 联合类型 + `SceneNodeType` | `tsc --noEmit` 通过 | T1.1 | 0.5d |
| T1.3 | [x] 12 坊环形街坊坐标算法 | 升级脚本内 `ring_point()`/`ring_yaw()` | 12 坊同环 R=76、弦长 39.34m | T1.2 | 1d |
| T1.4 | [x] 生成 12 坊布局数据 | `city-layout.json` 26 节点 | 4 新坊（将作监/营造司/盟府/宣抚院）入库 | T1.3 | 0.5d |
| T1.5 | [x] 改 `gen-world-layout-sql.py` 适配新字段 | 生成器改输出 V5 + upsert | `--check` PASS（26 节点/13 绑定） | T1.4 | 1d |
| T1.6 | [x] `sc_scene_node` 迁移脚本 | `V5__scene_world_layout_v2.sql` | V4 冻结，V5 增量覆盖；全新库/老库同终态 | T1.5 | 1d |
| T1.7 | [x] 前端渲染 12 坊 | 场景可见 | 城图 12 点等距；走查无穿模 | T1.6 | 1d |
| T1.8 | [x] 坊墙 + 坊门 + 巷口模型 | **程序化几何**（坊墙/门柱/门楣/匾额） | 每坊一匾、巷口可辨；GLB 化留待 S8 | T1.7 | 1.5d |
| T1.9 | [x] 皇城扩到 30，9 中枢殿坐标 | 布局数据（主殿 + 8 中枢殿 + 2 小阁） | 自检：11 座台基零交叠 | T1.1 | 1d |
| T1.10 | [x] 碰撞体全部更新 | `collision.ts` 支持旋转盒（OBB） | 北向实测穿过两道城门、停在主殿台基前 | T1.9 | 0.5d |

**S1 交付说明（2026-11-19）**

- 数据源单一：`city-layout.json` 是唯一真源，SQL 由 `gen-world-layout-sql.py` 生成、几何由
  `check-city-layout.mjs` 校验，三者不得手改。
- **V4 冻结**：Flyway 会对已执行脚本校验 checksum，改 V4 会让跑过它的环境启动失败。
  v2 布局一律走 V5 增量迁移（INSERT 补新 + UPDATE 按 `node_key` 覆盖），老库与全新库收敛到同一终态。
- **数组顺序契约**：节点主键是 `NODE_ID_BASE + 下标`，因此 `buildings` 只允许原地改写与末尾追加。
  典籍阁 / 玉衡阁 因此没有删除，改为缩到 0.5 倍退到南广场两角 (±10.5, 20)。
- T1.8 用程序化几何先顶上（12 坊 × 2 柱 + 门楣 + 匾额 + 5 段墙），Blender GLB 版留到 S8 统一换。
  这样做的好处是坊门位置立刻能和碰撞盒对齐——两者共用 `DISTRICT_WALL` 常量。
- 踩过的坑：坊墙的相机遮挡**不能**用外接圆近似（12 个 R≈17m 的圆在环上首尾相接，
  等于给内城套了一圈隐形墙，镜头会被一路顶到贴脸），必须按旋转矩形判断。

### S2 界面容器（屏风系统）

> **状态：已完成（2026-11-19）**。实现见 `whale-world/src/world/screen.ts` + `src/screen.css`，
> 验收脚本见 `whale-frontend/e2e/tools/world-screen-*.mjs`（6 个，全部通过）。
> 关键改动：进入屏风时把屏风**平面化**（搬进 `#bww-planar` 层、用 2D 仿射定位），
> 同时解决 IME 候选框定位（T2.10）与贴脸静室（T2.11）——两件事不必分开做。

| 编号 | 任务 | 产出 | 验收 | 依赖 | 工期 |
|---|---|---|---|---|---|
| T2.1 | [x] CSS3DRenderer 接入 world | 渲染管线 | 与 WebGL 同相机同帧 | T0.6 | 1d |
| T2.2 | [x] 相机同步与 resize 处理 | 同步模块 | 窗口缩放不错位 | T2.1 | 0.5d |
| T2.3 | [x] 屏风组件（尺寸/边框/材质） | `Screen` 组件 | 三种规格（小/中/大） | T2.1 | 1d |
| T2.4 | [x] 屏风内容挂载点 API | `mount(el)` 接口 | 挂 React 节点不报错 | T2.3 | 0.5d |
| T2.5 | [x] 屏风交互（hover/点击/焦点） | 事件层 | 点击穿透正确、焦点可见 | T2.4 | 1d |
| T2.6 | [x] 进入屏风：相机推近 + 放大 | 过场动画 | ≤800ms，无跳变 | T2.4 | 1d |
| T2.7 | [x] 退出屏风：Esc/后退回城 | 反向过场 | 状态完整恢复 | T2.6 | 0.5d |
| T2.8 | [x] 遮挡遮罩实现 | 深度遮罩 pass | 柱/墙正确挡住面板 | T0.3 | 1.5d |
| T2.9 | [x] 滚动/滚轮事件优先级 | 事件仲裁 | 面板内滚动不带动相机 | T2.5 | 1d |
| T2.10 | [x] IME 输入定位修复 | 平面化提层 | 输入框无 3D 祖先 | T0.2 | 1.5d |
| T2.11 | [x] 贴脸模式（静室） | 屏风占满视野 | 长文/大表可全屏操作 | T2.6 | 0.5d |
| T2.12 | [x] 全局 ConfigProvider 弹层容器 | `screenPopupContainer()` | 弹层不脱 3D 变换 | T2.4 | 0.5d |

**S2 实测数据（不是"看着差不多"）：**

| 指标 | 结果 |
|---|---|
| 进入后屏风占屏 | 82% × 81%，中心偏移 0px |
| 过场时长 | 680ms（缓动 easeInOutCubic） |
| 退出复位 | 矩形与进入前逐像素一致（997,394,180,92） |
| 遮挡层参与合成 | 同区域 A/B 像素差 **21.6%**，石柱正确切掉屏风左缘 |
| 输入态 3D 祖先 | 进入前 `true` → 进入后 **`false`** → 退出后 `true` |
| 屏风内滚轮 | 相机零变焦；空地滚轮正常变焦 |
| resize | 1440×900 / 1024×700 / 1920×1080 三档下宽高比恒为 1.966 |


### S3 数据厅框架

| 编号 | 任务 | 产出 | 验收 | 依赖 | 工期 |
|---|---|---|---|---|---|
| T3.1 | [x] `DataHall` 组件骨架 | 参数化厅 | 传 JSON 出厅 | T2.1 | 1d |
| T3.2 | [x] KPI 卡（Canvas → 纹理） | 3–6 张大字卡 | 数字清晰、随透视变形 | T3.1 | 1d |
| T3.3 | [x] 图表卡（折线/柱/环形） | 3 种图表 | 数据变化正确重绘 | T3.2 | 1.5d |
| T3.4 | [x] 告警条 + 状态灯 | 檐角灯 + 侧墙条 | 红黄绿切换正确 | T3.1 | 1d |
| T3.5 | [x] 厅内导航（殿间切换） | 左右殿门 | 不退出厅即可切换 | T3.1 | 0.5d |
| T3.6 | [x] 数据刷新（30s 轮询） | 刷新器 | 离开厅即停止 | T3.1 | 0.5d |
| T3.7 | [x] 降级态（接口挂/无权限） | 空态/闭门态 | 不白屏、不卡场景 | T3.1 | 1d |

**S3 交付说明（2026-11-20）**

- 实现见 `whale-world/src/world/datahall.ts`，验收脚本 `whale-frontend/e2e/tools/world-datahall-smoke.mjs`（全绿）。
- **数据不另造**：厅里所有数字来自 S0 已有的 `metrics.cards`（8 路只读概览接口），本阶段零新增后端接口。折线图的时间序列由每次 setData 累积的真实采样派生——刷新一次多一个点。
- **空间隔离**：厅是自包含 Group，驻留 y=400，平时 `visible=false`（连同厅灯一起被 three 跳过，对城池零开销）；进厅时相机 snap 到固定机位，城池跟随相机冻结，退出时按 `getFollowPose` 复位。不改 collision/layout/数枢殿几何。
- **刷新调度**：进厅停城池 60s 采集、起 30s 厅内刷新；离厅反向恢复。两套定时器不并存（避免 8 路接口被刷两遍）。`metrics.isRunning()` 探针供 e2e 断言"离厅即停"。
- **进入方式**：`V` 键（从最近殿坊的厅看起）或 `?debug=1` 下的 `__bwwHallOpen` 探针；厅内 `← →` 切厅、`Esc` 出厅、左右殿门可点击。
- **降级态**：无权限/接口挂时 KPI 显示「无凭据」、图表显示「无可比数据」、状态灯转灰/红——不白屏、不卡场景。`__bwwHallMock()` 仅在 debug 下注入假数据供截图评审，不进正常路径。
- **e2e 回归口径**：`world-city-smoke/path/weather/audio/guide/say` 的失败为 S1 城郭扩容（8→12 坊、传送门 9→13）后的旧断言遗留 + SwiftShader 软渲染时序抖动，已用 `git stash` 在干净基线复现同样的失败，确认非本阶段引入。`quest/glyphs/screen-smoke/datahall-smoke` 全绿。

### S4 接口层（一对多，先通一个再复制）

| 编号 | 任务 | 产出 | 验收 | 依赖 | 工期 |
|---|---|---|---|---|---|
| T4.1 | [x] `overview` schema 定稿 | JSON Schema + 文档 | 前后端签字 | T3.1 | 0.5d |
| T4.2 | [x] 后端通用 starter（摘要端点基类） | `OverviewSupport` | 新服务继承即用 | T4.1 | 2d |
| T4.3 | [x] 前端 overview 客户端 + 类型生成 | TS 类型 | 字段对齐 schema | T4.1 | 1d |
| T4.4 | [x] 标杆：ERP overview | `/erp/overview` | 3 个 KPI 与后台一致 | T4.2 | 1.5d |
| T4.5 | [x] 标杆：ERP 屏风页（列表+详情） | 一个完整业务页 | 滚动/筛选/查看详情可用 | T4.3,T2.4 | 2d |
| T4.6 | [x] 标杆：ERP 厅配置 | 一份 JSON | 进殿看到真数据 | T4.4,T3.3 | 0.5d |

**S4 交付说明（2026-11-21，两批：T4.1–T4.4 / T4.6 已交付；T4.5 屏风页已补齐）**

- 契约：`docs/specs/overview-contract/README.md` + `overview.schema.json`（前端 TS 类型的唯一来源）。
  一次把三件事定死：① 百分比**只传比率**（S3 前端"小于 1.5 就当比率"的兜底在真实 0.8% 上会显示成 80%）；
  ② 三态 `live/stale/degraded` —— "有数字"与"数字是真的"必须能区分；③ 降级也走同一形状，前端不为 404 写第二套解析。
- 后端：新增 `whale-sdk/whale-overview-sdk`（自动装配）。接入成本 = 实现 `OverviewProvider` + 配
  `whale.overview.base-path`。SDK 统一提供 TTL 缓存（键含租户，防跨租户串数）、provider 超时中断（独立
  daemon 线程池，`Future.cancel(true)`）、失败降级（有旧值→`stale`，无→`degraded`，**失败不写缓存**所以下轮自动重试）、
  权限 403、`overview.load{service,outcome}` 计数（Micrometer 缺失时自动退化为空实现）。
- 标杆：`/erp/overview`（`ErpOverviewProvider`）三个 KPI 直接复用 `AnalyticsAppService#getCockpit` ——
  与 2D 经营驾驶舱**同一个方法**，3D 不许另造口径。权限码复用 `sys:erp:bi:cockpit:list`，**没有新增权限点、没有新迁移**。
- 前端：`scripts/gen-overview-types.mjs` 从 schema 生成 `src/world/overview.types.ts`（`--check` 可进 CI）；
  `overview.ts` 负责取数与映射（denied/超时/非 JSON 全部折成结果对象，永不抛）；`halls.ts` 是"厅配置"——
  **加一行就多一个殿走契约接口**，未登记的殿继续用 S3 的 metrics 采集，两条路并存逐殿替换。
- 验证：SDK 26 例（含契约字段/枚举往返、租户隔离、超时、stale/degraded、监听器失败不影响取数、装配条件、
  base-path 绑定）+ ERP provider 4 例；checkstyle 0 违规；`tsc -b`/`eslint src --max-warnings 0`/`vite build` 通过；
  e2e `world-datahall-smoke` 25 项全绿、`world-glyphs` 全绿。`world-city-smoke` 3 项失败为 S1 扩容后的
  旧断言遗留（传送门 9→13、内城门外坐标 51→58），S3 交付时已在干净基线复现过。
- 负向验证：把租户从缓存键里去掉 → `shouldIsolateTenants` 失败（rc=1）；恢复后全绿。
- 起服验证待办：本环境 Nacos 未起，`GET /erp/overview` 的真实 HTTP 断言未跑；起服后按
  `curl -H "X-User-Id: 1" -H "X-Permission-Codes: sys:erp:bi:cockpit:list" http://127.0.0.1:8080/erp/overview` 复核。

**S4 第二批（2026-11-21，T4.5 ERP 屏风页）**

- 屏风页不是"再画一块板"，而是**真业务页**：`screenpage.ts` 是通用渲染器（KPI 条 + 图表 + 关键字/状态筛选
  + 分页 + 行点击详情 + 返回），`screenpages.ts` 是按殿登记的配置（列、状态字典、详情字段、分录列）。
  接入第二张列表 = 加一条配置，不改渲染器；S5 的 9 殿 3 件套靠这条路径复制。
- 概览侧补齐 `charts` 与 `links`：`ErpOverviewProvider` 的柱状图直接取同一份 cockpit 快照的
  营收/成本/利润三列（不新增查询、不新增口径），采购订单入口按 `ctx.hasPermission` 裁剪——
  没权限的人连"能点进去"这件事都不下发。
- 数据通路：列表与详情**复用 2D 后台的分页/详情接口**（`/erp/scm/purchase-order/page`、`/{id}`），
  权限码复用 `sys:erp:purchase:list`；**无新接口、无新权限点、无新迁移**。契约 §2.5 同步写明
  `links[].path` 的语义就是这个列表接口。
- 位置按台基反算：户部台基深 `(7+9)×0.95 = 15.2m`、顶面高 2.47m，屏风若按殿心偏移会插进台基
  （CSS3D 不做深度测试，看上去就是浮在半空）。现沿殿正面法线退到台基外的地面（15.2, 64.6），
  数值校验：屏风底边投影 563px vs 地面投影 556px，落在地上。
- 交互硬约束：**屏风没进入时，行/按钮不响应**。否则玩家在城里"点一下屏风想进去"，事件会先落到
  表体行上弹出详情，再被过场盖住——表现为"进殿后莫名其妙停在详情页"。
- 验证：`e2e/tools/world-screenpage-smoke.mjs` 35 项全绿（进屏风的前后态、关键字筛选、状态筛选、
  4 页翻页与末页禁用、行点击详情、详情 8 字段 + 12 行分录、分录区滚动、整页不滚、Esc 退出、零控制台错误）。
  回归：`world-screen-smoke/engage/ime/resize/hint`、`world-datahall-smoke`、`world-glyphs` 全通过；
  `world-city-smoke` 仍是 S1 扩容后的 3 项旧断言失败（传送门 9→13 等），与本阶段无关。
- 已知取舍：屏风内每页 6 行（620px 高里 KPI+图表之后只剩约 230px，6 行刚好排满且不用滚）；
  数枢殿那块写死数据的演示屏仍在，S5 统一按布局生成时一并替换。

### S5 皇城 9 殿（每殿 3 件套：overview + 屏风页 + 厅配置）

| 编号 | 任务 | 服务 | 依赖 |
|---|---|---|---|
| T5.1 | [x] 数枢殿总控（聚合多服务） | 聚合 | T4.6 |
| T5.2 | [x] 枢密殿 | auth | T5.1 |
| T5.3 | [x] 度支殿 | pay | T5.1 |
| T5.4 | [x] 藏书殿 | file | T5.1 |
| T5.5 | [x] 时辰殿 | job | T5.1 |
| T5.6 | [x] 政事殿 | workflow | T5.1 |
| T5.7 | [x] 考功殿 | form | T5.1 |
| T5.8 | [x] 通政殿 | message | T5.1 |
| T5.9 | [x] 起居殿（只读） | audit | T5.1 |
| T5.10 | [x] 城门楼地标（网关，无入口） | gateway | T1.9 |

> 每殿 3 个任务（接口 / 屏风页 / 厅配置），实际约 **27 个**。为控制篇幅此处按殿合并，开工时展开为 `T5.x.1/.2/.3`。

**S5 第一批交付说明（2026-11-21，T5.1 数枢殿总控）**

- **聚合放在服务端，不是前端拼**。S3 那套"八路异构接口各猜字段"最致命的不是字段漂移，
  而是**没有"没数"这个状态**：一路失败就少一张卡，看起来像"这殿今天没业务"。总控必须能给
  每座殿标 `live/stale/degraded/denied`，而这个判定只能发生在看得到全部下游结果的地方。
- **宿主是 whale-monitor**（`com.bluewhale.monitor.overview`）。它是本工程唯一的无库采集型服务，
  总控要做的事与它同构；新起一个"总控服务"要再占一档内存（VM 余量本就吃紧），收益只是名字更贴切。
  端点 `GET /monitor/overview`（概览）+ `GET /monitor/overview/halls[/{nodeKey}]`（屏风页数据）。
- **接入一殿 = 加一行配置**：`whale.overview.aggregate.targets`（`nodeKey/name/url/permission`）。
  容器内用容器名直连（`http://bw-erp:9001/erp/overview`），**不经过网关**——网关挂了，总控仍要能看到
  各殿真实状态；这也是"总控不依赖被监控对象"的最小前提。当前只登记户部，T5.2–T5.9 逐殿追加。
- **单殿失败隔离**：并行问询 + 单殿 2s 超时（SDK 那层 5s），一座挂只让那一行变红。
  四张 KPI 是 接入殿 / 通畅 / 陈旧 / 未通；`denied` 单独一档——"去要权限"和"去查服务"是两件事，
  混成一档会让值班的人先翻半小时日志。
- **身份透传而不是服务身份**：转发调用者的 `X-User-Id / X-Tenant-Id / X-Permission-Codes`，
  各殿按自己的权限码裁剪。总控**不设权限码**：它的可见范围就是"你在各殿能看到的范围"，
  再加一道总控权限只会把"没权限"和"没这条接口"混成同一个 403。
- **SDK 修复：缓存键补权限签名**。原键是 `service|nodeKey|tenant`，而契约要求 provider 按权限做块级裁剪
  ——同租户里权限不同的两个人看到的快照本来就不是一份东西，共用缓存等于把有权限的人的数发给没权限的人。
  负向验证：去掉签名后 `shouldIsolatePermissions` 失败（rc=1）。
- **SDK 补 `Overviews.formatValue/formatKpi`**：总控一行里混着金额、计数与百分比，一列只能有一种排版，
  只能服务端先排成字符串。规则与前端 `overview.ts#formatValue` 逐条对齐并有单测钉住
  （`OverviewFormatTest` 6 例）。
- **屏风页**：数枢殿那块 S2 的写死演示屏换成真总控页（`HUB_HALLS_PAGE`，四列 = 殿/主指标/状态/说明）。
  分页与关键字/状态筛选**在服务端生效**（`current/size/keyword/state`），否则就是"表里 6 行却写着第 1/2 页"的假分页。
  行里的主指标与数据时刻由服务端排版（`营收 ¥123.4万`、`11-21 03:00`），前端不再二次格式化。
- **顺手修掉一个 S2 遗留的真 bug**：退出屏风后整块纸面糊在镜头上、且再也点不动。
  根因是 three 的 `CSS3DRenderer` 用 `WeakMap` 缓存"上次写进 `element.style.transform` 的字符串"，
  只有字符串变化才重写；屏风退出后矩阵与进入前完全一致 → 缓存命中 → 渲染器跳过，
  而我们刚把 inline transform 清空。修法：还原时**换一个新的 `CSS3DObject`**（`Screen#reattach3d`），
  让缓存必然失效。三块屏风（告示/户部/数枢殿）复测：退出后矩形回到 `268×138`（此前是 `16948×3202`）。
- **验证**：`e2e/tools/world-shushu-smoke.mjs` 31 项全绿（用 `page.route` 返回与后端同形状的信封，
  顺带断言分页/筛选参数真的发给了服务端；末段把接口打成 500，验"有旧值就标陈旧、列表给人话+重试"）。
  后端 `HubOverviewAggregatorTest` 7 例（三殿 live/stale/500、身份转发、缺码不发请求、慢殿超时、
  TTL 缓存、空配置、详情映射）+ SDK 33 例；checkstyle 0 违规。
  回归：`world-screenpage-smoke` 35 项、`world-screen/engage/ime/resize/hint/occlusion/wheel`、
  `world-datahall-smoke`、`world-glyphs`、`world-quest` 全通过。
- **已知遗留**：`world-city-smoke` 3 项（传送门 9→13 等 S1 旧断言）、`world-guide` 1 项
  "中止后能正常走"（位移 0.8m < 1.5m；已用 `git stash` 在改动前基线复现同样结果，非本批引入）；
  guide 的站点数断言已按 13 站修正。真接口 HTTP 仍未验（本机 Nacos/Docker 未起）。

**S5 第二批交付说明（2026-11-21，T5.2 枢密殿 / auth）**

- **auth 概览**：`GET /auth/overview`，四张 KPI（启用账号 / 在用会话 / 今日登录 / 今日失败）
  + 两张图（近 7 日成功/失败趋势、今日结果构成）。权限码复用 2D 登录历史页的
  `sys:login:history:list`，不为 3D 另发一套授权。
- **统计下沉成端口** `AuthSecurityStatQuery`，**租户显式传参**：不读线程上下文——聚合总控时
  线程上挂的是"调用者"身份，靠上下文取租户等于把别人的数算进来；显式传参让这种漏数在编译期就写不出来。
  一次聚合供 KPI 与趋势共用，不为一屏打三遍库。
- **按天聚合的 SQL 条件列不套函数**（`login_time >= ? AND login_time < ?`）：
  写成 `DATE(login_time) = ?` 会让 `idx_login_history_time` 失效，数据一多就是全表扫。
- **告警两条只出一条**：次数（今日失败 ≥ 20）先判 `CRITICAL`，占比（≥ 30% 且样本 ≥ 5）作 `WARNING` 补刀。
  同时报两条会让值班分不清"是量大事"还是"是比例事"，而处置动作是同一个（去看登录历史）。
  样本 < 5 不谈占比——1 失败 0 成功就是 100%，报了只会让人关告警。
- **屏风页**：`/login-history/v1/page`，五列（账号 / 结果 / 方式 / 来源 IP / 时间），结果用中文徽标、失败标红。
  **筛选只给 `result` 一个**：接口没有 `keyword` 参数，屏风就不给搜索框——给了就是"输入了没反应"。
  详情 8 个字段（含失败原因 / 客户端 / 设备指纹），失败原因不在列表里重复，但详情必须能看到。
- **新增 `datetime` 列口径**：事件时间到分钟。只到日期时，同一天多条等于没给时间；
  不做时区换算——库里存的就是本地时间，前端再转一次会整体差 8 小时。
- **总控接入**：`targets` 加 `shumi-hall`（`http://bw-auth:8081/auth/overview`，权限码同上），
  数枢殿总控里出现第二行真数据，聚合链路（并行问询 / 单殿超时 / 身份透传）得到第二个样本。
- **验证**：`e2e/tools/world-shumi-smoke.mjs` 32 项全绿（概览 live、四 KPI、两图、无 keyword 搜索框、
  结果筛选参数真的发到服务端、翻页/详情/返回、Esc 退出后回到 3D 尺寸、接口 500 时标 stale 且列表给人话）。
  后端 `AuthOverviewProviderTest` 7 例、auth 架构测试 8 例、总控 13 例；checkstyle 0；六项门禁 PASS
  （api-path-drift 1571 处调用全命中、perm-mapping 989 项、internal-boundary 127 条、qiankun 23 个子应用）。
- **顺手修掉第一批遗留的 4 条坏断言**（都是断言过期，不是产品缺陷）：
  1. `world-city-smoke` 传送门写死 9 项（S1 扩容后是 13）；且 `E` 键自 S2 起"屏风优先于传送门"，
     出生点旁就是告示屏，测试要先走到城内空处再按 `E`，否则按 `E` 进的是屏风。
  2. 城墙负向用例起点按旧布局写 `z=51`，而内墙已扩到 `z=58`——起点其实在城里，等于没测门洞。
     改 `z=62`：门洞可穿到 `z<58`，贴墙停在 `59.5`；皇城墙同样复测停在 `31.6`，碰撞逻辑本身正确。
  3. 传送门面板"可见"原来按 `opacity > 0` 判。软件渲染（swiftshader）下 CSS 过渡能拖到 3s 以上，
     1.2s 时读到 `opacity=0` 就误判成"面板没开"。改判 `bww-open` 类 + `visibility` + `pointer-events`，
     卡片可见性用轮询等待，不写死等待时长。
  4. 导览"中止后能正常走"按位移 > 1.5m 判。主循环 `dt` 上限 0.05s，软件渲染下约 1fps，
     2.2s 累计位移天然只有 ~1m——这条一直在测显卡。改判"确实动了且方向对"，
     起点从已被新增建筑挡住的 `(0,78)` 换到 `(0,100)`。

**S5 第三批交付说明（2026-11-21，T5.3 度支殿 / pay）**

- **pay 概览**：`GET /pay/overview`，四张 KPI（今日收款 / 今日建单 / 待支付 / 今日退款）
  + 两张图（近 7 日收款趋势（line，金额）、今日渠道构成（bar，金额））。
  权限码复用 2D 支付单管理的 `sys:pay:list`，屏风页复用 `/pay/order/page`，**无新接口、无新权限点**。
- **退款不冲减收款**：收款口径是 `status IN ('PAID','REFUNDED')` 且按 `pay_time` 落在窗口内（毛额），
  退款走 `pay_transaction` 里 `type='REFUND' AND status='SUCCESS'` 单独统计。两条并行口径而不是
  「收款减退款」——净额会把"今天收 100 退 80"显示成 20，规模感全丢；而值班要看的恰恰是
  "进账多少、退了多大规模"，这两个数分开才看得出问题。
- **失败告警只数 PAY 类型的 FAILED**（`type='PAY' AND status='FAILED'`）。退款失败混进渠道故障，
  会把"支付通道挂了"稀释成"偶发失败"，而两者处置动作不同（一个找通道、一个找退款原因）。
- **两条告警可并存**：失败笔数 ≥ 5 报 `CRITICAL`（看渠道）、待支付里超 24 小时未回调 ≥ 10 报
  `WARNING`（看回调）。T5.2 登录那条是"二选一"（同一个处置动作），这里相反——不同根因就要都报出来。
- **统计下沉成端口** `PayOverviewStatQuery`（8 个方法、4 个 record），租户**显式传参**、时间窗统一
  用 `invalidWindow()` 拦 `tenantId==null / from/to==null / !from.isBefore(to)`，空结果一律折 0 不折 null；
  渠道为空白折 `"UNKNOWN"`（支付单允许没有渠道，空字符串当类目会让柱图出现一根无名柱）。
  一次聚合供 KPI 与图表共用，不为一屏打三遍库。
- **索引按查询形状补**（`V3__pay_overview_index.sql` + `init.sql`，先查 `STATISTICS` 再 `ADD INDEX` 幂等）：
  `pay_order(tenant_id, pay_time)`、`pay_order(tenant_id, status, create_time)`、
  `pay_transaction(tenant_id, type, status, create_time)`。时间条件列**不套函数**
  （`DATE(pay_time)=?` 会让索引失效）；按天聚合才用 `DATE(pay_time)`，那条是离线趋势、量已收窄到 7 天。
- **屏风页**：`PAY_ORDER_PAGE` 五列（单号 / 业务标识 / 金额 / 状态 / 建单时间），
  状态用中文徽标（待支付 warn、已支付 good、已取消 flat、已退款 warn）；**keyword 与 status 两个筛选都给**
  ——接口真支持这两个参数，给了就真发。**不配 `detailPath`**：列表行已含全部详情字段，
  再打一次详情接口只是多一次往返；行点击用行数据直接摊详情（10 个字段，含渠道流水号 / 已退金额 / 回调时间）。
- **屏风 3D 摆放修正（同时改掉 T5.2 的错值）**：`ScreenSpec.position` 是屏风**中心**，
  而皇城台基高 `2.6 × scale = 1.87`、大屏高 3.1 → 中心应在 `1.87 + 1.55 ≈ 3.42`。
  T5.2 取的 `2.55` 是把它当底边估的，屏风下半截插在实心台基里；CSS3D 不做深度测试，
  插进去的底边不会被打掉，看上去就是"屏风浮在台基前"。现统一为
  **本殿台基上、殿门前 `z = 殿心 + 4.5`、`y = 3.42`**（台基前沿 +5.4，留 0.9 米石面垫脚）。
- **验证**：`e2e/tools/world-duzhi-smoke.mjs` 36 项全绿（四 KPI、两张图、**有搜索框且 keyword 真发真筛**、
  金额带 `¥` 千分位、待支付黄灯、详情 10 字段、Esc 退出后回到 3D 尺寸、接口 500 标 stale 且列表给人话）。
  后端 `PayOverviewProviderTest` 8 例（KPI 顺序 / 退款不冲减 / 7 天补 0 / 无收款占位类目 /
  失败告警 CRITICAL / 超时告警 WARNING 可与失败并存 / 无租户 / 无权限不下发入口）+ `whale-pay` 全量 51 例；
  checkstyle 0 违规。回归：`world-shumi` 32、`world-shushu` 31、`world-screenpage` 35、
  `world-city-smoke`、`world-guide` 19、`world-quest` 42、`world-datahall`、`world-glyphs`、
  `world-screen/{smoke,engage,ime,resize,hint,occlusion,wheel}` 全通过。
- **已知取舍（布局级，不在本批改）**：皇城九殿 14 米一档、台基深 10.8 米，殿与殿之间只剩 **3.2 米**夹道，
  屏风只能在这个夹道里近距观看（约 2–4 米）。屏风本身是"看一眼有什么、按 E 进去读"的入口，
  进入即平面化全屏，近距不影响读数；要真正拉开观看距离得改九殿间距，那是布局变更。

**S5 第四批交付说明（2026-11-21，T5.4 藏书殿 / file）**

- **file 概览**：`GET /file/overview`，四张 KPI（文件总数 / 占用空间 / 今日新增 / 回收站）
  + 两张图（近 7 日新增（line，件数）、按桶分布（bar，件数））。权限码复用 2D 文件列表的
  `sys:file:list`，屏风页复用 `/file/v1/page`，**无新接口、无新权限点**。
- **「文件数」与「占用空间」故意不同口径**：文件数只数**根文件**（`parent_id IS NULL`，
  与人看到的列表条数一致），占用空间算**全部行**（含各版本行——版本行不出现在列表里，
  但实实在在占盘）。两个数都用根行会少算盘，都用全部行会让"文件变多了"的错觉。
- **回收站口径跟 2D 回收站页一致**：`deleted = 1 AND parent_id IS NULL`；占用字节算该文件
  的整组（版本行也随根文件一起被逻辑删除）。超期判定线 = `now - retention-days`，
  `retention-days <= 0`（无限期保留）时**不查超期**——否则每份被删的文件都会变成一条告警，
  值班的人第二天就把告警关了。
- **告警两条可并存**：① 回收站有文件超过保留期未清理 → `WARNING`「清理作业可能没跑」
  （与作业心跳同源的静默失效）；② 回收站占用 ≥ 5GB → `WARNING`「确认保留期或手动清理」
  （容量问题）。**清理本身没开启时两条都不报**，改用 note 说明——配置成不清理不是故障。
- **统计端口 `FileLibraryStatQuery` 放在 domain 层**：file 是分层模块
  （`interfaces → application → domain ← infrastructure`），
  infrastructure **不依赖** application，端口放 application 层会导致实现类编不过
  （第一次就撞了：`程序包 com.bluewhale.file.application.port 不存在`）。
  放 domain 后两个方向都成立，也与既有 `FileAssetRepository` 同层。
- **索引按查询形状补**（`V9__file_overview_index.sql` + `init.sql`，先查 `STATISTICS` 再 `ADD INDEX`）：
  `(tenant_id, deleted)`、`(tenant_id, create_time)`、`(tenant_id, deleted, delete_time)`；
  建好第一条后**删掉冗余的单列 `idx_tenant_id`**（最左前缀已被覆盖，上传链路少维护一棵索引）。
  时间条件列不套函数；按天聚合才用 `DATE(create_time)`。
- **屏风页**：`FILE_ASSET_PAGE` 五列（文件名 / 桶 / 大小 / 类型 / 上传时间）。为此新增
  **`size` 列口径**（1024 进制 → `1.5 GB`）：文件大小既不能套 `money`（会显示成 `¥1.5`），
  也不能套 `count`（1234567 个字节没人读得出来）。关键字参数是 `originalName` 而**不是通用
  `keyword`**，发错参数名后端不报错、只是不过滤——e2e 专门断言了参数名。
  **不给状态下拉**：`FilePageRequest` 没有 status 参数（文件状态只在管理端用），
  给了就是一个不生效的框（与度支殿正相反的一对判据）。
- **总控接入**：`targets` 加 `cangshu-hall`（`http://bw-file:8085/file/overview`，权限码同上），
  数枢殿总控里出现第四行真数据。
- **验证**：`e2e/tools/world-cangshu-smoke.mjs` 37 项全绿（四 KPI、两图、`size` 三档
  B/KB/GB、搜索框存在且参数名是 `originalName`、无状态下拉、翻页/详情 9 字段、
  详情不再打二次接口、Esc 退出后回到 3D 尺寸、接口 500 标 stale 且列表给人话）。
  后端 `FileOverviewProviderTest` 11 例 + `whale-service-file` 全量 101 例通过；checkstyle 0。
  回归：`world-duzhi` 36、`world-shumi` 32、`world-shushu` 31、`world-screenpage` 35、
  `world-city-smoke`、`world-guide` 19、`world-quest` 42、`world-screen/{smoke,engage,ime,resize,hint,occlusion,wheel}`
  全通过；八项门禁 rc=0（perm-mapping 991、api-path-drift 1571、internal-boundary 127）。
- **一个环境坑（记下来省下次的两小时）**：截图里 WebGL 画布全白、DOM 正常，既不是代码问题也不是
  数据问题——`canvas.getContext('webgl2').isContextLost()` 返回 `true`。宿主机可用内存只剩约 1GB 时，
  **1440×900 + d3d11 会丢上下文**（同一脚本降到 1024×640 立刻恢复）。判断"是渲染挂了还是模型没加载"
  最快的一步就是读 `isContextLost()`，不要靠截图猜。

**S5 第五批交付说明（2026-11-21，T5.5 时辰殿 / job）**

- **job 概览**：`GET /job/overview`，四张 KPI（任务总数 / 今日执行 / 今日失败 / 静默任务）
  + 两张图（近 7 日执行（line，次数）、近 7 日失败最多的任务（bar，次数））。
  权限码复用 2D 定时任务页的 `sys:job:list`，屏风页复用 `/job/v1/info/page`，
  **无新接口、无新权限点**。
- **只数已结束的执行**（`status IN (1, -1)`）：执行中的行在结束时才回写，
  把它算进分母会让"今日执行 20 次、成功率 60%"每隔几秒跳一次，看的人分不清是故障还是刷新。
- **静默任务只展示、不逐条告警**：判据只有"近 24 小时有没有执行记录"，**不看 cron 周期**。
  月级任务（如 LMS 学时结算 `0 0 1 1 * ?`）在这个窗口里天然是空的，逐个报等于制造噪音，
  值班的人一周内就会把这条告警关掉。只有**所有**运行中任务一起静默时才发 `CRITICAL`——
  那是调度器停摆（或租户上下文丢了），不是某个任务周期长。这个判据与 `audit` 的作业心跳同构。
- **失败告警"量先判、比例作补刀"**：今日失败 ≥ 5 报 `CRITICAL`；否则总样本 ≥ 10 且失败占比
  ≥ 30% 报 `WARNING`。两条并列会让值班分不清"是量大事"还是"是比例事"，而处置动作是同一个
  （去看执行日志）。样本 < 10 不谈占比——1 失败 0 成功就是 100%。
  这两条与静默告警**可以同时出现**（不同根因：一个查调度线程，一个查任务自身）。
- **统计端口 `JobScheduleStatQuery`**（6 方法 / 4 record）：租户**显式传参**、窗口统一用
  `invalidWindow()` 拦、空值折 0 不折 null、任务名为空折「未命名任务」。
  **没有复用 `JobLogService#stats`**：那个方法为 2D 统计页服务，把窗口内明细整批回捞到内存
  （上限 10 万行）再算 P95/TopN；屏风页 60 秒轮询一次，拿它当数据源等于每分钟拉 10 万行。
- **静默判定用 `NOT EXISTS` 而不是 `LEFT JOIN ... HAVING MAX(start_time) < ?`**：
  后者在"从未执行过"的任务上 `MAX` 为 NULL、比较结果也是 NULL，**这些最该被点名的任务
  反而会被过滤掉**。
- **失败 Top 按 `job_name` 分组而不是 `job_id`**：同一任务改名后会留下两个 job_id 的历史行，
  按 id 分组会让它占掉两个坑位，看起来像"两个任务都在失败"。
- **索引按查询形状补**（`V17__job_overview_index.sql` + `init.sql`，先查 `STATISTICS` 再 `ADD INDEX`）：
  `job_log(tenant_id, start_time)`、`job_log(tenant_id, status, start_time)`、
  `job_log(job_id, start_time)`、`job_info(tenant_id, status)`；`idx_job_id` 被
  `(job_id, start_time)` 最左覆盖，建好后删掉。时间条件列不套函数，按天聚合才用 `DATE(start_time)`。
- **屏风页**：`JOB_INFO_PAGE` 五列（任务 / 分组 / 计划 / 状态 / 下次执行）。
  **keyword 与 status 两个筛选都给**——`JobInfoQueryRequest` 真支持这两个参数；
  状态值是后端的 `'1'/'0'` 而不是自造枚举（发 `RUNNING` 这种值后端不报错、只是不过滤，
  e2e 专门断言了发出去的是 `status=1`）。「已停止」给**灰灯不是红灯**：停用是主动决定，
  不是故障，给红灯会让人去查一个本来就不该在跑的任务。Cron 与错过策略原样显示，
  不在前端翻译成人话——翻译错了比显示原文更难查。不配 `detailPath`（列表行已含全部详情，12 个字段）。
- **总控接入**：`targets` 加 `shichen-hall`（`http://bw-job:8082/job/overview`，权限码同上），
  数枢殿总控里出现第五行真数据。
- **验证**：`e2e/tools/world-shichen-smoke.mjs` **44 项全绿**（四 KPI、失败/静默两张 KPI 亮黄灯、
  成功副标带百分比、搜索框 + 状态下拉都在、状态值是 `1/0`、关键字与状态参数真发真筛、
  已停止灰灯、翻页/详情 12 字段、详情不再打二次接口、Esc 退出后回到 3D 尺寸、接口 500 标 stale）。
  后端 `JobOverviewProviderTest` **12 例** + `whale-service-job` 全量 **162 例**通过
  （含 ArchUnit 分层守卫 3 例、Flyway 迁移脚本守卫）；checkstyle 0。
  回归：`world-cangshu` 37、`world-duzhi` 36、`world-shumi` 32、`world-shushu` 31、
  `world-screenpage` 35、`world-city-smoke`、`world-guide` 19、`world-quest` 42、`world-datahall`、
  `world-glyphs`、`world-screen/{smoke,engage,ime,resize,hint,occlusion,wheel}` 全通过；
  八项门禁 rc=0（perm-mapping **992**、api-path-drift 1571、internal-boundary 127、qiankun 23）。
- **补上 T5.4 的欠账**：`whale-service-file-architecture-test` 4 例这次跑通了
  （当时宿主余量 1.1GB，fork 出的 surefire JVM 直接 `malloc failed`）。注意**必须带 `-am`**：
  只跑 architecture-test 单模块时，`interfaces_must_not_depend_on_infrastructure` 规则
  扫到 0 个类，ArchUnit 的 `failOnEmptyShould` 会把它判成失败——那是"扫描对象为 0"，
  不是真的违规（与本项目老门禁 `check-reauth-wiring.py` 曾空转同一个坑）。
- **环境坑**：本次编译连 384m 堆都申请不到（`os::commit_memory ... DOS error 1455`）。
  查下去是 `explorer.exe` 私有内存涨到 **20GB**（工作集 7.7GB），把提交内存吃干；
  重启 explorer 后可用内存从 0.8GB 回到 4.4GB，构建立刻正常。
  遇到"连小堆都 OOM"先看 `PrivateMemorySize64` 排行，不要一味调 `-Xmx`。

**S5 第六批交付说明（2026-11-21，T5.6 政事殿 / workflow）**

- **workflow 概览**：`GET /workflow/overview`，四张 KPI（运行中 / 今日办结 / 待办任务 / 卡住实例）
  + 两张图（近 7 日发起与办结（line，两条序列）、待办积压 Top 5 节点（bar））。
  权限码复用 2D 流程实例页的 `sys:workflow:instance:list`，屏风页复用
  `/workflow/v1/instance/page` 与 `/{id}` 详情，**无新接口、无新权限点**。
- **「卡住实例」单独占一张 KPI，不是只写进告警**：它是这一屏最不可替代的数。
  另外三张（在途 / 办结 / 待办）在 2D 列表页翻一翻也能拼出来，而"运行中却没有任何待办"
  只有把 `process_instance` 与 `process_task` 对起来才看得见——实例停在运行中、
  没人收到催办、也不会失败，只有发起人在等。判据用 `NOT EXISTS`，
  不用 `LEFT JOIN + COUNT(*)=0`（后者在任务表行数上来后会把整张表拉进 join）。
- **告警两条根因不同、可并存**：卡住 → `CRITICAL`（链断了，要看缺陷）；
  待办超时 ≥ 10 → `CRITICAL`、1–9 → `WARNING`（人在拖，要催办）。混成一条会让值班的人
  先去催一个根本没人有待办的流程。
- **办结只算审批通过**（`status = 1` 且看 `finished_at`）：`finished_at` 本就只在通过时写入
  （V10 迁移），驳回与撤销停在 `update_time`。三种终态混成一个"办结"，会让驳回率高的时候
  "办结数"反而更高。
- **超时时长用分钟、向上取整**：`TIMESTAMPDIFF(HOUR, ...)` 会把 30 分钟截成 0 小时，
  告警文案就成了"最久超时 0 小时"——看起来像没超时。
- **统计端口 `ProcessOverviewStatQuery`**（6 方法 / 4 record）：租户显式传参、
  `invalidWindow()` 统一拦时间窗、空值折 0、节点名为空折「未命名节点」；
  `dailyFlow` 在实现里把"发起（`create_time` 分组）"与"办结（`finished_at` 分组）"两条
  按日期并成一张表，省得 provider 各写一遍"两条列表对齐到 7 天"。
- **索引按查询形状补**（`V14__workflow_overview_index.sql` + `init.sql`）：
  `process_instance(tenant_id, status, finished_at)`、`(tenant_id, create_time)`、
  `process_task(tenant_id, status, due_at)`、`(tenant_id, status, node_name)`；
  两张表的单列 `idx_tenant_id` 被复合索引最左前缀覆盖，建好后删掉。
  不动 `idx_status_finished` / `idx_status_due_at`——它们服务的是**后台跨租户扫描**
  （归档、超时扫描线程没有租户上下文），前面带 `tenant_id` 的索引用不上。
- **屏风页**：`PROCESS_INSTANCE_PAGE` 五列（标题 / 流程 / 发起人 / 状态 / 发起时间）。
  关键字参数是 **`title`**，不是 `keyword`——`ProcessInstanceQueryRequest` 只有
  `title / status / definitionCode / initiator` 四个条件，发后端不认的参数会被静默忽略，
  搜索框就成了摆设（T5.4 在 `originalName` 上踩过同一种坑）。
  详情配 `detailPath`：实例详情会把审批链（`tasks`）一并带回，那是这一页最值得看的东西。
- **顺手修掉两个"静默丢数据"**：
  1. **折线只画 `series[0]`**。发起 vs 办结这类"进 / 出"对比只有画在一起才有意义，
     而渲染器只取第一条序列——第二条被静默丢弃，图上看着完全正常，只是少了一半数。
     现改为按调色板逐条描线，多条时补一个最小图例（两根线不加图例分不清谁是谁）。
  2. **状态字典只有页面级**。实例与任务的状态码是同一套数字、含义不同
     （实例 0 = 运行中，任务 0 = 待办）。详情里的审批链会按实例字典翻译任务行，
     "待办"被显示成"运行中"且不报错。现支持**列级 `statusMap` 覆盖**，
     `TASK_STATUS_STYLE` 只作用在审批链那一列。
- **位置**：政事殿在**东列**（`x = +20.5`、殿心 `z = -21`），与西列枢密殿东西对称，
  所以屏风也镜像到东侧（`x = +20.5`、`z = -16.5`），而不是继续沿西列往南排。
  `y = 3.42`（台基 1.87 + 大屏半高 1.55），与 T5.2 起统一的摆法一致。
- **总控接入**：`targets` 加 `zhengshi-hall`（`http://bw-workflow:8083/workflow/overview`，
  权限码同上），数枢殿总控里出现第六行真数据。
- **验证**：`e2e/tools/world-zhengshi-smoke.mjs` **56 项全绿**（四张 KPI 与两张 KPI 黄灯、
  折线真画了两条且有图例、搜索参数名是 `title` 且不夹带 `keyword`、状态值用 `0/1/2/3`、
  已驳回红灯 / 已撤销灰灯、分页、详情 9 字段 + 审批链 3 行、**审批链显示"待办"而不是"运行中"**、
  详情按 id 打真接口、Esc 退出后回到 3D 尺寸、接口 500 标 stale 且列表给人话）。
  后端 `ProcessOverviewProviderTest` **12 例** + `whale-service-workflow` 全量 **259 例**通过
  （含 ArchUnit 3 例、Flyway 迁移守卫 5 例——守卫里新增了 V14 四个索引与
  "init.sql 不得再单建 `idx_tenant_id`"两条断言）；checkstyle 0 违规。
- **已知遗留**：`/workflow/overview` 的真实 HTTP 未验（本机 Docker / Nacos 未起）。

**S5 第七批交付说明（2026-11-21，T5.7 考功殿 / form）**

- **form 概览**：`GET /form/overview`，四张 KPI（启用表单 / 今日提交 / 待审批 / 待修差异）
  + 两张图（近 7 日提交（line）、近 7 日提交最多 Top 5 表单（bar））。
  权限码复用 2D 表单定义页的 `sys:form:list`，屏风页复用 `/form/v1/definition/page`
  与 `/{id}` 详情，**无新接口、无新权限点**。
- **「待修差异」单独占一张 KPI，这是这一屏唯一必须把两张"表"对起来才看得见的数。**
  `form_data` 与各版本物理表 `form_data_{code}_v{n}` 双写，任一侧失败就留下单向数据：
  列表读主表 → 有这一行；点开详情读物理表 → 字段全空。**全程不报错**，
  只有当事人觉得"我明明填了"。它不能并进"数据总量"，否则永远是个看不出来的分母。
- **告警两条根因不同、可并存**：主从不一致 → `CRITICAL`（写库那一步失败了，要看对账页
  与物理表）；待审批超 24 小时 → `WARNING`（流程没人处理，要去找审批人）。
  混成一条会让人先去催一个根本没人有待办的流程。
- **超时按 `update_time` 判、不按 `create_time`**：审批中的行只会被审批回调改动，
  两者等价；但"驳回后重提"的行会被 `update_time` 正确地重新计时，而 `create_time`
  会让它一辈子带着"已卡 30 天"的标签。24 小时是有意的粗粒度——隔夜审批本来就有。
- **只数有效数据**（`deleted = 0 AND status = 1`）：作废行还在表里，算进提交量会让
  考核口径单向偏大，而且越清理越对不上。
- **「近 7 日 N 条」复用趋势那份聚合**（`dailySubmits` 求和），不再单独打一次库：
  两个数天然一致，也不会出现"图上 7 根柱子加起来 40、副标写 41"这种没法解释的偏差。
- **统计端口 `FormOverviewStatQuery`**（7 方法 / 3 record）：租户显式传参、
  `invalidWindow()` 统一拦时间窗、空值折 0、表单名缺失折 `null` 由 provider 回落编码。
  提交 Top 用 `LEFT JOIN form_definition` 取名、`MAX(d.name)` 保住"一个编码一行"：
  定义被删掉后历史数据还在，内连接会把这几条提交从图上直接抹掉。
- **索引按查询形状补**（`V16__form_overview_index.sql` + `init.sql`）：
  `form_data(tenant_id, create_time)`、`(tenant_id, biz_status, update_time)`；
  建好后删掉被最左前缀完全覆盖的 `idx_tenant_id` 与 `idx_tenant_biz_status`。
  **不动** `idx_definition_id` / `idx_biz_key` / `idx_table_code_version`——它们服务的是
  业务侧按定义、按业务键、按物理表路由的等值查询，与租户 + 时间无关。
- **`init.sql` 顺带补齐 `form_data` 的欠账**：`client_request_id`（V5）、
  `biz_status` / `process_instance_id`（V10）三列与两个唯一键原本只活在迁移里，
  空库跑 `init.sql` 建出来的表与存量库不一致（新索引又依赖 `biz_status`，
  不补会直接建表失败）。这一批把它补成"空库可独立执行"。
- **屏风页**：`FORM_DEFINITION_PAGE` 五列（表单 / 编码 / 分类 / 状态 / 更新）。
  关键字参数是 **`name`**，不是 `keyword`——`FormQueryRequest` 只有
  `name / code / status / category` 四个条件（T5.4 `originalName`、T5.6 `title` 同一个坑）。
  详情配 `detailPath`：字段配置（`fields`）只有详情接口带回来，列表行里没有。
- **「禁用」给灰灯不给红灯**：停用是主动决定（这张表不收了），不是故障；
  给红灯会让人每次路过都想点进去查一遍。字段分录里的"禁用"同理，且分录表只做文字翻译、
  不打灯（与政事殿审批链一致：分录是明细，不是"这一屏整体健康度"）。
- **位置**：考功殿在**东列第二进**（`x = +20.5`、殿心 `z = -7`），紧挨政事殿南侧，
  屏风 `z = -2.5`（殿心 + 4.5）、`y = 3.42`，与 T5.2 起统一的摆法一致。
- **总控接入**：`targets` 加 `kaogong-hall`（`http://bw-form:8084/form/overview`，
  权限码同上），数枢殿总控里出现第七行真数据。
- **验证**：`e2e/tools/world-kaogong-smoke.mjs` **59 项全绿**（四张 KPI 与两张黄灯、
  单序列折线不画图例、搜索参数名是 `name` 且不夹带 `keyword`、状态值用 `1/0`、
  正常绿灯 / 禁用灰灯、分页、详情 9 字段 + 字段配置 4 行、详情按 id 打真接口、
  Esc 退出后回到 3D 尺寸、接口 500 标 stale 且列表给人话）。
  后端 `FormOverviewProviderTest` **13 例** + `whale-service-form` 全量 **263 例**通过
  （含 ArchUnit 5 例、Flyway 迁移守卫 3 例——守卫里新增 V16 两个索引与
  "init.sql 不得再单建 `idx_tenant_id` / `idx_tenant_biz_status`"两条断言）；checkstyle 0 违规。
- **e2e 环境坑（新）**：**两个 e2e 族不要并发跑**。屏风基础族（8124）与业务族（8125）
  各起一个浏览器，两个 WebGL 上下文同时占 GPU 时会拖慢合成，`world-screenpage-smoke`
  翻页按钮被判定成"element is outside of the viewport"而超时失败；串行重跑 35 项全绿。
- **已知遗留**：`/form/overview` 的真实 HTTP 未验（本机 Docker / Nacos 未起）。

**S5 第八批交付说明（2026-11-21，T5.8 通政殿 / message）**

- **接口**：`GET /message/overview`（`whale.overview.base-path`）：四张 KPI（今日投递 /
  送达率 / 积压未发 / 退信）+ 两张图（近 7 日投递 line、失败最多渠道 bar）。
  权限码复用 `sys:message:record:list`，屏风页复用 `/message/v1/record/page` 与 `/{id}`，
  **无新接口、无新权限点**。
- **不复用 2D 投递看板**（`/message/v1/record/dashboard`）：那个方法是给后台用的，它把窗口内
  投递按 `channel + status` 分组**全部回捞到内存**再拼渠道表与失败 Top10。屏风 30 秒轮询一次，
  拿它当数据源等于每 30 秒拉一遍组合行。新端口只做 count 型聚合，一条 SQL 出一个数；
  但**成功率口径逐字沿用**（成功 = `DELIVERED + READ`、失败 = `FAILED + BOUNCED`），
  否则城里 92%、后台 88%，谁都不知道该信哪个。
- **投递按"渠道 × 接收人"计数，不是消息条数**：一条消息发 3 个渠道给 10 人 = 30 条投递。
  对运维来说每一次投递都是一次真实发送尝试；按消息数算会把"1 条发给 10 人"显示成 1，
  失败率随之为零。
- **「积压未发」是本殿的核心数**：状态还停在 `PENDING / DISPATCHING` 且创建时间超过 10 分钟。
  它不报错、不告警，接口返回成功而收件人什么也没收到——消息服务最典型的静默失效。
  阈值 10 分钟是异步补偿回收阈值（120 秒）的 5 倍余量：卡 2 分钟是正常排队，卡 10 分钟
  说明补偿也没救回来。
- **告警两条、根因不同、可并存**：积压 → `CRITICAL`（收下了没送出，去看投递线程与补偿任务）；
  退信 → `WARNING`（地址失效，重试永远失败，只能改数据）。混成一条会让人先去重试一批
  根本不可能成功的投递。
- **小样本不谈比率**：近 24h 投递不足 20 条时，送达率跌破 90% 也不转黄灯——4 条里坏 1 条
  就是 75%，报出来只是噪音。但副标始终写"成功 X / 失败 Y"，避免"100% 其实只发了 1 条"。
- **端口** `MessageOverviewStatQuery`（5 方法 / 3 record）：租户显式传参、`invalidWindow()`、
  空值折 0、渠道空折「未知渠道」。窗口按 `sent_at` 卡而不是 `create_time`：投递行先落库
  再发送，未发出的那些 `sent_at` 为空，算进"今日投递"会让数字比实际送出的大。
- **索引** `V9__message_overview_index.sql` + `init.sql`：
  `msg_delivery(tenant_id, sent_at)`、`(tenant_id, status, sent_at)`、
  `msg_record(tenant_id, status, create_time)`、`(status, create_time)`；
  建好后删被最左前缀覆盖的 `idx_tenant_status`。第 3、4 个索引**不能互相替代**——
  补偿任务查的是"所有租户里卡住的 PENDING"（`InterceptorIgnore(tenantLine)`），没有
  `tenant_id`，带租户前缀的索引用不上；而概览必须带租户，否则会把别家的积压算进来。
- **屏风页**：`MESSAGE_RECORD_PAGE` 五列（标题 / 业务类型 / 渠道 / 状态 / 创建）。
  **没有搜索框**：`MessageRecordQueryRequest` 只有 `bizType / bizId / status / channel`，
  没有关键字（标题模糊）——配一个 `keywordParam` 也会被 Spring 静默忽略，框就成了摆设
  （与 T5.2 同一个判据）。状态选项按"问题优先"排：失败 / 部分失败 / 待投递在最前，
  排查的人十次有九次是来找坏消息的。详情配 `detailPath`，把各渠道投递明细（`deliveries`）
  渲染成分录表；正文 `content` 不进详情——它是 TEXT，长度不受控，塞进 620px 屏风会把版面顶穿。
- **详情分录必须用列级状态字典**：投递状态是**另一套码**（`READ` / `BOUNCED` 在消息状态里
  根本不存在）。用页面级字典会把它们原样显示成英文且不报错——`DELIVERY_STATUS_STYLE`
  + 列级 `statusMap` 覆盖（T5.6 加的能力，这是第二个真实用例）。
- **位置**：通政殿在**东列第三进**（`x = +20.5`、殿心 `z = 7`），紧挨考功殿南侧，
  屏风 `z = 11.5`（殿心 + 4.5）、`y = 3.42`，与 T5.2 起统一的摆法一致。
- **总控接入**：`targets` 加 `tongzheng-hall`（`http://bw-message:8094/message/overview`，
  权限码同上），数枢殿总控里出现第八行真数据。
- **验证**：`e2e/tools/world-tongzheng-smoke.mjs` **55 项全绿**（四张 KPI 与两盏黄灯、
  送达率显示成百分比、"没有搜索框"、状态选项问题优先且值为后端枚举、首屏只带分页参数、
  待投递黄灯不是中性色、分页、详情 8 字段 + 投递明细 4 行、`READ→已读` / `BOUNCED→退信`
  证明列级字典生效、Esc 退出后回到 3D 尺寸、接口 500 标 stale 且列表给人话）。
  后端 `MessageOverviewProviderTest` **12 例** + `whale-service-message` 全量 **144 例**通过
  （含 ArchUnit 3 例、Flyway 迁移守卫 5 例——守卫里新增 V9 四个索引与"init.sql 不得再单建
  `idx_tenant_status`"断言，断言前先剥行注释）；checkstyle 0 违规。
- **已知遗留**：`/message/overview` 的真实 HTTP 未验（本机 Docker / Nacos 未起）。

**S5 第九批交付说明（2026-11-21，T5.9 起居殿 / audit，只读）**

- **接口**：`GET /audit/overview`（`whale.overview.base-path`）：四张 KPI（今日审计 /
  失败操作 / 留痕断流 / 归档陈旧）+ 两张图（近 7 日审计 line、操作类型 Top5 bar）。
  权限码复用 `sys:audit:log:list`，屏风页复用 `/audit/v1/log/page`，**无新接口、无新权限点、无新迁移**。
  audit 是**单模块服务**（`whale-ops/whale-ops-audit`），provider 放 `overview` 包、统计端口放
  `service`、聚合 SQL 走独立只读 Mapper（不污染公共 `AuditLogMapper` 的 BaseMapper）。
- **这一屏回答"审计本身还活着吗"**：审计服务最怕的不是漏抓一次违规，而是"该记的那段是空白"。
  摄入链路断开、归档停摆都**不报错**——进程活着、健康探针绿、指标照常暴露，等查账才发现没证据。
  所以除常规两牌外专设两张静默失效牌：
  - **留痕断流**（`silence`, DURATION）= 当前时刻减本租户最近一条审计写入时刻。比"今日 0 条"
    更准——空闲环境今日 0 条正常，连续一天一条都没有才不正常。**从未写入记 -1，不算故障**
    （前端 `formatDuration` 会把负值 clamp 成 0 秒，靠副标"尚无审计记录"兜底）。
  - **归档陈旧**（`stale`, 天）= 归档作业 `audit.log.retention` 心跳最后成功距今，与指标
    `audit.job.stale.days` 同口径（心跳表平台级、主键 job_code、无 tenant_id，已在租户白名单）。
- **告警两条、根因不同、可并存**：断流 → `CRITICAL`（写不进来，先查摄入链路）；
  归档停摆 → `WARNING`（记下来了但没归档，去查 retention 作业）。混成一条会让人先翻归档日志，
  而真正的问题是审计根本没写进去。
- **全程只读**：概览 30s 轮询，provider 内只有 count/select，无任何写库——与 `audit_log`
  append-only 语义一致，触发器与门禁不误伤。
- **租户显式传参**：provider 在 SDK 独立线程池执行（`OverviewService#invoke`），拿不到请求线程的
  `TenantContext`，租户插件会忽略审计表——统计 SQL 必须自己带 `tenant_id = #{tenantId}`，
  否则把别家的审计算进这一屏（还不报错）。时间条件一律 `>= from AND < to`，函数只用在分组键。
- **索引**：复用 audit_log 既有 `idx_tenant_time(tenant_id, create_time)`，趋势按天聚合窗口仅 7 天，
  **无新索引、无新迁移**（V1 基线已覆盖，audit 服务自身版本仍止于 V4）。
- **屏风页** `AUDIT_LOG_PAGE` 五列（操作时间 / 操作人 / 模块 / 操作类型 / 结果）。
  **关键字参数是 `username` 不是 `keyword`**（`AuditLogQueryRequest` 的文本条件只有 username 模糊匹配，
  配 keyword 会被 Spring 静默忽略）。状态筛选走 `bizStatus`（0 失败 / 1 成功），值用后端原值；
  `bizStatus` 是数字，靠 `AUDIT_RESULT_STYLE` 字典 `String(value)` 查表译成"成功/失败"并打灯。
  **不配 `detailPath`**：详情接口 `/{id}` 返回的与列表行同字段（同一个 `AuditLogVO`），再请求拿不到
  新信息，还会把 `requestParams`/`resultBody` 两个 TEXT 顶进 620px 版面。
- **位置**：起居殿在**东列第四进**（`x = +20.5`、殿心 `z = 21`，通政殿北侧），
  屏风 `[20.5, 3.42, 25.5]`（殿心 + 4.5）、`y = 3.42`、`facing = 0`，摆法与前几殿一致。
- **总控接入**：`targets` 加 `qiju-hall`（`http://bw-audit:9101/audit/overview`，权限码同上），
  数枢殿总控里出现第九行真数据。容器名/端口取自 compose（`bw-audit` / 9101），网关 `/audit/**` 路由已存。
- **验证**：`e2e/tools/world-qiju-smoke.mjs` **46 项全绿**（四张 KPI 与两盏告警灯、断流 DURATION 排版、
  有搜索框且关键字发 username 不发 keyword、状态值用 bizStatus 原值、数字状态码译成中文并打灯、
  首屏只带分页参数、分页、列表行即详情不打二次请求、Esc 退出回 3D、接口 500 标 stale 给人话）。
  后端 `AuditOverviewProviderTest` **12 例** + audit 全量 **135 例**通过（含 ArchUnit 5 例，带 `-am`）；
  monitor 13 例；checkstyle 0 违规；八项门禁 rc=0；前端 check:overview / lint / build 全过。
  回归：业务族 tongzheng 55 / shushu 31 / kaogong 59 / screenpage 35、屏风基础族 7 个、city-smoke 全绿。
- **已知遗留**：`/audit/overview` 的真实 HTTP 未验（本机 Docker / Nacos 未起），同前八批口径。

**S5 第十批交付说明（2026-11-21，T5.10 城门楼地标 / gateway，无入口）**

- **这一批不接 overview，也不建屏风页**，与"无入口"的定位一致：网关是**无状态代理**，
  只有路由 / 鉴权 / 限流，没有业务表，也就没有可浏览的台账。硬给它做一屏"网关健康"，
  就得在**全城唯一入口**上多开一个业务端点并进白名单——每次改白名单/兜底路由都要重建镜像、
  重启入口。为一块地标多一颗暴露面，不划算。总纲给它的定位是"仅地标"，这里按字面执行。
- **"仅地标"不等于"什么都不做"**。门楼原本只是几何体：走到门洞里按 E 会弹出一张殿宇清单、
  附近提示条什么都不显示，玩家会以为"这里应该能进，是不是坏了"。所以补的是**认得出**：
  1. 四座有匾额的外城门楼登记成地标（`landmarks.ts`），内城两座无名门楼只留城门洞不冒充地标；
  2. 提示条写 **"按 E 过门 · 南薰門 · 通道非殿"**——不敢写"进入"，因为它确实没有内页；
  3. 按 E 仍是过门（开传送门列表），但同时落一条说明"通道，不是殿：鉴权、限流、路由都在这儿"。
- **坐标不新增来源**：四门坐标就是 `outerWall × (sin,cos)yaw`，门洞半宽就是 `gateHalfWidth`，
  全部从 `city-layout.json` 现算，不登记成 `sc_scene_node`（它不是可进入的建筑，2D 城图里
  它只是城墙的一段；多一个节点 = 布局校验与迁移都要跟着加一版，而玩家感知不到区别）。
- **提示条收口成单一裁决者**：原来 `updatePortals` 与 `updateScreenHint` **各写同一块
  `#bww-portal-hint`**，靠"后调用的赢"这种隐式约定工作。加第三个来源时必然互相覆盖，
  所以改成 `updateInteractionHint()` 一次算完，优先级 **屏风（4.6m）> 传送门（7m）> 城门楼（11m）**。
  回归 `world-screen-hint` 专门断言"屏风前仍是『进入』、不出现『过门』"。
- **顺手修掉一个真 bug：城图与实景分层漂移**。`minimap.ts` 把 v1 尺度写死成
  外城 92 / 内城 48 / 皇城 22 / 环路 68 / 视野 ±100，S1 把城扩到 **110/58/30/76** 之后没人回来改，
  于是城图上的外城墙整圈跑到**画布外**、内城墙偏里 10 米——玩家看到的是"我站在城墙边，
  箭头却在图外"。现改为全部从 `CITY_GEOMETRY` 派生（视野 = 外城 + 10），
  并给三重城墙的**十处门洞留缺口**（四外门 + 两内门 + 四皇城御道门）+ 四门点金石标记；
  指北从"顶部居中"挪到左上角三角 + 「北」——170px 的图上，居中的"北"字正好压在北门门楼上。
- **验证**：`e2e/tools/world-gate-smoke.mjs` **26 项全绿**（地标表 6 座/4 座有名、坐标落在外墙线、
  出生点不在提示圈、门洞前提示文案含"过门"且不含"进入"、按 E 过门有说明且不误进屏风、
  走开提示消失、屏风/传送门优先级没被抢、城图五组尺度 = 布局值、10 处门洞、四门标记）。
  回归（两族串行）：业务族 qiju 46 / tongzheng 55 / zhengshi 56 / kaogong 59 / shichen 44 /
  cangshu 37 / duzhi 36 / shumi 32 / shushu 31 / screenpage 35，基础族 screen-hint / smoke /
  engage / ime / resize / occlusion / wheel + city-smoke 25 / datahall 25 / glyphs 4 / quest 42 /
  guide 19 / walk-check 1 全绿；八项门禁 rc=0（perm-mapping 995、api-path-drift 1571）。
  前端 tsc / eslint --max-warnings 0 / vite build / check:overview 全过。
- **基线复现出的既有失败**（不是本批引入）：`world-path` 2 项（"提示已改道"与"落点被挪到殿外"，
  实际落在 26.5/62.5——户部坊门洞与墙的交界，nav grid 判为可走）。已用 `git checkout` 回滚本批
  两个文件、重新构建后复跑，**同样 2 项失败**，故记为既有问题。

### S6 内城 12 坊

同 S5 结构，每坊 3 个任务（overview + 屏风页 + 厅配置），共 **36 个**：

| 编号 | 任务 | 服务 | 状态 |
|---|---|---|---|
| T6.1 | 户部坊 overview（`/erp/overview`） | erp | [x] S4 标杆 |
| T6.2 | 户部坊 屏风页（采购订单） | erp | [x] S4/T4.5 |
| T6.3 | 户部坊 厅配置 | erp | [x] S4/T4.6 |
| T6.4 | 工部坊 overview（`/mes/overview`） | mes | [x] |
| T6.5 | 工部坊 屏风页（生产工单） | mes | [x] |
| T6.6 | 工部坊 厅配置 + 数枢殿总控在册 | mes | [x] |
| T6.7–T6.36 | 其余十坊（吏部 / 礼部 / 兵部 / 杏坛 / 将作 / 营造 / 盟府 / 宣抚 / 翰林 / 市舶） | oa / crm / iot / lms / pdm / pm / contract / party / doc / mall | [ ] |

**S6 第一批交付说明（2026-11-21，T6.4–T6.6 工部坊 / mes）**

- **接口**：`GET /mes/overview`（`whale.overview.base-path`）：四张 KPI（今日完成 / 达成率 /
  未关闭 / 故障设备）+ 一张图（今日 目标/完成/不良）+ 四条告警（故障设备 CRITICAL、生产异常与
  NCR 各一条 WARNING、SPC 命中 INFO）。权限码复用 2D 综合看板的 `sys:mes:dashboard:view`，
  屏风页复用 `/mes/work-order/page`，**无新接口、无新权限点、无新迁移**。
- **口径不另造**：provider 只调 `MesDashboardService#dashboard(null, null, null)` —— 与 2D 综合看板、
  SSE 推送、CSV 导出**同一个方法**；生产/质量两域在 MES 内部又分别复用生产看板与质量看板服务。
  不传 `lineId/teamId`：城里看的是全厂今日，在 3D 侧默认一条产线等于造一个"后台看不到的口径"。
- **百分比必须换算成比率**：MES 的 `achievementRate` 是 0–100 的百分数（`90.00`），契约
  `format=percent` 要求比率（`0.9`）。provider 显式 `divide(100, 4, HALF_UP)`；单测把
  "0.9 → 90.0%"写死成断言，负向验证（去掉除法）当场 FAIL。
- **两处静默失效被提前挡住**：① 今日无计划时 `achievementRate` 为 null，直接发 0 会显示
  "达成率 0.0%"像全线停产 → 语气置 FLAT、提示语写"今日无计划"；② MES 工单查询**没有 `keyword`**，
  屏风页关键字接到 `code`（工单编码），不发明一个后端会默默忽略的参数名。
- **屏风落位不再手抄坐标**：内城坊台基深 15.2m、坊墙内缘不到 2m，屏风只能立在台基外、坊门内那一条地上。
  新增 `districtScreenPose()`，把 S4 在户部实测的偏移（正面法线 9.67m + 切向 2.04m）套到
  `PALACES` 上现算；户部保留实测字面量（公式来源），其余十一坊一律走函数。
- **数枢殿总控在册**：`whale-monitor` 的 `whale.overview.aggregate.targets` 加一行
  （`http://bw-mes:9002/mes/overview`），总控聚合与前端都不用改。
- **验证**：`MesOverviewProviderTest` 9 例 + `MesArchitectureTest` 7 条（新包
  `dashboard.overview` 归入交付层，禁直连 mapper）全绿；whale-mes 全量 159 例通过、checkstyle 0 违规；
  e2e `world-gongbu-smoke.mjs` **48 项全绿**；回归 qiju 46 / tongzheng 55 / zhengshi 56 / kaogong 59 /
  shichen 44 / cangshu 37 / duzhi 36 / shumi 32 / shushu 31 / screenpage 35 / gate 26 / city-smoke 全通过 /
  datahall 全通过 / guide 19 / quest 42 / glyphs 4 + 基础族七支 rc=0；八项门禁 rc=0（perm-mapping 995）。
- **已知遗留**：`/mes/overview` 的真实 HTTP 未验（本机 Docker / Nacos 未起），同前九批口径；
  起服后按 `curl -H "X-User-Id: 1" -H "X-Permission-Codes: sys:mes:dashboard:view" http://127.0.0.1:8080/mes/overview` 复核。

### S7 外城 9 处

| 编号 | 任务 | 服务 | 特殊 |
|---|---|---|---|
| T7.1 | [ ] 乐坊 | music | 空间音频 |
| T7.2 | [ ] 影戏院 | video | 视频纹理 |
| T7.3 | [ ] 清议堂 | forum | 帖子列表 |
| T7.4 | [ ] 邮驿 | mail | 邮件列表 |
| T7.5 | [ ] 传信司 | im | 会话列表（贴脸模式） |
| T7.6 | [ ] 舆图司 | map | 地面舆图纹理 |
| T7.7 | [ ] 观星台 | twin | 3D 场景嵌入 |
| T7.8 | [ ] 智囊院 | agent + assistant | 对话（贴脸模式） |
| T7.9 | [ ] 文库 | archive | 归档目录 |

### S8 性能与打磨

| 编号 | 任务 | 验收 | 工期 |
|---|---|---|---|
| T8.1 | [ ] 建筑改 InstancedMesh | 绘制调用降 50% | 1d |
| T8.2 | [ ] 城外子树进殿时卸载 | 进殿帧率提升 | 1d |
| T8.3 | [ ] 纹理图集合并 | 显存降 30% | 1d |
| T8.4 | [ ] LOD（远处建筑降模） | 远景不糊、近景不卡 | 1.5d |
| T8.5 | [ ] 首屏加载优化（分包 + 预加载） | ≤3s 可操作 | 1d |
| T8.6 | [ ] 低档画质路径 | ≥45fps | 1d |
| T8.7 | [ ] 全城走查与穿模修复 | 无穿模、无卡死 | 1d |
| T8.8 | [ ] 错误兜底与埋点 | 异常可定位 | 0.5d |

### 任务总量

| 阶段 | 任务数 | 工期 |
|---|---|---|
| S0 验证 | 6 | 6d |
| S1 骨架 | 10 | 9d |
| S2 屏风 | 11 | 10d |
| S3 数据厅 | 7 | 7d |
| S4 接口层 | 6 | 7d |
| S5 皇城 | 27 | 14d |
| S6 内城 | 36 | 18d |
| S7 外城 | 9 | 12d |
| S8 打磨 | 8 | 8d |
| **合计** | **120** | **~91d**（单人不并行） |

> 91 个工作日 ≈ 4.5 个月（单人）。并行 2 人可压到 2.5 个月，但 S0–S4 必须串行。

---

## 4. 里程碑与准入门槛

| 里程碑 | 内容 | 准入门槛（达不到不进入下一阶段） |
|---|---|---|
| **M0** | S0 完成 | 四堵墙全部有结论；IME 与遮挡有可运行 demo |
| **M1** | S1+S2 完成 | 12 坊可见、屏风可进可出、能挂 React 页面 |
| **M2** | S3+S4 完成 | ERP 一个完整业务闭环（看板→列表→详情→操作） |
| **M3** | S5 完成 | 皇城 9 殿全部可进，数据与 2D 后台一致 |
| **M4** | S6 完成 | 12 坊全部可进，权限隔离生效 |
| **M5** | S7+S8 完成 | 全城 30 处可进，1080p ≥30fps |

---

## 5. 红线（v2 新增）

1. **不跳出 3D**：任何"打开新页面/白色后台"都算 bug。
2. **不为单个服务定制 3D 场景**：一律用 `DataHall` + `Screen` 参数化。
3. **同屏 DOM 面积上限**：最多 2 块屏风同时可见，超出用纹理贴图。
4. **不新增坐标源**：一切从 `city-layout.json` 派生。
5. **不改 2D 后台**：并行线，零改动。
6. **不写 C 盘**：沿用 `.toolchains`。

---

## 6. 待舰长决策

| # | 事项 | 选项 | 影响 |
|---|---|---|---|
| 1 | S0 是否先做 | 先验证 6 天 vs 直接开干 | 直接开干若方案不通，返工 30 天 |
| 2 | 人力 | 单人 vs 双人并行 | 4.5 个月 vs 2.5 个月 |
| 3 | 外城是否保留 | 9 处 vs 4 处 | 省 8 天 |
| 4 | 首期范围 | 只做 M2（ERP 闭环） vs 一口气到 M3 | 前者 30 天可演示，后者 45 天 |

**我的建议**：先做 S0（6 天），拿到 IME 和遮挡的可运行 demo 再决定。这两项是唯一可能翻盘的地方，其余都是工程量问题。
